import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { Actor, Deps } from '../core/types.js';
import { ApiError, Errors } from '../core/errors.js';
import { parse, zDate, zTime } from '../core/validate.js';
import { audit } from '../core/audit.js';
import { isValidRwSpec, normalizeRw } from '../core/rw.js';
import { addDays, localDate } from '../core/time.js';
import { lockEntityWrites, withTx, type PoolClient } from '../db/pool.js';
import { actorOf, requireAuth } from '../auth/guard.js';
import { getAccount, revokeSessions } from '../auth/accounts.js';
import { hashPassword, passwordIssue } from '../auth/password.js';
import { reserveIds, markReservationUsed } from '../sync/ids.js';
import { getEntity, writeEntity, type JsonObject } from '../sync/store.js';
import { sanitizeReason } from '../sync/types.js';
import { RISK_LEVELS, Suppressor, rwRow, rwStats, screenedByMonth, sumStats } from './aggregates.js';
import { coerceThresholds, effectiveThresholds, getThresholds, setThresholds } from './thresholds.js';
import { createSchedule, deleteSchedule, getSchedule, listAll, updateSchedule } from './posyandu.js';

const zUuid = z.string().uuid();

const STATUS_FILTER: Record<string, string[] | null> = {
  OPEN: ['OPEN', 'IN_PROGRESS'],
  SCHEDULED: ['SCHEDULED'],
  DONE: ['DONE', 'CANCELLED'],
  ALL: null,
};

const posyanduSchema = z.object({
  rw: z.string().trim().regex(/^\d{1,3}$/),
  date: zDate,
  startTime: zTime,
  endTime: zTime,
  location: z.string().trim().min(1).max(200),
  notes: z.string().trim().max(500).optional().default(''),
}).refine((v) => v.startTime < v.endTime, { message: 'Jam mulai harus sebelum jam selesai', path: ['endTime'] });

const cadreCreateSchema = z.object({
  fullName: z.string().trim().min(2).max(100),
  rw: z.string().trim().max(40),
  password: z.string().max(200),
  facilityId: z.string().trim().max(100).optional(),
});

const cadrePatchSchema = z.object({
  active: z.boolean().optional(),
  fullName: z.string().trim().min(2).max(100).optional(),
  rw: z.string().trim().max(40).optional(),
  password: z.string().max(200).optional(),
}).refine((v) => Object.keys(v).length > 0, { message: 'Tidak ada perubahan' });

interface FollowupDto {
  id: string; sehatiId: string | null; rw: string | null; type: unknown; reason: string; priority: unknown; status: unknown;
  dueAt: unknown; assignedCadreId: string | null; assignedCadreName: string | null; createdAt: unknown;
}

function followupDto(row: { entity_id: string; subject_id: string | null; subject_rw: string | null; payload: JsonObject; cadre_name: string | null }): FollowupDto {
  const p = row.payload;
  return {
    id: row.entity_id,
    sehatiId: row.subject_id,
    rw: row.subject_rw,
    type: p.type ?? null,
    reason: sanitizeReason(p.reasonCode, p.reason),
    priority: p.priority ?? null,
    status: p.status ?? 'OPEN',
    dueAt: p.dueAt ?? null,
    assignedCadreId: typeof p.assignedCadreId === 'string' ? p.assignedCadreId : null,
    assignedCadreName: row.cadre_name,
    createdAt: p.createdAt ?? null,
  };
}

/** Tulis cadre entity agar perangkat kader/admin menerima perubahan lewat pull. */
async function upsertCadreEntity(c: PoolClient, id: string, fields: { assignedRw: string; active: boolean; facilityId?: string }, actor: Actor, now: number) {
  const existing = await getEntity(c, 'cadre', id, true);
  const base: JsonObject = existing?.payload ?? { sehatiId: id, facilityId: fields.facilityId ?? '', syncStatus: 'SYNCED', serverId: null };
  const version = (existing?.version ?? 0) + 1;
  const payload: JsonObject = { ...base, sehatiId: id, assignedRw: fields.assignedRw, active: fields.active, updatedAt: now, version };
  if (fields.facilityId !== undefined) payload.facilityId = fields.facilityId;
  await writeEntity(c, { type: 'cadre', entityId: id, subjectId: null, subjectRw: null, version, payload, deleted: false, actorId: actor.id, deviceId: null, now });
}

export async function adminRoutes(app: FastifyInstance, deps: Deps): Promise<void> {
  const tz = deps.config.timeZone;
  app.addHook('preHandler', requireAuth(deps, ['ADMIN']));

  // ------------------------------------------------------------ agregat
  app.get('/admin/overview', async () => {
    const now = deps.now();
    const t = await effectiveThresholds(deps.pool);
    const total = sumStats(await rwStats(deps.pool, t, now));
    const months = await screenedByMonth(deps.pool, now, tz);
    const sup = new Suppressor();
    return {
      registered: sup.cell(total.registered, 'registered'),
      screened30d: sup.cell(total.screened30d, 'screened30d'),
      followUpOpen: sup.cell(total.followUpOpen, 'followUpOpen'),
      followUpOverdue: sup.cell(total.followUpOverdue, 'followUpOverdue'),
      levels: Object.fromEntries(RISK_LEVELS.map((l) => [l, sup.cell(total.levels[l] ?? 0, `levels.${l}`)])),
      bp: {
        normal: sup.cell(total.bpNormal, 'bp.normal'),
        elevated: sup.cell(total.bpElevated, 'bp.elevated'),
        high: sup.cell(total.bpHigh, 'bp.high'),
      },
      smokers: sup.cell(total.smokers, 'smokers'),
      byMonth: months.map((m) => {
        const s = new Suppressor();
        const screened = s.cell(m.n);
        if (s.suppressed) sup.cell(m.n, `byMonth.${m.month}`);
        return { month: m.month, screened, suppressed: s.suppressed };
      }),
      suppressed: sup.suppressed,
      suppressedFields: sup.fields,
      updatedAt: now,
    };
  });

  app.get('/admin/rw', async () => {
    const t = await effectiveThresholds(deps.pool);
    return (await rwStats(deps.pool, t, deps.now())).map(rwRow);
  });

  app.get('/admin/reports/summary.csv', async (_req, reply) => {
    const t = await effectiveThresholds(deps.pool);
    const rows = (await rwStats(deps.pool, t, deps.now())).map(rwRow);
    const cell = (v: number | null, suppressed: boolean) => (v === null ? (suppressed ? '<5' : '') : String(v));
    const lines = ['rw,terdaftar,terskrining_30_hari,tindak_lanjut_terbuka,persen_td_tinggi'];
    for (const r of rows) {
      const pct = r.elevatedBpPct === null ? (r.suppressed ? '<5' : '') : (r.elevatedBpPct * 100).toFixed(0);
      lines.push([r.rw, cell(r.registered, r.suppressed), cell(r.screened, r.suppressed), cell(r.followUpOpen, r.suppressed), pct].join(','));
    }
    const day = localDate(deps.now(), tz);
    return reply
      .header('content-type', 'text/csv; charset=utf-8')
      .header('content-disposition', `attachment; filename="sehati-ringkasan-${day}.csv"`)
      .send('﻿' + lines.join('\r\n') + '\r\n');
  });

  // ------------------------------------------------------------ tindak lanjut
  app.get('/admin/followups', async (req) => {
    const q = parse(z.object({
      status: z.enum(['OPEN', 'SCHEDULED', 'DONE', 'ALL']).default('ALL'),
      rw: z.string().trim().regex(/^\d{1,3}$/).optional(),
    }), req.query);
    const statuses = STATUS_FILTER[q.status];
    const r = await deps.pool.query<{ entity_id: string; subject_id: string | null; subject_rw: string | null; payload: JsonObject; cadre_name: string | null }>(
      `SELECT e.entity_id, e.subject_id, e.subject_rw, e.payload, c.full_name AS cadre_name
         FROM entities e LEFT JOIN accounts c ON c.sehati_id = e.payload->>'assignedCadreId' AND c.role = 'KADER'
        WHERE e.type = 'followup' AND NOT e.deleted
          AND ($1::text[] IS NULL OR COALESCE(e.payload->>'status', 'OPEN') = ANY($1::text[]))
          AND ($2::text IS NULL OR e.subject_rw = $2)
        ORDER BY sehati_num(e.payload->>'priority') DESC NULLS LAST, sehati_num(e.payload->>'dueAt') NULLS LAST
        LIMIT 2000`,
      [statuses, q.rw ? normalizeRw(q.rw) : null],
    );
    return r.rows.map(followupDto);
  });

  async function mutateFollowup(id: string, actor: Actor, action: string, detail: string, mutate: (p: JsonObject) => JsonObject): Promise<FollowupDto> {
    const now = deps.now();
    return withTx(deps.pool, async (c) => {
      await lockEntityWrites(c);
      const e = await getEntity(c, 'followup', id, true);
      if (!e || e.deleted || !e.payload) throw Errors.notFound('Tindak lanjut tidak ditemukan.');
      const version = e.version + 1;
      const payload: JsonObject = { ...mutate(e.payload), updatedAt: now, version };
      await writeEntity(c, {
        type: 'followup', entityId: id, subjectId: e.subject_id, subjectRw: e.subject_rw, version, payload, deleted: false,
        actorId: actor.id, deviceId: null, now,
      });
      await audit(c, { actorId: actor.id, actorRole: actor.role, action, subjectId: e.subject_id, detail: `id=${id} ${detail}` }, now);
      const cadreId = typeof payload.assignedCadreId === 'string' ? payload.assignedCadreId : null;
      const cadre = cadreId ? await getAccount(c, cadreId) : null;
      return followupDto({ entity_id: id, subject_id: e.subject_id, subject_rw: e.subject_rw, payload, cadre_name: cadre?.full_name ?? null });
    });
  }

  app.post<{ Params: { id: string } }>('/admin/followups/:id/assign', async (req) => {
    const body = parse(z.object({ cadreId: z.string().trim().toUpperCase().nullable() }), req.body);
    if (body.cadreId !== null) {
      const cadre = await getAccount(deps.pool, body.cadreId);
      if (!cadre || cadre.role !== 'KADER' || !cadre.active) throw Errors.validation('Kader tidak ditemukan atau tidak aktif.');
    }
    // Sama dengan aplikasi: penugasan atas tindak lanjut OPEN menjadikannya SCHEDULED.
    return mutateFollowup(req.params.id, actorOf(req), 'followup_assign', `cadre=${body.cadreId ?? '-'}`, (p) => ({
      ...p,
      assignedCadreId: body.cadreId,
      status: body.cadreId !== null && (p.status ?? 'OPEN') === 'OPEN' ? 'SCHEDULED' : p.status,
    }));
  });

  app.post<{ Params: { id: string } }>('/admin/followups/:id/schedule', async (req) => {
    const body = parse(z.object({ dueAt: z.number().int().min(0).max(8_640_000_000_000) }), req.body);
    return mutateFollowup(req.params.id, actorOf(req), 'followup_schedule', `dueAt=${body.dueAt}`, (p) => ({
      ...p, dueAt: body.dueAt, status: 'SCHEDULED',
    }));
  });

  // ------------------------------------------------------------ kader
  app.get('/admin/cadres', async () => {
    const r = await deps.pool.query<{ sehati_id: string; full_name: string; rw: string; active: boolean; last_seen_at: number | null }>(
      `SELECT sehati_id, full_name, rw, active, last_seen_at FROM accounts WHERE role = 'KADER' ORDER BY sehati_id`,
    );
    return r.rows.map((a) => ({ sehatiId: a.sehati_id, fullName: a.full_name, rw: a.rw, active: a.active, lastSeenAt: a.last_seen_at }));
  });

  app.post('/admin/cadres', async (req, reply) => {
    const body = parse(cadreCreateSchema, req.body);
    const sehatiId = await createCadre(deps, actorOf(req), body);
    return reply.code(201).send({ sehatiId });
  });

  app.patch<{ Params: { id: string } }>('/admin/cadres/:id', async (req) => {
    const body = parse(cadrePatchSchema, req.body);
    const id = req.params.id.trim().toUpperCase();
    const actor = actorOf(req);
    if (body.rw !== undefined && !isValidRwSpec(body.rw)) throw Errors.validation('Format RW tidak valid.');
    if (body.password !== undefined) {
      const issue = passwordIssue(body.password);
      if (issue) throw Errors.validation(issue);
    }
    const pw = body.password !== undefined ? await hashPassword(body.password) : null;
    const now = deps.now();
    return withTx(deps.pool, async (c) => {
      await lockEntityWrites(c);
      const acc = await getAccount(c, id, true);
      if (!acc || acc.role !== 'KADER') throw Errors.notFound('Kader tidak ditemukan.');
      const rw = body.rw !== undefined ? normalizeRwSpec(body.rw) : acc.rw;
      const active = body.active ?? acc.active;
      const fullName = body.fullName ?? acc.full_name;
      await c.query(
        `UPDATE accounts SET full_name = $2, rw = $3, active = $4, salt = COALESCE($5, salt), hash = COALESCE($6, hash),
                iterations = COALESCE($7, iterations), failed_attempts = CASE WHEN $6::text IS NULL THEN failed_attempts ELSE 0 END,
                locked_until = CASE WHEN $6::text IS NULL THEN locked_until ELSE 0 END, updated_at = $8
          WHERE sehati_id = $1`,
        [id, fullName, rw, active, pw?.salt ?? null, pw?.hash ?? null, pw?.iterations ?? null, now],
      );
      if ((acc.active && !active) || pw) await revokeSessions(c, id);
      if (rw !== acc.rw || active !== acc.active) await upsertCadreEntity(c, id, { assignedRw: rw, active }, actor, now);
      const changed = Object.keys(body).filter((k) => k !== 'password').concat(pw ? ['password_reset'] : []);
      await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'cadre_update', subjectId: id, detail: changed.join(',') }, now);
      return { sehatiId: id, fullName, rw, active, lastSeenAt: acc.last_seen_at };
    });
  });

  // ------------------------------------------------------------ jadwal Posyandu
  app.get('/admin/posyandu', async () => listAll(deps.pool));

  app.post('/admin/posyandu', async (req, reply) => {
    const body = parse(posyanduSchema, req.body);
    const actor = actorOf(req);
    const now = deps.now();
    const dto = await withTx(deps.pool, async (c) => {
      const d = await createSchedule(c, { ...body, rw: normalizeRw(body.rw) }, actor.id, now);
      await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'posyandu_create', detail: `id=${d.id} rw=${d.rw} date=${d.date}` }, now);
      return d;
    });
    return reply.code(201).send(dto);
  });

  app.patch<{ Params: { id: string } }>('/admin/posyandu/:id', async (req) => {
    const id = parse(zUuid, req.params.id);
    const existing = await getSchedule(deps.pool, id);
    if (!existing) throw Errors.notFound('Jadwal tidak ditemukan.');
    const merged = parse(posyanduSchema, { ...existing, ...(req.body as object | null ?? {}) });
    const actor = actorOf(req);
    const now = deps.now();
    const dto = await updateSchedule(deps.pool, id, { ...merged, rw: normalizeRw(merged.rw) }, now);
    if (!dto) throw Errors.notFound('Jadwal tidak ditemukan.');
    await audit(deps.pool, { actorId: actor.id, actorRole: actor.role, action: 'posyandu_update', detail: `id=${id}` }, now);
    return dto;
  });

  app.delete<{ Params: { id: string } }>('/admin/posyandu/:id', async (req, reply) => {
    const id = parse(zUuid, req.params.id);
    const actor = actorOf(req);
    if (!(await deleteSchedule(deps.pool, id))) throw Errors.notFound('Jadwal tidak ditemukan.');
    await audit(deps.pool, { actorId: actor.id, actorRole: actor.role, action: 'posyandu_delete', detail: `id=${id}` }, deps.now());
    return reply.code(204).send();
  });

  // ------------------------------------------------------------ ambang klinis
  app.get('/admin/thresholds', async () => {
    const s = await getThresholds(deps.pool);
    return { thresholds: s.thresholds, version: s.version };
  });

  app.put('/admin/thresholds', async (req) => {
    const body = parse(z.object({ thresholds: z.unknown() }), req.body);
    let value = null;
    if (body.thresholds !== null && body.thresholds !== undefined) {
      const r = coerceThresholds(body.thresholds);
      if ('error' in r) throw new ApiError(400, 'INVALID_THRESHOLDS', r.error);
      value = r.value;
    }
    const actor = actorOf(req);
    const now = deps.now();
    return withTx(deps.pool, async (c) => {
      const version = await setThresholds(c, value, actor.id, now);
      await audit(c, { actorId: actor.id, actorRole: actor.role, action: value ? 'thresholds_update' : 'thresholds_reset', detail: `version=${version}` }, now);
      return { thresholds: value, version };
    });
  });

  // ------------------------------------------------------------ audit & pemakaian AI
  app.get('/admin/audit', async (req) => {
    const q = parse(z.object({ limit: z.coerce.number().int().min(1).max(1000).default(100) }), req.query);
    const r = await deps.pool.query<{ at: number; actor_id: string; actor_role: string; action: string; subject_id: string | null; detail: string }>(
      'SELECT at, actor_id, actor_role, action, subject_id, detail FROM audit_log ORDER BY at DESC, id DESC LIMIT $1',
      [q.limit],
    );
    return r.rows.map((a) => ({ at: a.at, actorId: a.actor_id, actorRole: a.actor_role, action: a.action, subjectId: a.subject_id, detail: a.detail }));
  });

  app.get('/admin/ai-usage', async (req) => {
    const q = parse(z.object({ days: z.coerce.number().int().min(1).max(365).default(30) }), req.query);
    const today = localDate(deps.now(), tz);
    const from = addDays(today, -(q.days - 1));
    const r = await deps.pool.query<{ day: string; messages: number; users: number; emergencies: number }>(
      `SELECT day::text AS day, sum(messages)::int AS messages, count(*) FILTER (WHERE messages > 0)::int AS users,
              sum(emergencies)::int AS emergencies
         FROM ai_usage WHERE day >= $1::date GROUP BY day ORDER BY day`,
      [from],
    );
    // Hitungan darurat 1–4 disupresi (sinyal kesehatan); hitungan pesan/pengguna bersifat operasional.
    return r.rows.map((row) => {
      const s = new Suppressor();
      return { day: row.day, messages: row.messages, users: row.users, emergencies: s.cell(row.emergencies), suppressed: s.suppressed };
    });
  });
}

function normalizeRwSpec(spec: string): string {
  return /^\d{1,3}$/.test(spec.trim()) ? normalizeRw(spec) : spec.replace(/\s+/g, '');
}

/** Buat akun kader baru (ID KD dari penghitung) + entitas cadre. Dipakai rute admin dan seed demo. */
export async function createCadre(
  deps: Deps, actor: Actor, input: { fullName: string; rw: string; password: string; facilityId?: string | undefined },
): Promise<string> {
  if (!isValidRwSpec(input.rw)) throw Errors.validation('Format RW tidak valid.');
  const issue = passwordIssue(input.password);
  if (issue) throw Errors.validation(issue);
  const pw = await hashPassword(input.password);
  const rw = normalizeRwSpec(input.rw);
  const now = deps.now();
  return withTx(deps.pool, async (c) => {
    await lockEntityWrites(c);
    const { ids } = await reserveIds(c, { prefix: 'KD', count: 1, deviceId: 'server', reservedBy: actor.id, now });
    const id = ids[0]!;
    await c.query(
      `INSERT INTO accounts (sehati_id, role, full_name, rw, village, salt, hash, iterations, consent_server_sync, registered_by, created_at, updated_at)
       VALUES ($1, 'KADER', $2, $3, $4, $5, $6, $7, true, $8, $9, $9)`,
      [id, input.fullName, rw, actor.village, pw.salt, pw.hash, pw.iterations, actor.id, now],
    );
    await markReservationUsed(c, id, now);
    await upsertCadreEntity(c, id, { assignedRw: rw, active: true, facilityId: input.facilityId ?? '' }, actor, now);
    await audit(c, { actorId: actor.id, actorRole: actor.role, action: 'cadre_create', subjectId: id, detail: `rw=${rw}` }, now);
    return id;
  });
}
