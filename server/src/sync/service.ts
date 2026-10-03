import type { FastifyBaseLogger } from 'fastify';
import type { PoolClient, Queryable } from '../db/pool.js';
import { lockEntityWrites, withTx } from '../db/pool.js';
import type { Actor, Deps } from '../core/types.js';
import { audit } from '../core/audit.js';
import { isStaffId, normalizeSehatiId, WARGA_ID } from '../core/ids.js';
import { expandRwSpec, isValidRwSpec, normalizeRw } from '../core/rw.js';
import { getAccount, revokeSessions, type AccountRow } from '../auth/accounts.js';
import { bumpCounterPast, getReservation, markReservationUsed } from './ids.js';
import { getEntity, moveSubjectRw, writeEntity, type EntityRow, type JsonObject } from './store.js';
import {
  ADMIN_FOLLOWUP_FIELDS, ADMIN_PULL, ADMIN_PUSH, ALL_TYPES, KADER_PUSH, SUBJECTLESS_TYPES, WARGA_PUSH,
  sanitizeReason, type Ack,
} from './types.js';

export interface PushItem {
  id: string;
  type: string;
  entityId: string;
  subjectId?: string | null | undefined;
  operation: 'UPSERT' | 'DELETE';
  version: number;
  payload: string;
}

const NO_CONSENT = 'Warga belum menyetujui sinkronisasi';

/** Hasil antara: REJECTED/CONFLICT dikembalikan tanpa menyimpan receipt agar klien dapat mencoba lagi nanti. */
class ItemOutcome extends Error {
  constructor(readonly ack: Omit<Ack, 'id'>) {
    super(ack.message ?? ack.status);
  }
}
const reject = (message: string) => new ItemOutcome({ status: 'REJECTED', message });
const conflict = (message: string) => new ItemOutcome({ status: 'CONFLICT', message });

function isObject(v: unknown): v is JsonObject {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}
const str = (v: unknown): string | null => (typeof v === 'string' && v.length > 0 ? v : null);

interface SubjectInfo {
  id: string | null;
  account: AccountRow | null;
  user: EntityRow | null;
}

/** Proses satu batch push. Tiap item = satu transaksi, sehingga kegagalan satu item tidak membatalkan yang lain. */
export async function pushBatch(deps: Deps, actor: Actor, deviceId: string, items: PushItem[], log: FastifyBaseLogger): Promise<Ack[]> {
  const results: Ack[] = [];
  for (const item of items) {
    try {
      const ack = await withTx(deps.pool, (c) => processItem(deps, c, actor, deviceId, item));
      results.push({ id: item.id, ...ack });
    } catch (e) {
      if (e instanceof ItemOutcome) {
        results.push({ id: item.id, ...e.ack });
      } else {
        // Galat tak terduga → 500: klien mencoba ulang; item yang sudah tersimpan akan menjadi DUPLICATE.
        log.error({ err: e, type: item.type }, 'sync push item failed');
        throw e;
      }
    }
  }
  return results;
}

async function processItem(deps: Deps, c: PoolClient, actor: Actor, deviceId: string, item: PushItem): Promise<Omit<Ack, 'id'>> {
  const now = deps.now();
  if (!ALL_TYPES.has(item.type)) throw reject('Tipe data tidak dikenal');

  const receipt = await c.query<{ server_id: string | null }>('SELECT server_id FROM sync_receipts WHERE idem_key = $1', [item.id]);
  if (receipt.rows[0]) return { status: 'DUPLICATE', serverId: receipt.rows[0].server_id };

  let payload: JsonObject;
  try {
    const parsed: unknown = JSON.parse(item.payload);
    if (!isObject(parsed)) throw new Error('not an object');
    payload = parsed;
  } catch {
    if (item.operation !== 'DELETE') throw reject('Payload bukan JSON objek yang valid');
    payload = {};
  }

  await lockEntityWrites(c);
  const subject = await resolveSubject(c, item, payload);
  checkPayloadConsistency(item, payload, subject.id);
  checkRbac(actor, item, payload, subject);

  if (subject.user?.deleted && !(item.type === 'user' && item.operation === 'DELETE')) {
    throw reject('Data warga ini sudah dihapus dari server');
  }

  if (item.type === 'user' && item.operation === 'DELETE') {
    const serverId = await eraseSubject(c, subject.id!, actor, now);
    await saveReceipt(c, item, actor, 'OK', serverId, now);
    return { status: 'OK', serverId };
  }

  if (subject.id !== null) checkConsent(item, payload, subject);

  const existing = await getEntity(c, item.type, item.entityId, true);
  if (existing) {
    if (existing.version > item.version) {
      return { status: 'CONFLICT', serverId: existing.server_id, message: 'Server memiliki versi yang lebih baru' };
    }
    // Versi sama/lebih rendah dengan kunci baru = kiriman ulang. Pengecualian: kredensial (versinya tidak
    // dikelola entitas Room, jadi versi sama tetap diterapkan) dan DELETE atas baris yang belum dihapus
    // (tombstone diberi versi +1).
    const freshDelete = item.operation === 'DELETE' && !existing.deleted;
    if (existing.version >= item.version && item.type !== 'credential' && !freshDelete) {
      await saveReceipt(c, item, actor, 'DUPLICATE', existing.server_id, now);
      return { status: 'DUPLICATE', serverId: existing.server_id };
    }
  } else if (item.type === 'user') {
    await checkIdCollision(c, actor, deviceId, subject, now);
  }

  if (item.operation === 'DELETE') {
    // Tombstone: baris tetap ada (deleted=true, tanpa payload) dengan versi & seq baru agar ikut ter-pull.
    const version = existing ? Math.max(item.version, existing.version + 1) : item.version;
    const serverId = await writeEntity(c, {
      type: item.type, entityId: item.entityId, subjectId: subject.id, subjectRw: existing?.subject_rw ?? subjectRwOf(subject),
      version, payload: null, deleted: true, actorId: actor.id, deviceId, now,
    });
    await saveReceipt(c, item, actor, 'OK', serverId, now);
    return { status: 'OK', serverId };
  }

  let stored: JsonObject = payload;
  let subjectRw: string | null = subjectRwOf(subject);
  switch (item.type) {
    case 'user':
      subjectRw = await applyUser(c, actor, payload, subject, now);
      break;
    case 'credential':
      stored = await applyCredential(c, actor, payload, subject, now);
      break;
    case 'cadre':
      await applyCadre(c, actor, payload, now);
      break;
    case 'followup':
      if (actor.role === 'ADMIN') {
        if (!existing || existing.deleted || !existing.payload) throw reject('Tindak lanjut tidak ditemukan di server');
        stored = mergeAdminFollowup(existing.payload, payload, item.version);
        await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'followup_update', subjectId: subject.id, detail: `id=${item.entityId}` }, now);
      }
      break;
  }

  const serverId = await writeEntity(c, {
    type: item.type, entityId: item.entityId, subjectId: subject.id, subjectRw, version: item.version,
    payload: stored, deleted: false, actorId: actor.id, deviceId, now,
  });
  await saveReceipt(c, item, actor, 'OK', serverId, now);
  return { status: 'OK', serverId };
}

async function saveReceipt(c: Queryable, item: PushItem, actor: Actor, status: string, serverId: string | null, now: number) {
  await c.query(
    `INSERT INTO sync_receipts (idem_key, account_id, type, entity_id, status, server_id, created_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7) ON CONFLICT (idem_key) DO NOTHING`,
    [item.id, actor.id, item.type, item.entityId, status, serverId, now],
  );
}

// ---------------------------------------------------------------- subjek, RBAC, consent

async function resolveSubject(c: PoolClient, item: PushItem, payload: JsonObject): Promise<SubjectInfo> {
  let id: string | null = null;
  const given = item.subjectId ? normalizeSehatiId(item.subjectId) : null;
  if (SUBJECTLESS_TYPES.has(item.type)) {
    id = null;
  } else if (item.type === 'user') {
    id = normalizeSehatiId(item.entityId);
    if (given && given !== id) throw reject('subjectId tidak sesuai dengan entitas user');
  } else if (item.type === 'credential') {
    id = normalizeSehatiId(str(payload.sehatiId) ?? item.entityId);
  } else {
    id = given ?? str(payload.userId);
    if (!id && item.type === 'measurement_detail') {
      for (const k of ['anthropometry', 'bloodPressure', 'glucose', 'lipid']) {
        const part = payload[k];
        if (isObject(part) && str(part.userId)) id = str(part.userId);
      }
      if (!id) {
        const header = await getEntity(c, 'measurement', str(payload.measurementId) ?? item.entityId);
        id = header?.subject_id ?? null;
      }
    }
    if (!id) throw reject('subjectId wajib untuk tipe ini');
    id = normalizeSehatiId(id);
  }
  if (id === null) return { id: null, account: null, user: null };
  return { id, account: await getAccount(c, id), user: await getEntity(c, 'user', id) };
}

function checkPayloadConsistency(item: PushItem, p: JsonObject, subjectId: string | null): void {
  if (item.operation === 'DELETE') return;
  const idField: Record<string, string> = {
    user: 'sehatiId', credential: 'sehatiId', cadre: 'sehatiId', profile: 'userId', measurement_detail: 'measurementId',
  };
  const key = idField[item.type] ?? 'id';
  if (key in p && p[key] !== item.entityId) throw reject('ID entitas pada payload tidak sesuai');
  if (subjectId && typeof p.userId === 'string' && normalizeSehatiId(p.userId) !== subjectId) {
    throw reject('userId pada payload tidak sesuai dengan subjectId');
  }
  if (item.type === 'measurement_detail') {
    for (const k of ['anthropometry', 'bloodPressure', 'glucose', 'lipid']) {
      const part = p[k];
      if (isObject(part) && typeof part.userId === 'string' && subjectId && normalizeSehatiId(part.userId) !== subjectId) {
        throw reject('userId pada detail pemeriksaan tidak sesuai');
      }
    }
  }
}

/** Subjek dianggap warga bila akunnya ber-role WARGA, atau belum ada akun dan ID bukan ID staf. */
function isWargaSubject(s: SubjectInfo): boolean {
  if (!s.id) return false;
  if (s.account) return s.account.role === 'WARGA';
  return !isStaffId(s.id);
}

function checkRbac(actor: Actor, item: PushItem, p: JsonObject, s: SubjectInfo): void {
  const t = item.type;
  const del = item.operation === 'DELETE';
  if (t === 'credential' && del) throw reject('Kredensial tidak dapat dihapus lewat sinkronisasi');

  switch (actor.role) {
    case 'WARGA': {
      if (!WARGA_PUSH.has(t)) throw reject('Peran warga tidak boleh mengirim tipe data ini');
      if (s.id !== actor.id) throw reject('Warga hanya boleh mengirim datanya sendiri');
      if (t === 'user' && !del && (p.role ?? 'WARGA') !== 'WARGA') throw reject('Role akun tidak dapat diubah');
      return;
    }
    case 'KADER': {
      if (!KADER_PUSH.has(t)) throw reject('Peran kader tidak boleh mengirim tipe data ini');
      if (!isWargaSubject(s)) throw reject('Kader tidak boleh mengubah data staf');
      if (t === 'user' || t === 'credential') {
        const registrant = s.account ? s.account.registered_by : t === 'user' && !del ? str(p.registeredBy) : null;
        if (registrant !== actor.id) throw reject('Kader hanya boleh mengubah akun warga yang ia daftarkan');
        if (t === 'user' && !del && (p.role ?? 'WARGA') !== 'WARGA') throw reject('Role akun tidak dapat diubah');
      }
      return;
    }
    case 'ADMIN': {
      if (!ADMIN_PUSH.has(t)) throw reject('Admin tidak boleh mengirim data kesehatan warga');
      if (t === 'followup' && (del || !isWargaSubject(s))) throw reject('Admin hanya dapat menugaskan/menjadwalkan tindak lanjut warga');
      return;
    }
  }
}

function hasConsent(s: SubjectInfo): boolean {
  if (s.user && !s.user.deleted && s.user.payload) return s.user.payload.consentServerSync === true;
  return s.account?.role === 'WARGA' && s.account.consent_server_sync;
}

function checkConsent(item: PushItem, p: JsonObject, s: SubjectInfo): void {
  // Item user dengan consent=true memberi persetujuan; consent=false tetap diterima bila sebelumnya
  // sudah setuju (pencabutan persetujuan tercatat, data berikutnya ditolak).
  if (item.type === 'user' && (p.consentServerSync === true || hasConsent(s))) return;
  if (!hasConsent(s)) throw reject(NO_CONSENT);
}

function subjectRwOf(s: SubjectInfo): string | null {
  if (!s.id) return null;
  if (s.account?.role === 'WARGA' && s.account.rw) return s.account.rw;
  const rw = s.user?.payload ? str(s.user.payload.rw) : null;
  return rw ? normalizeRw(rw) : null;
}

/** Bentrok ID antar-perangkat: ID sudah dipakai orang lain atau dicadangkan perangkat lain → CONFLICT. */
async function checkIdCollision(c: PoolClient, actor: Actor, deviceId: string, s: SubjectInfo, now: number): Promise<void> {
  const id = s.id!;
  if (s.account && s.account.sehati_id !== actor.id && s.account.registered_by !== actor.id) {
    throw conflict('SEHATI ID sudah dipakai warga lain');
  }
  const r = await getReservation(c, id);
  if (r && r.device_id !== deviceId && r.expires_at > now && r.used_at === null && s.account === null) {
    throw conflict('SEHATI ID dicadangkan perangkat lain');
  }
}

// ---------------------------------------------------------------- efek samping per tipe

async function applyUser(c: PoolClient, actor: Actor, p: JsonObject, s: SubjectInfo, now: number): Promise<string> {
  const id = s.id!;
  if (!WARGA_ID.test(id)) throw reject('Format SEHATI ID warga harus HM-000000');
  const rwRaw = str(p.rw);
  if (!rwRaw || !/^\d{1,3}$/.test(rwRaw.trim())) throw reject('RW pada data user tidak valid');
  const rw = normalizeRw(rwRaw);
  const consent = p.consentServerSync === true;
  const registeredBy = actor.role === 'KADER' ? actor.id : s.account?.registered_by ?? null;
  await c.query(
    `INSERT INTO accounts (sehati_id, role, full_name, rw, village, consent_server_sync, registered_by, created_at, updated_at)
     VALUES ($1, 'WARGA', $2, $3, $4, $5, $6, $7, $7)
     ON CONFLICT (sehati_id) DO UPDATE SET full_name = EXCLUDED.full_name, rw = EXCLUDED.rw, village = EXCLUDED.village,
       consent_server_sync = EXCLUDED.consent_server_sync,
       registered_by = COALESCE(accounts.registered_by, EXCLUDED.registered_by), updated_at = EXCLUDED.updated_at`,
    [id, String(p.fullName ?? '').slice(0, 200), rw, String(p.village ?? '').slice(0, 200), consent, registeredBy, now],
  );
  if (subjectRwOf(s) !== null && subjectRwOf(s) !== rw) await moveSubjectRw(c, id, rw, now);
  await bumpCounterPast(c, id);
  await markReservationUsed(c, id, now);
  if (!s.account) {
    await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'account_create', subjectId: id, detail: 'via sync' }, now);
  } else if (s.account.consent_server_sync !== consent) {
    await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'consent_change', subjectId: id, detail: `consentServerSync=${consent}` }, now);
  }
  return rw;
}

/** Kredensial: hash disimpan di accounts; entitas hanya menyimpan metadata (hash tidak pernah ikut pull). */
async function applyCredential(c: PoolClient, actor: Actor, p: JsonObject, s: SubjectInfo, now: number): Promise<JsonObject> {
  const salt = str(p.salt);
  const hash = str(p.hash);
  const iterations = p.iterations;
  const b64 = /^[A-Za-z0-9+/]+={0,2}$/;
  if (!salt || !hash || !b64.test(salt) || !b64.test(hash) || Buffer.from(salt, 'base64').length < 8
    || Buffer.from(hash, 'base64').length !== 32 || typeof iterations !== 'number' || !Number.isInteger(iterations)
    || iterations < 10_000 || iterations > 10_000_000) {
    throw reject('Format kredensial tidak valid');
  }
  if (!s.account) throw reject('Akun warga belum ada; kirim data user terlebih dahulu');
  await c.query(
    `UPDATE accounts SET salt = $2, hash = $3, iterations = $4, failed_attempts = 0, locked_until = 0, updated_at = $5
      WHERE sehati_id = $1`,
    [s.id, salt, hash, iterations, now],
  );
  await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'credential_update', subjectId: s.id }, now);
  return { sehatiId: s.id, iterations };
}

async function applyCadre(c: PoolClient, actor: Actor, p: JsonObject, now: number): Promise<void> {
  const id = str(p.sehatiId);
  if (!id) throw reject('sehatiId kader wajib');
  const acc = await getAccount(c, id);
  if (acc && acc.role !== 'KADER') throw reject('Akun ini bukan kader');
  if (acc) {
    const rw = str(p.assignedRw);
    const active = typeof p.active === 'boolean' ? p.active : acc.active;
    await c.query('UPDATE accounts SET rw = $2, active = $3, updated_at = $4 WHERE sehati_id = $1', [
      id, rw && isValidRwSpec(rw) ? rw : acc.rw, active, now,
    ]);
    if (!active && acc.active) await revokeSessions(c, id);
  }
  await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'cadre_update', subjectId: id, detail: 'via sync' }, now);
}

function mergeAdminFollowup(stored: JsonObject, incoming: JsonObject, version: number): JsonObject {
  const next: JsonObject = { ...stored };
  for (const k of ADMIN_FOLLOWUP_FIELDS) if (k in incoming) next[k] = incoming[k];
  next.version = version;
  return next;
}

/** Hak penghapusan: semua entitas warga menjadi tombstone tanpa payload, akun & sesi dihapus. */
export async function eraseSubject(c: PoolClient, subjectId: string, actor: Actor, now: number): Promise<string | null> {
  await c.query(
    `UPDATE entities SET deleted = true, payload = NULL, version = version + 1, seq = nextval('entity_seq'),
            updated_at = $2, updated_by = $3
      WHERE subject_id = $1 OR (type = 'user' AND entity_id = $1)`,
    [subjectId, now, actor.id],
  );
  await c.query(`DELETE FROM accounts WHERE sehati_id = $1 AND role = 'WARGA'`, [subjectId]);
  await c.query('DELETE FROM ai_usage WHERE account_id = $1', [subjectId]);
  await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'user_erase', subjectId }, now);
  const u = await getEntity(c, 'user', subjectId);
  return u?.server_id ?? null;
}

// ---------------------------------------------------------------- pull

export interface PullItem {
  seq: number;
  type: string;
  entityId: string;
  subjectId: string | null;
  version: number;
  deleted: boolean;
  payload: string | null;
}

export async function pull(deps: Deps, actor: Actor, cursor: number, limit: number): Promise<{ items: PullItem[]; nextCursor: number; hasMore: boolean }> {
  const params: unknown[] = [cursor, limit + 1];
  let visibility: string;
  switch (actor.role) {
    case 'WARGA':
      params.push(actor.id);
      visibility = 'subject_id = $3';
      break;
    case 'KADER':
      params.push(expandRwSpec(actor.rw), actor.id);
      visibility = `(type IN ('cadre', 'logistics'))
        OR (subject_rw = ANY($3::text[]) AND type NOT IN ('cadre', 'logistics'))
        OR (type = 'followup' AND payload->>'assignedCadreId' = $4)`;
      break;
    case 'ADMIN':
      params.push([...ADMIN_PULL]);
      visibility = 'type = ANY($3::text[])';
      break;
  }
  const r = await deps.pool.query<{
    seq: number; type: string; entity_id: string; subject_id: string | null; version: number; deleted: boolean; payload: JsonObject | null;
  }>(
    `SELECT seq, type, entity_id, subject_id, version, deleted, payload FROM entities
      WHERE seq > $1 AND type <> 'credential' AND (${visibility})
      ORDER BY seq LIMIT $2`,
    params,
  );
  const hasMore = r.rows.length > limit;
  const rows = hasMore ? r.rows.slice(0, limit) : r.rows;
  const items = rows.map((row) => {
    let payload = row.payload;
    if (payload && actor.role === 'ADMIN' && row.type === 'followup') payload = sanitizeFollowupForAdmin(payload);
    return {
      seq: row.seq, type: row.type, entityId: row.entity_id, subjectId: row.subject_id, version: row.version,
      deleted: row.deleted, payload: payload === null ? null : JSON.stringify(payload),
    };
  });
  const last = rows[rows.length - 1];
  return { items, nextCursor: last ? last.seq : cursor, hasMore };
}

/** ADMIN tidak menerima nilai pemeriksaan: alasan diganti label tanpa angka, catatan kader dikosongkan. */
export function sanitizeFollowupForAdmin(p: JsonObject): JsonObject {
  return { ...p, reason: sanitizeReason(p.reasonCode, p.reason), notes: '' };
}
