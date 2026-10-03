import { after, afterEach, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { pbkdf2Sync } from 'node:crypto';
import {
  ADMIN_ID, ADMIN_PW, PW, call, closePool, createKader, adminToken, item, login, push, registerWarga, setup, userPayload, type TestCtx,
} from './helpers.js';
import { hashPassword, verifyPassword } from '../src/auth/password.js';

let ctx: TestCtx;
beforeEach(async () => { ctx = await setup(); });
afterEach(async () => { await ctx.close(); });
after(closePool);

describe('kompatibilitas hash kata sandi', () => {
  test('format sama dengan PasswordHasher aplikasi (PBKDF2-HMAC-SHA256, 256 bit, base64)', async () => {
    const salt = Buffer.alloc(16, 7);
    const expected = pbkdf2Sync('demo1234', salt, 120_000, 32, 'sha256').toString('base64');
    assert.equal(await verifyPassword('demo1234', { salt: salt.toString('base64'), hash: expected, iterations: 120_000 }), true);
    assert.equal(await verifyPassword('salah', { salt: salt.toString('base64'), hash: expected, iterations: 120_000 }), false);
    const h = await hashPassword('x123456');
    assert.equal(Buffer.from(h.salt, 'base64').length, 16);
    assert.equal(Buffer.from(h.hash, 'base64').length, 32);
    assert.equal(h.iterations, 120_000);
  });
});

describe('login', () => {
  test('berhasil: token opaque, expiresAt per role, objek user', async () => {
    const r = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: 'ad-000001', password: ADMIN_PW, deviceId: 'd1' });
    assert.equal(r.statusCode, 200);
    const b = r.json();
    assert.match(b.token, /^[A-Za-z0-9_-]{43}$/);
    assert.equal(b.expiresAt, ctx.clock.t + 12 * 3_600_000);
    assert.deepEqual(b.user, { sehatiId: ADMIN_ID, fullName: 'Admin Puskesmas', role: 'ADMIN', rw: '', village: '' });
    const me = await call(ctx.app, 'GET', '/auth/me', b.token);
    assert.equal(me.json().sehatiId, ADMIN_ID);
    // token hanya disimpan sebagai hash
    const s = await ctx.pool.query('SELECT token_hash FROM sessions');
    assert.notEqual(s.rows[0].token_hash, b.token);
    assert.equal(s.rows[0].token_hash.length, 64);
  });

  test('warga: sesi 30 hari', async () => {
    const r = await call(ctx.app, 'POST', '/auth/register', null, {
      sehatiId: 'HM-000777', fullName: 'Tariska', birthDate: '1990-01-01', sex: 'FEMALE', village: 'Desa Mirigambar', rw: '2', rt: '01',
      phone: null, password: PW, consentServerSync: true, deviceId: 'dev-a',
    });
    assert.equal(r.statusCode, 201);
    assert.equal(r.json().expiresAt, ctx.clock.t + 30 * 86_400_000);
    assert.equal(r.json().user.rw, '02');
  });

  test('gagal: 401 INVALID_CREDENTIALS generik (akun ada maupun tidak)', async () => {
    const a = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: ADMIN_ID, password: 'salah', deviceId: 'd' });
    const b = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: 'HM-999999', password: 'salah', deviceId: 'd' });
    assert.equal(a.statusCode, 401);
    assert.equal(b.statusCode, 401);
    assert.deepEqual(a.json(), b.json());
    assert.equal(a.json().error.code, 'INVALID_CREDENTIALS');
  });

  test('kunci 15 menit setelah 5 gagal berturut-turut', async () => {
    for (let i = 0; i < 4; i++) {
      const r = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: ADMIN_ID, password: 'salah', deviceId: 'd' });
      assert.equal(r.statusCode, 401);
    }
    const fifth = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: ADMIN_ID, password: 'salah', deviceId: 'd' });
    assert.equal(fifth.statusCode, 429);
    assert.equal(fifth.json().error.code, 'ACCOUNT_LOCKED');
    const correct = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: ADMIN_ID, password: ADMIN_PW, deviceId: 'd' });
    assert.equal(correct.statusCode, 429);
    ctx.clock.advance(15 * 60_000 + 1);
    const later = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: ADMIN_ID, password: ADMIN_PW, deviceId: 'd' });
    assert.equal(later.statusCode, 200);
    const audit = await ctx.pool.query(`SELECT action FROM audit_log WHERE action LIKE 'login%' ORDER BY id`);
    assert.deepEqual(audit.rows.map((r) => r.action), ['login_failed', 'login_failed', 'login_failed', 'login_failed', 'login_locked', 'login']);
  });

  test('logout mencabut token; token tak valid → 401', async () => {
    const t = await adminToken(ctx.app);
    assert.equal((await call(ctx.app, 'POST', '/auth/logout', t)).statusCode, 204);
    const me = await call(ctx.app, 'GET', '/auth/me', t);
    assert.equal(me.statusCode, 401);
    assert.equal(me.json().error.code, 'UNAUTHORIZED');
  });

  test('sesi kedaluwarsa → 401', async () => {
    const t = await adminToken(ctx.app);
    ctx.clock.advance(12 * 3_600_000 + 1);
    assert.equal((await call(ctx.app, 'GET', '/auth/me', t)).statusCode, 401);
  });

  test('kader nonaktif tidak dapat masuk dan sesinya dicabut', async () => {
    const admin = await adminToken(ctx.app);
    const k = await createKader(ctx.app, admin, '02');
    const p = await call(ctx.app, 'PATCH', `/admin/cadres/${k.id}`, admin, { active: false });
    assert.equal(p.statusCode, 200);
    assert.equal((await call(ctx.app, 'GET', '/auth/me', k.token)).statusCode, 401);
    const r = await call(ctx.app, 'POST', '/auth/login', null, { sehatiId: k.id, password: PW, deviceId: 'd' });
    assert.equal(r.statusCode, 403);
    assert.equal(r.json().error.code, 'ACCOUNT_INACTIVE');
  });
});

describe('register & reservasi ID', () => {
  const reg = (over: Record<string, unknown> = {}) => ({
    sehatiId: 'HM-000231', fullName: 'Warga Baru', birthDate: '1990-01-01', sex: 'FEMALE', village: 'Desa', rw: '02', rt: '01',
    phone: null, password: PW, consentServerSync: true, deviceId: 'dev-1', ...over,
  });

  test('tanpa consentServerSync → 400 CONSENT_REQUIRED', async () => {
    const r = await call(ctx.app, 'POST', '/auth/register', null, reg({ consentServerSync: false }));
    assert.equal(r.statusCode, 400);
    assert.equal(r.json().error.code, 'CONSENT_REQUIRED');
  });

  test('ID terpakai → 409 ID_TAKEN', async () => {
    assert.equal((await call(ctx.app, 'POST', '/auth/register', null, reg())).statusCode, 201);
    const r = await call(ctx.app, 'POST', '/auth/register', null, reg({ deviceId: 'dev-2' }));
    assert.equal(r.statusCode, 409);
    assert.equal(r.json().error.code, 'ID_TAKEN');
  });

  test('reservasi anonim dibatasi 1 ID; ID terikat deviceId', async () => {
    const r = await call(ctx.app, 'POST', '/ids/reserve', null, { deviceId: 'dev-A', count: 20, prefix: 'HM' });
    assert.equal(r.statusCode, 200);
    const { ids, expiresAt } = r.json();
    assert.equal(ids.length, 1);
    assert.match(ids[0], /^HM-\d{6}$/);
    assert.equal(expiresAt, ctx.clock.t + 30 * 86_400_000);
    // perangkat lain tidak boleh memakai ID itu
    const other = await call(ctx.app, 'POST', '/auth/register', null, reg({ sehatiId: ids[0], deviceId: 'dev-B' }));
    assert.equal(other.statusCode, 409);
    assert.equal(other.json().error.code, 'ID_TAKEN');
    const own = await call(ctx.app, 'POST', '/auth/register', null, reg({ sehatiId: ids[0], deviceId: 'dev-A' }));
    assert.equal(own.statusCode, 201);
    // nomor tidak dipakai ulang
    const r2 = await call(ctx.app, 'POST', '/ids/reserve', null, { deviceId: 'dev-C', count: 1, prefix: 'HM' });
    assert.notEqual(r2.json().ids[0], ids[0]);
    assert.equal((await call(ctx.app, 'POST', '/ids/reserve', null, { deviceId: 'dev-C', count: 1, prefix: 'KD' })).statusCode, 403);
  });

  test('kader: maks 50, hanya HM; admin boleh KD; ID yang sudah dipakai dilewati', async () => {
    const admin = await adminToken(ctx.app);
    const k = await createKader(ctx.app, admin, '02');
    await registerWarga(ctx.app, '02', 'HM-000002'); // dipakai di luar reservasi → penghitung melompatinya
    const r = await call(ctx.app, 'POST', '/ids/reserve', k.token, { deviceId: 'dev-K', count: 80, prefix: 'HM' });
    assert.equal(r.json().ids.length, 50);
    assert.ok(!r.json().ids.includes('HM-000002'));
    assert.equal(new Set(r.json().ids).size, 50);
    assert.equal((await call(ctx.app, 'POST', '/ids/reserve', k.token, { deviceId: 'dev-K', count: 1, prefix: 'KD' })).statusCode, 403);
    const a = await call(ctx.app, 'POST', '/ids/reserve', admin, { deviceId: 'dev-A', count: 2, prefix: 'KD' });
    assert.deepEqual(a.json().ids.map((x: string) => x.slice(0, 3)), ['KD-', 'KD-']);
  });
});

describe('aktivasi akun warga yang didaftarkan kader', () => {
  async function kaderRegistersWarga(hasConsent = true) {
    const admin = await adminToken(ctx.app);
    const k = await createKader(ctx.app, admin, '03');
    const id = 'HM-000300';
    const res = await push(ctx.app, k.token, [item({
      type: 'user', entityId: id, payload: userPayload(id, '03', { hasAccount: false, registeredBy: k.id, birthDate: '1992-03-01', consentServerSync: hasConsent }),
    })]);
    return { id, k, res };
  }

  test('berhasil: membuat kredensial, respons seperti login, user hasAccount=true (versi +1)', async () => {
    const { id, k } = await kaderRegistersWarga();
    const r = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: id, birthDate: '1992-03-01', password: 'baru1234', deviceId: 'hp-warga' });
    assert.equal(r.statusCode, 200, r.body);
    assert.equal(r.json().user.sehatiId, id);
    assert.equal(r.json().user.role, 'WARGA');
    assert.ok(r.json().token);
    await login(ctx.app, id, 'baru1234');
    const u = await ctx.pool.query(`SELECT version, payload FROM entities WHERE type = 'user' AND entity_id = $1`, [id]);
    assert.equal(u.rows[0].version, 2);
    assert.equal(u.rows[0].payload.hasAccount, true);
    const acc = await ctx.pool.query('SELECT registered_by, iterations FROM accounts WHERE sehati_id = $1', [id]);
    assert.equal(acc.rows[0].registered_by, k.id);
    assert.equal(acc.rows[0].iterations, 120_000);
    // sudah aktif → 409
    const again = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: id, birthDate: '1992-03-01', password: 'lain1234', deviceId: 'x' });
    assert.equal(again.statusCode, 409);
    assert.equal(again.json().error.code, 'ALREADY_ACTIVE');
    const audit = await ctx.pool.query(`SELECT 1 FROM audit_log WHERE action = 'account_activate' AND subject_id = $1`, [id]);
    assert.equal(audit.rowCount, 1);
  });

  test('tidak ada / tanggal lahir salah / tanpa consent → 404 dengan pesan yang sama', async () => {
    const { id } = await kaderRegistersWarga();
    const notFound = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: 'HM-000999', birthDate: '1992-03-01', password: 'baru1234', deviceId: 'd' });
    const mismatch = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: id, birthDate: '1992-03-02', password: 'baru1234', deviceId: 'd' });
    assert.equal(notFound.statusCode, 404);
    assert.equal(mismatch.statusCode, 404);
    assert.deepEqual(notFound.json(), mismatch.json());
    assert.deepEqual(notFound.json(), { error: { code: 'NOT_FOUND', message: 'Data tidak cocok.' } });
  });

  test('warga mandiri yang sudah punya kata sandi → 409 ALREADY_ACTIVE', async () => {
    const w = await registerWarga(ctx.app, '02');
    await push(ctx.app, w.token, [item({ type: 'user', entityId: w.id, payload: userPayload(w.id, '02', { birthDate: '1980-05-01' }) })]);
    const r = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: w.id, birthDate: '1980-05-01', password: 'baru1234', deviceId: 'd' });
    assert.equal(r.statusCode, 409);
  });

  test('batas 5 percobaan per jam per sehatiId → 429', async () => {
    await ctx.close();
    ctx = await setup({ rateLimits: { activatePerIdPerHour: 5 } });
    const { id } = await kaderRegistersWarga();
    for (let i = 0; i < 5; i++) {
      const r = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: id, birthDate: '2000-01-01', password: 'baru1234', deviceId: 'd' });
      assert.equal(r.statusCode, 404);
    }
    const r = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: id, birthDate: '1992-03-01', password: 'baru1234', deviceId: 'd' });
    assert.equal(r.statusCode, 429);
    ctx.clock.advance(3_600_001);
    const ok = await call(ctx.app, 'POST', '/auth/activate', null, { sehatiId: id, birthDate: '1992-03-01', password: 'baru1234', deviceId: 'd' });
    assert.equal(ok.statusCode, 200);
  });
});

describe('batas per IP', () => {
  test('register: 429 setelah batas per IP', async () => {
    await ctx.close();
    ctx = await setup({ rateLimits: { registerPerHour: 2 } });
    for (let i = 0; i < 2; i++) await registerWarga(ctx.app, '01');
    const r = await call(ctx.app, 'POST', '/auth/register', null, {
      sehatiId: 'HM-000888', fullName: 'X Y', birthDate: '1990-01-01', sex: 'MALE', village: 'D', rw: '01', password: PW,
      consentServerSync: true, deviceId: 'd',
    });
    assert.equal(r.statusCode, 429);
    assert.equal(r.json().error.code, 'RATE_LIMITED');
  });
});
