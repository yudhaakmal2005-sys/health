import { after, afterEach, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { pbkdf2Sync, randomBytes, randomUUID } from 'node:crypto';
import {
  PW, adminToken, call, closePool, createKader, detail, followup, item, login, measurement, pullAll, push, registerWarga, setup,
  userPayload, type TestCtx,
} from './helpers.js';

let ctx: TestCtx;
let admin: string;
beforeEach(async () => {
  ctx = await setup();
  admin = await adminToken(ctx.app);
});
afterEach(async () => { await ctx.close(); });
after(closePool);

const T = Date.UTC(2026, 9, 1);

describe('push: idempotensi & versi', () => {
  test('kiriman ulang dengan id sama → DUPLICATE; versi sama kunci baru → DUPLICATE; versi lebih rendah → CONFLICT', async () => {
    const w = await registerWarga(ctx.app, '02');
    const m = item({ type: 'measurement', entityId: 'm1', subjectId: w.id, version: 2, payload: measurement('m1', w.id, T, { version: 2 }) });
    const [first] = await push(ctx.app, w.token, [m]);
    assert.equal(first!.status, 'OK');
    assert.ok(first!.serverId);
    const [again] = await push(ctx.app, w.token, [m]);
    assert.equal(again!.status, 'DUPLICATE');
    assert.equal(again!.serverId, first!.serverId);

    const sameVersion = await push(ctx.app, w.token, [item({ type: 'measurement', entityId: 'm1', subjectId: w.id, version: 2, payload: measurement('m1', w.id, T) })]);
    assert.equal(sameVersion[0]!.status, 'DUPLICATE');
    const lower = await push(ctx.app, w.token, [item({ type: 'measurement', entityId: 'm1', subjectId: w.id, version: 1, payload: measurement('m1', w.id, T) })]);
    assert.equal(lower[0]!.status, 'CONFLICT');
    const higher = await push(ctx.app, w.token, [item({ type: 'measurement', entityId: 'm1', subjectId: w.id, version: 3, payload: measurement('m1', w.id, T, { notes: 'v3' }) })]);
    assert.equal(higher[0]!.status, 'OK');
    const row = await ctx.pool.query(`SELECT version, payload FROM entities WHERE type = 'measurement' AND entity_id = 'm1'`);
    assert.equal(row.rows[0].version, 3);
    assert.equal(row.rows[0].payload.notes, 'v3');
  });

  test('respons berurutan sesuai permintaan; tipe tak dikenal & payload rusak → REJECTED', async () => {
    const w = await registerWarga(ctx.app, '02');
    const items = [
      item({ type: 'measurement', entityId: 'm2', subjectId: w.id, payload: measurement('m2', w.id, T) }),
      item({ type: 'unknown', entityId: 'x', subjectId: w.id, payload: {} }),
      { ...item({ type: 'sleep', entityId: 's1', subjectId: w.id, payload: {} }), payload: 'not json' },
    ];
    const res = await push(ctx.app, w.token, items);
    assert.deepEqual(res.map((r) => r.id), items.map((i) => i.id));
    assert.deepEqual(res.map((r) => r.status), ['OK', 'REJECTED', 'REJECTED']);
  });

  test('subjectId boleh tidak dikirim: diturunkan dari payload (userId / measurement header)', async () => {
    const w = await registerWarga(ctx.app, '02');
    const m = { ...item({ type: 'measurement', entityId: 'm3', payload: measurement('m3', w.id, T) }), subjectId: undefined };
    const d = item({ type: 'measurement_detail', entityId: 'm3', payload: { measurementId: 'm3', anthropometry: null, bloodPressure: null, glucose: null, lipid: null } });
    const res = await push(ctx.app, w.token, [m, d]);
    assert.deepEqual(res.map((r) => r.status), ['OK', 'OK']);
    const row = await ctx.pool.query(`SELECT subject_id, subject_rw FROM entities WHERE type = 'measurement_detail'`);
    assert.deepEqual(row.rows[0], { subject_id: w.id, subject_rw: '02' });
  });

  test('batas 500 item per permintaan', async () => {
    const w = await registerWarga(ctx.app, '02');
    const items = Array.from({ length: 501 }, (_, i) => item({ type: 'habit', entityId: `h${i}`, subjectId: w.id, payload: {} }));
    const r = await call(ctx.app, 'POST', '/sync/push', w.token, { deviceId: 'd', items });
    assert.equal(r.statusCode, 400);
    assert.equal(r.json().error.code, 'VALIDATION_ERROR');
  });
});

describe('push: consent', () => {
  test('ditolak tanpa user consent; diterima bila user (consent=true) lebih awal di batch', async () => {
    const k = await createKader(ctx.app, admin, '04');
    const id = 'HM-000400';
    const noUser = await push(ctx.app, k.token, [item({ type: 'measurement', entityId: 'mx', subjectId: id, payload: measurement('mx', id, T) })]);
    assert.equal(noUser[0]!.status, 'REJECTED');
    assert.equal(noUser[0]!.message, 'Warga belum menyetujui sinkronisasi');

    const noConsent = await push(ctx.app, k.token, [
      item({ type: 'user', entityId: id, payload: userPayload(id, '04', { consentServerSync: false, registeredBy: k.id, hasAccount: false }) }),
      item({ type: 'measurement', entityId: 'mx', subjectId: id, payload: measurement('mx', id, T) }),
    ]);
    assert.deepEqual(noConsent.map((r) => r.status), ['REJECTED', 'REJECTED']);

    const ok = await push(ctx.app, k.token, [
      item({ type: 'user', entityId: id, payload: userPayload(id, '04', { registeredBy: k.id, hasAccount: false }) }),
      item({ type: 'household', entityId: 'hh1', subjectId: id, payload: { id: 'hh1', headName: 'KK', rw: '04', rt: '01', createdAt: 1, updatedAt: 1 } }),
      item({ type: 'measurement', entityId: 'mx', subjectId: id, payload: measurement('mx', id, T) }),
    ]);
    assert.deepEqual(ok.map((r) => r.status), ['OK', 'OK', 'OK']);

    // Pencabutan consent diterima, data berikutnya ditolak.
    const revoke = await push(ctx.app, k.token, [
      item({ type: 'user', entityId: id, version: 2, payload: userPayload(id, '04', { consentServerSync: false, registeredBy: k.id, version: 2 }) }),
      item({ type: 'measurement', entityId: 'my', subjectId: id, payload: measurement('my', id, T) }),
    ]);
    assert.deepEqual(revoke.map((r) => r.status), ['OK', 'REJECTED']);
  });
});

describe('push: RBAC', () => {
  test('WARGA: hanya data sendiri dan tipe warga', async () => {
    const a = await registerWarga(ctx.app, '02');
    const b = await registerWarga(ctx.app, '02');
    const res = await push(ctx.app, a.token, [
      item({ type: 'measurement', entityId: 'w1', subjectId: b.id, payload: measurement('w1', b.id, T) }),
      item({ type: 'household', entityId: 'hh', subjectId: a.id, payload: { id: 'hh' } }),
      item({ type: 'followup', entityId: 'f', subjectId: a.id, payload: followup('f', a.id) }),
      item({ type: 'cadre', entityId: 'KD-000001', payload: { sehatiId: 'KD-000001' } }),
      item({ type: 'user', entityId: a.id, payload: userPayload(a.id, '02', { role: 'ADMIN' }) }),
      item({ type: 'measurement', entityId: 'w2', subjectId: a.id, payload: measurement('w2', b.id, T) }), // userId palsu
      item({ type: 'medication', entityId: 'med1', subjectId: a.id, payload: { id: 'med1', userId: a.id, name: 'x', instructions: '', times: '08:00', active: true, createdAt: 1, updatedAt: 1 } }),
    ]);
    assert.deepEqual(res.map((r) => r.status), ['REJECTED', 'REJECTED', 'REJECTED', 'REJECTED', 'REJECTED', 'REJECTED', 'OK']);
  });

  test('KADER: tipe kerja untuk warga; bukan medication/medlog, bukan staf, bukan akun warga orang lain', async () => {
    const k = await createKader(ctx.app, admin, '02');
    const w = await registerWarga(ctx.app, '02'); // mandiri (tidak didaftarkan kader)
    const res = await push(ctx.app, k.token, [
      item({ type: 'measurement', entityId: 'k1', subjectId: w.id, payload: measurement('k1', w.id, T) }),
      item({ type: 'visit', entityId: 'v1', subjectId: w.id, payload: { id: 'v1', userId: w.id, cadreId: k.id } }),
      item({ type: 'followup', entityId: 'f1', subjectId: w.id, payload: followup('f1', w.id) }),
      item({ type: 'medication', entityId: 'med', subjectId: w.id, payload: { id: 'med', userId: w.id } }),
      item({ type: 'medlog', entityId: 'ml', subjectId: w.id, payload: { id: 'ml', userId: w.id } }),
      item({ type: 'measurement', entityId: 'k2', subjectId: k.id, payload: measurement('k2', k.id, T) }),
      item({ type: 'user', entityId: w.id, version: 5, payload: userPayload(w.id, '02', { registeredBy: k.id }) }),
      item({ type: 'logistics', entityId: 'l1', payload: { id: 'l1', name: 'Strip', unit: 'pcs', stock: 1, minStock: 1, facilityId: null, updatedAt: 1 } }),
      item({ type: 'user', entityId: 'AD-000001', payload: userPayload('AD-000001', '02', { registeredBy: k.id }) }),
    ]);
    assert.deepEqual(res.map((r) => r.status), ['OK', 'OK', 'OK', 'REJECTED', 'REJECTED', 'REJECTED', 'REJECTED', 'REJECTED', 'REJECTED']);
  });

  test('KADER mendaftarkan warga + kredensial (hash format aplikasi) → warga dapat login', async () => {
    const k = await createKader(ctx.app, admin, '05');
    const id = 'HM-000501';
    const salt = randomBytes(16);
    const hash = pbkdf2Sync('kataSandi9', salt, 120_000, 32, 'sha256').toString('base64');
    const res = await push(ctx.app, k.token, [
      item({ type: 'user', entityId: id, payload: userPayload(id, '05', { registeredBy: k.id }) }),
      item({ type: 'credential', entityId: id, subjectId: id, payload: { sehatiId: id, salt: salt.toString('base64'), hash, iterations: 120_000 } }),
    ]);
    assert.deepEqual(res.map((r) => r.status), ['OK', 'OK']);
    await login(ctx.app, id, 'kataSandi9');
    // kader lain tidak boleh mengubah kredensial warga ini
    const k2 = await createKader(ctx.app, admin, '05');
    const r2 = await push(ctx.app, k2.token, [
      item({ type: 'credential', entityId: id, subjectId: id, version: 2, payload: { sehatiId: id, salt: salt.toString('base64'), hash, iterations: 120_000 } }),
    ]);
    assert.equal(r2[0]!.status, 'REJECTED');
    // hash tidak pernah disimpan di entities
    const e = await ctx.pool.query(`SELECT payload FROM entities WHERE type = 'credential'`);
    assert.deepEqual(e.rows[0].payload, { sehatiId: id, iterations: 120_000 });
  });

  test('ADMIN: hanya cadre/logistics/followup; followup admin hanya mengubah field penugasan', async () => {
    const w = await registerWarga(ctx.app, '02');
    const k = await createKader(ctx.app, admin, '02');
    await push(ctx.app, k.token, [item({ type: 'followup', entityId: 'f9', subjectId: w.id, payload: followup('f9', w.id) })]);
    const res = await push(ctx.app, admin, [
      item({ type: 'measurement', entityId: 'a1', subjectId: w.id, payload: measurement('a1', w.id, T) }),
      item({ type: 'user', entityId: 'HM-000600', payload: userPayload('HM-000600', '02') }),
      item({ type: 'logistics', entityId: 'l1', payload: { id: 'l1', name: 'Strip', unit: 'pcs', stock: 5, minStock: 1, facilityId: null, updatedAt: 1 } }),
      item({ type: 'followup', entityId: 'f9', subjectId: w.id, version: 2, payload: followup('f9', w.id, { assignedCadreId: k.id, reason: 'diubah', notes: 'x', version: 2 }) }),
      item({ type: 'followup', entityId: 'f-new', subjectId: w.id, payload: followup('f-new', w.id) }),
    ]);
    assert.deepEqual(res.map((r) => r.status), ['REJECTED', 'REJECTED', 'OK', 'OK', 'REJECTED']);
    const f = await ctx.pool.query(`SELECT payload FROM entities WHERE type = 'followup' AND entity_id = 'f9'`);
    assert.equal(f.rows[0].payload.assignedCadreId, k.id);
    assert.equal(f.rows[0].payload.reason, 'Tekanan darah 150/95 perlu diukur ulang');
    assert.equal(f.rows[0].payload.notes, 'catatan kader');
  });

  test('tanpa token → 401', async () => {
    const r = await call(ctx.app, 'POST', '/sync/push', null, { deviceId: 'd', items: [] });
    assert.equal(r.statusCode, 401);
  });
});

describe('push: DELETE', () => {
  test('tipe non-user: tombstone (deleted=true, versi & seq naik) dengan RBAC sama', async () => {
    const w = await registerWarga(ctx.app, '02');
    const log = { id: 'ml1', userId: w.id, medicationId: 'med1', dateIso: '2026-10-01', time: '08:00', takenAt: 1, updatedAt: 1, version: 1 };
    await push(ctx.app, w.token, [item({ type: 'medlog', entityId: 'ml1', subjectId: w.id, payload: log })]);
    const before = await pullAll(ctx.app, w.token);
    const seqBefore = before.find((i) => i.entityId === 'ml1')!.seq;
    const del = await push(ctx.app, w.token, [item({ type: 'medlog', entityId: 'ml1', subjectId: w.id, operation: 'DELETE', version: 1, payload: log })]);
    assert.equal(del[0]!.status, 'OK');
    const after = (await pullAll(ctx.app, w.token)).find((i) => i.entityId === 'ml1')!;
    assert.equal(after.deleted, true);
    assert.equal(after.payload, null);
    assert.equal(after.version, 2);
    assert.ok(after.seq > seqBefore);
    // warga lain tidak boleh menghapus
    const other = await registerWarga(ctx.app, '02');
    const r = await push(ctx.app, other.token, [item({ type: 'medlog', entityId: 'ml1', subjectId: w.id, operation: 'DELETE', version: 9, payload: {} })]);
    assert.equal(r[0]!.status, 'REJECTED');
    // diaktifkan lagi dengan versi lebih tinggi
    const revive = await push(ctx.app, w.token, [item({ type: 'medlog', entityId: 'ml1', subjectId: w.id, version: 3, payload: { ...log, version: 3 } })]);
    assert.equal(revive[0]!.status, 'OK');
  });

  test('user DELETE: hak penghapusan — payload dihapus, akun & sesi hilang, tombstone ter-pull kader', async () => {
    const k = await createKader(ctx.app, admin, '02');
    const w = await registerWarga(ctx.app, '02');
    await push(ctx.app, w.token, [
      item({ type: 'user', entityId: w.id, payload: userPayload(w.id, '02') }),
      item({ type: 'measurement', entityId: 'e1', subjectId: w.id, payload: measurement('e1', w.id, T) }),
      item({ type: 'measurement_detail', entityId: 'e1', subjectId: w.id, payload: detail('e1', w.id, 150, 95) }),
    ]);
    await push(ctx.app, k.token, [item({ type: 'followup', entityId: 'fe', subjectId: w.id, payload: followup('fe', w.id) })]);
    const res = await push(ctx.app, w.token, [item({ type: 'user', entityId: w.id, operation: 'DELETE', version: 2, payload: userPayload(w.id, '02', { fullName: '' }) })]);
    assert.equal(res[0]!.status, 'OK');

    const rows = await ctx.pool.query('SELECT type, deleted, payload FROM entities WHERE subject_id = $1', [w.id]);
    assert.equal(rows.rowCount, 4);
    assert.ok(rows.rows.every((r) => r.deleted === true && r.payload === null));
    assert.equal((await ctx.pool.query('SELECT 1 FROM accounts WHERE sehati_id = $1', [w.id])).rowCount, 0);
    assert.equal((await ctx.pool.query('SELECT 1 FROM sessions WHERE account_id = $1', [w.id])).rowCount, 0);
    const lg = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: w.id, password: PW, deviceId: 'd' });
    assert.equal(lg.statusCode, 401);
    const kPull = (await pullAll(ctx.app, k.token)).filter((i) => i.subjectId === w.id);
    assert.ok(kPull.length >= 4);
    assert.ok(kPull.every((i) => i.deleted && i.payload === null));
    // perangkat lama yang mengirim ulang data warga yang dihapus ditolak
    const stale = await push(ctx.app, k.token, [item({ type: 'measurement', entityId: 'e2', subjectId: w.id, payload: measurement('e2', w.id, T) })]);
    assert.equal(stale[0]!.status, 'REJECTED');
    const audit = await ctx.pool.query(`SELECT 1 FROM audit_log WHERE action = 'user_erase' AND subject_id = $1`, [w.id]);
    assert.equal(audit.rowCount, 1);
  });
});

describe('pull: visibilitas & kursor', () => {
  async function scenario() {
    const k2 = await createKader(ctx.app, admin, '02');
    const k3 = await createKader(ctx.app, admin, '03');
    const a = await registerWarga(ctx.app, '02');
    const b = await registerWarga(ctx.app, '03');
    const salt = randomBytes(16).toString('base64');
    const hash = pbkdf2Sync('abcdef', Buffer.from(salt, 'base64'), 120_000, 32, 'sha256').toString('base64');
    await push(ctx.app, a.token, [
      item({ type: 'user', entityId: a.id, payload: userPayload(a.id, '02') }),
      item({ type: 'credential', entityId: a.id, subjectId: a.id, payload: { sehatiId: a.id, salt, hash, iterations: 120_000 } }),
      item({ type: 'measurement', entityId: 'pa', subjectId: a.id, payload: measurement('pa', a.id, T) }),
      item({ type: 'medication', entityId: 'meda', subjectId: a.id, payload: { id: 'meda', userId: a.id } }),
    ]);
    await push(ctx.app, b.token, [
      item({ type: 'user', entityId: b.id, payload: userPayload(b.id, '03') }),
      item({ type: 'measurement', entityId: 'pb', subjectId: b.id, payload: measurement('pb', b.id, T) }),
    ]);
    await push(ctx.app, k2.token, [item({ type: 'followup', entityId: 'fa', subjectId: a.id, payload: followup('fa', a.id) })]);
    await push(ctx.app, admin, [item({ type: 'logistics', entityId: 'log1', payload: { id: 'log1', name: 'Strip', unit: 'pcs', stock: 5, minStock: 1, facilityId: null, updatedAt: 1 } })]);
    return { k2, k3, a, b };
  }

  test('WARGA hanya miliknya; KADER RW tugasnya + cadre/logistics; ADMIN hanya cadre/logistics/followup; credential tidak pernah', async () => {
    const { k2, k3, a } = await scenario();
    const wa = await pullAll(ctx.app, a.token);
    assert.ok(wa.length > 0);
    assert.ok(wa.every((i) => i.subjectId === a.id));
    assert.deepEqual(new Set(wa.map((i) => i.type)), new Set(['user', 'measurement', 'medication', 'followup']));

    const kp = await pullAll(ctx.app, k2.token);
    assert.ok(kp.every((i) => i.subjectId === a.id || i.type === 'cadre' || i.type === 'logistics'));
    assert.ok(kp.some((i) => i.type === 'medication'));
    assert.equal(kp.filter((i) => i.type === 'cadre').length, 2);
    assert.ok(kp.some((i) => i.type === 'logistics'));
    const k3p = await pullAll(ctx.app, k3.token);
    assert.ok(!k3p.some((i) => i.subjectId === a.id));
    assert.ok(k3p.some((i) => i.entityId === 'pb'));

    const ap = await pullAll(ctx.app, admin);
    assert.deepEqual(new Set(ap.map((i) => i.type)), new Set(['cadre', 'logistics', 'followup']));
    const f = JSON.parse(ap.find((i) => i.type === 'followup')!.payload!);
    assert.equal(f.reason, 'Tekanan darah perlu diukur ulang'); // tanpa nilai pemeriksaan
    assert.equal(f.notes, '');

    for (const list of [wa, kp, k3p, ap]) assert.ok(!list.some((i) => i.type === 'credential'));
    for (const list of [wa, kp]) assert.ok(!list.some((i) => (i.payload ?? '').includes('"hash"')));
  });

  test('kursor: seq naik monoton, paging dengan hasMore', async () => {
    const w = await registerWarga(ctx.app, '02');
    const items = Array.from({ length: 7 }, (_, i) => item({ type: 'habit', entityId: `${w.id}|2026-10-0${i + 1}`, subjectId: w.id, payload: { id: `${w.id}|2026-10-0${i + 1}`, userId: w.id } }));
    await push(ctx.app, w.token, items);
    const p1 = (await call(ctx.app, 'GET', '/sync/pull?cursor=0&limit=3', w.token)).json();
    assert.equal(p1.items.length, 3);
    assert.equal(p1.hasMore, true);
    assert.equal(p1.nextCursor, p1.items[2].seq);
    const all = await pullAll(ctx.app, w.token, 3);
    assert.equal(all.length, 7);
    const seqs = all.map((i) => i.seq);
    assert.deepEqual(seqs, [...seqs].sort((a, b) => a - b));
    assert.equal(new Set(seqs).size, 7);
    const last = (await call(ctx.app, 'GET', `/sync/pull?cursor=${seqs[6]}`, w.token)).json();
    assert.deepEqual(last, { items: [], nextCursor: seqs[6], hasMore: false });
    // payload adalah string JSON entitas apa adanya
    assert.equal(JSON.parse(all[0]!.payload!).userId, w.id);
  });

  test('perpindahan RW warga memindahkan visibilitas ke kader RW baru', async () => {
    const k3 = await createKader(ctx.app, admin, '03');
    const w = await registerWarga(ctx.app, '02');
    await push(ctx.app, w.token, [
      item({ type: 'user', entityId: w.id, payload: userPayload(w.id, '02') }),
      item({ type: 'measurement', entityId: 'mv', subjectId: w.id, payload: measurement('mv', w.id, T) }),
    ]);
    assert.ok(!(await pullAll(ctx.app, k3.token)).some((i) => i.entityId === 'mv'));
    await push(ctx.app, w.token, [item({ type: 'user', entityId: w.id, version: 2, payload: userPayload(w.id, '03', { version: 2 }) })]);
    assert.ok((await pullAll(ctx.app, k3.token)).some((i) => i.entityId === 'mv'));
  });
});

describe('bentrok ID', () => {
  test('kader mendaftarkan ID yang sudah dipakai warga lain → CONFLICT', async () => {
    const w = await registerWarga(ctx.app, '02');
    const k = await createKader(ctx.app, admin, '02');
    const r = await push(ctx.app, k.token, [item({ type: 'user', entityId: w.id, payload: userPayload(w.id, '02', { registeredBy: k.id, fullName: 'Orang Lain' }) })]);
    assert.equal(r[0]!.status, 'REJECTED'); // akun warga mandiri: kader bukan pendaftarnya
    const id = (await call(ctx.app, 'POST', '/ids/reserve', null, { deviceId: 'dev-lain', count: 1, prefix: 'HM' })).json().ids[0];
    const c = await push(ctx.app, k.token, [item({ type: 'user', entityId: id, payload: userPayload(id, '02', { registeredBy: k.id }) })], 'dev-kader');
    assert.equal(c[0]!.status, 'CONFLICT');
  });

  test('ID dari penghitung lokal perangkat memajukan penghitung server', async () => {
    const k = await createKader(ctx.app, admin, '02');
    await push(ctx.app, k.token, [item({ type: 'user', entityId: 'HM-000050', payload: userPayload('HM-000050', '02', { registeredBy: k.id }) })]);
    const r = await call(ctx.app, 'POST', '/ids/reserve', k.token, { deviceId: 'd', count: 1, prefix: 'HM' });
    assert.equal(r.json().ids[0], 'HM-000051');
    void randomUUID;
  });
});
