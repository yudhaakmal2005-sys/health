import { after, afterEach, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  adminToken, call, closePool, createKader, detail, followup, item, measurement, pullAll, push, registerWarga, setup, userPayload,
  type TestCtx,
} from './helpers.js';

let ctx: TestCtx;
let admin: string;
beforeEach(async () => {
  ctx = await setup();
  admin = await adminToken(ctx.app);
});
afterEach(async () => { await ctx.close(); });
after(closePool);

/** Buat n warga di RW dengan pemeriksaan (TD), profil, asesmen. */
async function populate(rw: string, n: number, opts: { sys?: number; dia?: number; smoker?: boolean; level?: string } = {}) {
  const out: { id: string; token: string }[] = [];
  const t = ctx.clock.t - 86_400_000;
  for (let i = 0; i < n; i++) {
    const w = await registerWarga(ctx.app, rw);
    const mid = `m-${w.id}`;
    await push(ctx.app, w.token, [
      item({ type: 'user', entityId: w.id, payload: userPayload(w.id, rw) }),
      item({ type: 'measurement', entityId: mid, subjectId: w.id, payload: measurement(mid, w.id, t) }),
      item({ type: 'measurement_detail', entityId: mid, subjectId: w.id, payload: detail(mid, w.id, opts.sys ?? 150, opts.dia ?? 85) }),
      item({ type: 'profile', entityId: w.id, subjectId: w.id, payload: { userId: w.id, level: opts.level ?? 'HIGHER_MONITORING', findingsJson: '[]', planJson: '[]', rulesetVersion: 'x', computedAt: t } }),
      item({ type: 'assessment', entityId: `as-${w.id}`, subjectId: w.id, payload: { id: `as-${w.id}`, userId: w.id, takenAt: t, smokingStatus: opts.smoker ? 'CURRENT' : 'NEVER' } }),
    ]);
    out.push(w);
  }
  return out;
}

describe('RBAC admin', () => {
  test('WARGA dan KADER tidak boleh mengakses /admin/*', async () => {
    const w = await registerWarga(ctx.app, '02');
    const k = await createKader(ctx.app, admin, '02');
    for (const t of [w.token, k.token]) {
      const r = await call(ctx.app, 'GET', '/admin/overview', t);
      assert.equal(r.statusCode, 403);
      assert.equal(r.json().error.code, 'FORBIDDEN');
    }
    assert.equal((await call(ctx.app, 'GET', '/admin/overview')).statusCode, 401);
  });
});

describe('agregat & supresi sel kecil', () => {
  test('hitungan 1–4 → null + suppressed', async () => {
    await populate('01', 3, { smoker: true });
    const r = await call(ctx.app, 'GET', '/admin/overview', admin);
    assert.equal(r.statusCode, 200);
    const o = r.json();
    assert.equal(o.registered, null);
    assert.equal(o.screened30d, null);
    assert.equal(o.smokers, null);
    assert.equal(o.bp.high, null);
    assert.equal(o.bp.normal, 0); // nol bukan sel kecil
    assert.equal(o.levels.HIGHER_MONITORING, null);
    assert.equal(o.levels.HEALTHY_HABIT, 0);
    assert.equal(o.suppressed, true);
    assert.ok(o.suppressedFields.includes('registered'));
    const month = o.byMonth.find((m: { month: string }) => m.month === '2026-10');
    assert.deepEqual(month, { month: '2026-10', screened: null, suppressed: true });
    assert.equal(o.byMonth.length, 6);

    const rw = (await call(ctx.app, 'GET', '/admin/rw', admin)).json();
    assert.deepEqual(rw, [{ rw: '01', registered: null, screened: null, followUpOpen: null, elevatedBpPct: null, suppressed: true }]);
  });

  test('hitungan ≥ 5 dikirim; persen TD tinggi per RW; CSV', async () => {
    const ws = await populate('02', 6, { sys: 150 });
    await populate('02', 4, { sys: 110, dia: 70, level: 'HEALTHY_HABIT' });
    const k = await createKader(ctx.app, admin, '02');
    await push(ctx.app, k.token, ws.slice(0, 5).map((w, i) => item({
      type: 'followup', entityId: `f${i}`, subjectId: w.id, payload: followup(`f${i}`, w.id, { dueAt: ctx.clock.t - 1000 }),
    })));
    const o = (await call(ctx.app, 'GET', '/admin/overview', admin)).json();
    assert.equal(o.registered, 10);
    assert.equal(o.screened30d, 10);
    assert.equal(o.bp.high, 6);
    assert.equal(o.bp.normal, null); // 4 → disupresi
    assert.equal(o.followUpOpen, 5);
    assert.equal(o.followUpOverdue, 5);
    assert.equal(o.levels.HIGHER_MONITORING, 6);
    assert.equal(typeof o.updatedAt, 'number');

    const rw = (await call(ctx.app, 'GET', '/admin/rw', admin)).json();
    assert.deepEqual(rw, [{ rw: '02', registered: 10, screened: 10, followUpOpen: 5, elevatedBpPct: 0.6, suppressed: false }]);

    const csv = await call(ctx.app, 'GET', '/admin/reports/summary.csv', admin);
    assert.equal(csv.statusCode, 200);
    assert.match(csv.headers['content-type'] as string, /^text\/csv/);
    const lines = csv.body.replace('﻿', '').trim().split('\r\n');
    assert.equal(lines[0], 'rw,terdaftar,terskrining_30_hari,tindak_lanjut_terbuka,persen_td_tinggi');
    assert.equal(lines[1], '02,10,10,5,60');
  });

  test('ambang tersimpan memengaruhi klasifikasi TD', async () => {
    await populate('03', 5, { sys: 135, dia: 70 });
    let o = (await call(ctx.app, 'GET', '/admin/overview', admin)).json();
    assert.equal(o.bp.elevated, 5);
    await call(ctx.app, 'PUT', '/admin/thresholds', admin, { thresholds: { bpHighSys: 130 } });
    o = (await call(ctx.app, 'GET', '/admin/overview', admin)).json();
    assert.equal(o.bp.high, 5);
  });
});

describe('tindak lanjut', () => {
  test('daftar tanpa nama & nilai; assign menaikkan versi dan muncul di pull kader', async () => {
    const k = await createKader(ctx.app, admin, '04', 'Bu Kader');
    const w = await registerWarga(ctx.app, '02');
    await push(ctx.app, w.token, [item({ type: 'user', entityId: w.id, payload: userPayload(w.id, '02') })]);
    await push(ctx.app, w.token, []); // no-op
    const kader02 = await createKader(ctx.app, admin, '02');
    await push(ctx.app, kader02.token, [item({ type: 'followup', entityId: 'fu1', subjectId: w.id, payload: followup('fu1', w.id) })]);

    const list = (await call(ctx.app, 'GET', '/admin/followups?status=OPEN&rw=02', admin)).json();
    assert.equal(list.length, 1);
    assert.deepEqual(Object.keys(list[0]).sort(), ['assignedCadreId', 'assignedCadreName', 'createdAt', 'dueAt', 'id', 'priority', 'reason', 'rw', 'sehatiId', 'status', 'type'].sort());
    assert.equal(list[0].reason, 'Tekanan darah perlu diukur ulang');
    assert.ok(!JSON.stringify(list).includes('Warga Uji'));
    assert.equal((await call(ctx.app, 'GET', '/admin/followups?rw=03', admin)).json().length, 0);

    // kader RW 04 belum melihatnya
    assert.ok(!(await pullAll(ctx.app, k.token)).some((i) => i.entityId === 'fu1'));
    const a = await call(ctx.app, 'POST', '/admin/followups/fu1/assign', admin, { cadreId: k.id });
    assert.equal(a.statusCode, 200);
    assert.equal(a.json().assignedCadreId, k.id);
    assert.equal(a.json().assignedCadreName, 'Bu Kader');
    assert.equal(a.json().status, 'SCHEDULED');
    const pulled = (await pullAll(ctx.app, k.token)).find((i) => i.entityId === 'fu1')!;
    assert.equal(pulled.version, 2);
    const p = JSON.parse(pulled.payload!);
    assert.equal(p.assignedCadreId, k.id);
    assert.equal(p.version, 2);
    assert.equal(p.updatedAt, ctx.clock.t);
    assert.equal(p.reason, 'Tekanan darah 150/95 perlu diukur ulang'); // kader tetap menerima alasan lengkap

    const s = await call(ctx.app, 'POST', '/admin/followups/fu1/schedule', admin, { dueAt: 1_790_000_000_000 });
    assert.equal(s.json().dueAt, 1_790_000_000_000);
    const v = await ctx.pool.query(`SELECT version FROM entities WHERE type = 'followup' AND entity_id = 'fu1'`);
    assert.equal(v.rows[0].version, 3);

    assert.equal((await call(ctx.app, 'POST', '/admin/followups/fu1/assign', admin, { cadreId: 'KD-999999' })).statusCode, 400);
    assert.equal((await call(ctx.app, 'POST', '/admin/followups/nope/assign', admin, { cadreId: null })).statusCode, 404);
    const unassign = await call(ctx.app, 'POST', '/admin/followups/fu1/assign', admin, { cadreId: null });
    assert.equal(unassign.json().assignedCadreId, null);
    const audit = (await call(ctx.app, 'GET', '/admin/audit?limit=50', admin)).json();
    assert.ok(audit.some((x: { action: string }) => x.action === 'followup_assign'));
    assert.ok(audit.some((x: { action: string }) => x.action === 'followup_schedule'));
  });
});

describe('ambang klinis', () => {
  test('validasi sama dengan aplikasi; versi naik; null = bawaan; /config mengikutinya', async () => {
    const get0 = (await call(ctx.app, 'GET', '/admin/thresholds', admin)).json();
    assert.deepEqual(get0, { thresholds: null, version: 0 });

    const bad = [
      { bpHighSys: 210 },
      { bpNormalSys: 150 },
      { bpHighDia: 100, bpStage2Dia: 95 },
      { glucoseLow: 150 },
      { gdpElevated: 130 },
      { cholBorderline: 250 },
      { bmiOver: 26 },
      { waistMale: 140 },
      { activeMinutesPerWeekGoal: 20 },
      { bpHighSys: 140.5 },
      { unknownField: 1 },
      { bpHighSys: '140' },
    ];
    for (const t of bad) {
      const r = await call(ctx.app, 'PUT', '/admin/thresholds', admin, { thresholds: t });
      assert.equal(r.statusCode, 400, JSON.stringify(t));
      assert.equal(r.json().error.code, 'INVALID_THRESHOLDS');
    }
    const ok = await call(ctx.app, 'PUT', '/admin/thresholds', admin, { thresholds: { bpHighSys: 135, bmiOver: 23.5 } });
    assert.equal(ok.statusCode, 200);
    assert.equal(ok.json().version, 1);
    assert.equal(ok.json().thresholds.bpHighSys, 135);
    assert.equal(ok.json().thresholds.bpNormalSys, 120);
    assert.equal(Object.keys(ok.json().thresholds).length, 25);

    const w = await registerWarga(ctx.app, '02');
    const cfg = (await call(ctx.app, 'GET', '/config', w.token)).json();
    assert.equal(cfg.thresholdsVersion, 1);
    assert.equal(cfg.thresholds.bpHighSys, 135);
    assert.equal(cfg.emergencyNumbers, '119 atau 112');

    const reset = await call(ctx.app, 'PUT', '/admin/thresholds', admin, { thresholds: null });
    assert.deepEqual(reset.json(), { thresholds: null, version: 2 });
    const cfg2 = (await call(ctx.app, 'GET', '/config', w.token)).json();
    assert.equal(cfg2.thresholds, null);
    assert.equal(cfg2.thresholdsVersion, 2);
    const audit = await ctx.pool.query(`SELECT action FROM audit_log WHERE action LIKE 'thresholds%' ORDER BY id`);
    assert.deepEqual(audit.rows.map((r) => r.action), ['thresholds_update', 'thresholds_reset']);
  });
});

describe('jadwal Posyandu', () => {
  test('CRUD admin dan visibilitas per RW di /config', async () => {
    const mk = (rw: string, date: string) => call(ctx.app, 'POST', '/admin/posyandu', admin, {
      rw, date, startTime: '08:00', endTime: '11:00', location: `Balai RW ${rw}`, notes: 'Bawa QR SEHATI',
    });
    const a = await mk('02', '2026-10-12');
    assert.equal(a.statusCode, 201);
    assert.match(a.json().id, /^[0-9a-f-]{36}$/);
    await mk('02', '2026-10-03'); // hari ini (Asia/Jakarta) tetap tampil
    await mk('02', '2026-10-01'); // lewat
    await mk('03', '2026-10-20');
    await mk('05', '2026-10-21');
    assert.equal((await call(ctx.app, 'POST', '/admin/posyandu', admin, { rw: '02', date: '2026-13-01', startTime: '08:00', endTime: '11:00', location: 'x' })).statusCode, 400);
    assert.equal((await call(ctx.app, 'POST', '/admin/posyandu', admin, { rw: '02', date: '2026-10-10', startTime: '11:00', endTime: '08:00', location: 'x' })).statusCode, 400);

    const w = await registerWarga(ctx.app, '02');
    const wc = (await call(ctx.app, 'GET', '/config', w.token)).json();
    assert.deepEqual(wc.posyandu.map((p: { date: string; rw: string }) => `${p.rw}:${p.date}`), ['02:2026-10-03', '02:2026-10-12']);
    assert.deepEqual(Object.keys(wc.posyandu[0]).sort(), ['date', 'endTime', 'id', 'location', 'notes', 'rw', 'startTime']);

    const k = await createKader(ctx.app, admin, '03-05');
    const kc = (await call(ctx.app, 'GET', '/config', k.token)).json();
    assert.deepEqual(kc.posyandu.map((p: { rw: string }) => p.rw), ['03', '05']);

    const ac = (await call(ctx.app, 'GET', '/config', admin)).json();
    assert.equal(ac.posyandu.length, 4);
    assert.equal((await call(ctx.app, 'GET', '/admin/posyandu', admin)).json().length, 5);

    const id = a.json().id;
    const p = await call(ctx.app, 'PATCH', `/admin/posyandu/${id}`, admin, { location: 'Masjid RW 02' });
    assert.equal(p.statusCode, 200);
    assert.equal(p.json().location, 'Masjid RW 02');
    assert.equal(p.json().startTime, '08:00');
    assert.equal((await call(ctx.app, 'DELETE', `/admin/posyandu/${id}`, admin)).statusCode, 204);
    assert.equal((await call(ctx.app, 'DELETE', `/admin/posyandu/${id}`, admin)).statusCode, 404);
  });
});

describe('kader', () => {
  test('buat (201 KD-xxxxxx), daftar, patch; entitas cadre ter-pull', async () => {
    const r = await call(ctx.app, 'POST', '/admin/cadres', admin, { fullName: 'Siti Aminah', rw: '2', password: 'kader123' });
    assert.equal(r.statusCode, 201);
    assert.deepEqual(r.json(), { sehatiId: 'KD-000001' });
    assert.equal((await call(ctx.app, 'POST', '/admin/cadres', admin, { fullName: 'X', rw: '02', password: 'kader123' })).statusCode, 400);
    assert.equal((await call(ctx.app, 'POST', '/admin/cadres', admin, { fullName: 'Siti', rw: '02', password: '123' })).statusCode, 400);
    const list = (await call(ctx.app, 'GET', '/admin/cadres', admin)).json();
    assert.deepEqual(list, [{ sehatiId: 'KD-000001', fullName: 'Siti Aminah', rw: '02', active: true, lastSeenAt: null }]);
    const p = await call(ctx.app, 'PATCH', '/admin/cadres/KD-000001', admin, { active: false });
    assert.equal(p.json().active, false);
    const cadre = (await pullAll(ctx.app, admin)).filter((i) => i.type === 'cadre');
    assert.equal(cadre.length, 1);
    const payload = JSON.parse(cadre[0]!.payload!);
    assert.equal(payload.active, false);
    assert.equal(payload.assignedRw, '02');
    assert.equal(cadre[0]!.version, 2);
    const audit = await ctx.pool.query(`SELECT action FROM audit_log WHERE action LIKE 'cadre%' ORDER BY id`);
    assert.deepEqual(audit.rows.map((x) => x.action), ['cadre_create', 'cadre_update']);
  });
});

describe('audit & pemakaian AI', () => {
  test('audit berisi login; ai-usage per hari dengan supresi darurat', async () => {
    const audit = (await call(ctx.app, 'GET', '/admin/audit?limit=5', admin)).json();
    assert.ok(audit.length >= 1);
    assert.deepEqual(Object.keys(audit[0]).sort(), ['action', 'actorId', 'actorRole', 'at', 'detail', 'subjectId']);
    await ctx.pool.query(`INSERT INTO ai_usage (account_id, day, messages, emergencies) VALUES
      ('HM-000001', '2026-10-02', 7, 1), ('HM-000002', '2026-10-02', 3, 0), ('HM-000001', '2026-10-03', 2, 0), ('HM-000001', '2026-08-01', 9, 9)`);
    const u = (await call(ctx.app, 'GET', '/admin/ai-usage?days=30', admin)).json();
    assert.deepEqual(u, [
      { day: '2026-10-02', messages: 10, users: 2, emergencies: null, suppressed: true },
      { day: '2026-10-03', messages: 2, users: 1, emergencies: 0, suppressed: false },
    ]);
  });
});
