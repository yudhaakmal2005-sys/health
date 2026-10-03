import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { Deps } from '../core/types.js';
import { ApiError, Errors } from '../core/errors.js';
import { parse, zDate, zDeviceId, zRw } from '../core/validate.js';
import { normalizeSehatiId, WARGA_ID } from '../core/ids.js';
import { normalizeRw } from '../core/rw.js';
import { audit } from '../core/audit.js';
import { SlidingWindowLimiter } from '../core/limiter.js';
import { lockEntityWrites, withTx } from '../db/pool.js';
import { bumpCounterPast, getReservation, isIdUsed, markReservationUsed } from '../sync/ids.js';
import { getEntity, writeEntity } from '../sync/store.js';
import { burnPasswordCheck, hashPassword, passwordIssue, verifyPassword } from './password.js';
import { createSession, getAccount, toUserDto, type AccountRow } from './accounts.js';
import { actorOf, requireAuth } from './guard.js';

const MAX_FAILED = 5;
const LOCK_MS = 15 * 60_000;

const invalidCredentials = () => new ApiError(401, 'INVALID_CREDENTIALS', 'SEHATI ID atau kata sandi salah.');
const accountLocked = () =>
  new ApiError(429, 'ACCOUNT_LOCKED', 'Akun dikunci sementara karena terlalu banyak percobaan gagal. Coba lagi dalam 15 menit.');
const activationMismatch = () => new ApiError(404, 'NOT_FOUND', 'Data tidak cocok.');

const loginSchema = z.object({
  sehatiId: z.string().trim().min(1).max(20),
  password: z.string().min(1).max(200),
  deviceId: zDeviceId.optional(),
});

const registerSchema = z.object({
  sehatiId: z.string().trim().max(20),
  fullName: z.string().trim().min(2).max(100),
  birthDate: zDate,
  sex: z.enum(['MALE', 'FEMALE']),
  village: z.string().trim().min(1).max(100),
  rw: zRw,
  rt: z.string().trim().max(5).optional().default(''),
  phone: z.string().trim().max(20).nullable().optional(),
  password: z.string().max(200),
  consentServerSync: z.boolean().optional(),
  deviceId: zDeviceId,
});

const activateSchema = z.object({
  sehatiId: z.string().trim().max(20),
  birthDate: zDate,
  password: z.string().max(200),
  deviceId: zDeviceId,
});

export async function authRoutes(app: FastifyInstance, deps: Deps): Promise<void> {
  const rl = deps.config.rateLimits;
  const activateById = new SlidingWindowLimiter(rl.activatePerIdPerHour, 3_600_000);

  app.post('/auth/login', { config: { rateLimit: { max: rl.loginPerMinute, timeWindow: 60_000 } } }, async (req) => {
    const body = parse(loginSchema, req.body);
    const id = normalizeSehatiId(body.sehatiId);
    const now = deps.now();
    const acc = await getAccount(deps.pool, id);
    if (!acc || !acc.hash || !acc.salt || !acc.iterations) {
      await burnPasswordCheck(body.password);
      throw invalidCredentials();
    }
    if (acc.locked_until > now) throw accountLocked();

    const ok = await verifyPassword(body.password, { salt: acc.salt, hash: acc.hash, iterations: acc.iterations });
    if (!ok) {
      const failed = acc.failed_attempts + 1;
      const lock = failed >= MAX_FAILED;
      await deps.pool.query('UPDATE accounts SET failed_attempts = $2, locked_until = $3 WHERE sehati_id = $1', [
        id, lock ? 0 : failed, lock ? now + LOCK_MS : acc.locked_until,
      ]);
      await audit(deps.pool, { actorId: id, actorRole: acc.role, action: lock ? 'login_locked' : 'login_failed', subjectId: id }, now);
      throw lock ? accountLocked() : invalidCredentials();
    }
    if (!acc.active) {
      await audit(deps.pool, { actorId: id, actorRole: acc.role, action: 'login_inactive', subjectId: id }, now);
      throw new ApiError(403, 'ACCOUNT_INACTIVE', 'Akun ini dinonaktifkan. Hubungi admin Puskesmas.');
    }
    return withTx(deps.pool, async (c) => {
      await c.query('UPDATE accounts SET failed_attempts = 0, locked_until = 0, last_seen_at = $2 WHERE sehati_id = $1', [id, now]);
      await audit(c, { actorId: id, actorRole: acc.role, action: 'login', subjectId: id }, now);
      return createSession(c, acc, body.deviceId ?? null, now);
    });
  });

  app.post('/auth/register', { config: { rateLimit: { max: rl.registerPerHour, timeWindow: 3_600_000 } } }, async (req, reply) => {
    const body = parse(registerSchema, req.body);
    if (body.consentServerSync !== true) {
      throw new ApiError(400, 'CONSENT_REQUIRED', 'Persetujuan sinkronisasi ke server diperlukan untuk mendaftar secara online.');
    }
    const issue = passwordIssue(body.password);
    if (issue) throw Errors.validation(issue);
    const id = normalizeSehatiId(body.sehatiId);
    if (!WARGA_ID.test(id)) throw Errors.validation('Format SEHATI ID harus HM-000000.');
    const pw = await hashPassword(body.password);
    const now = deps.now();
    const rw = normalizeRw(body.rw);

    const res = await withTx(deps.pool, async (c) => {
      await c.query('SELECT pg_advisory_xact_lock(726202)'); // serialisasi klaim ID
      const reservation = await getReservation(c, id);
      const reservedElsewhere = reservation && reservation.device_id !== body.deviceId && reservation.expires_at > now;
      if (reservedElsewhere || (await isIdUsed(c, id))) {
        throw Errors.conflict('ID_TAKEN', 'SEHATI ID sudah dipakai. Minta ID baru lalu coba lagi.');
      }
      const r = await c.query<AccountRow>(
        `INSERT INTO accounts (sehati_id, role, full_name, rw, village, salt, hash, iterations, consent_server_sync, created_at, updated_at)
         VALUES ($1, 'WARGA', $2, $3, $4, $5, $6, $7, true, $8, $8) RETURNING *`,
        [id, body.fullName, rw, body.village, pw.salt, pw.hash, pw.iterations, now],
      );
      await markReservationUsed(c, id, now);
      await bumpCounterPast(c, id);
      await audit(c, { actorId: id, actorRole: 'WARGA', action: 'register', subjectId: id }, now);
      return createSession(c, r.rows[0]!, body.deviceId, now);
    });
    return reply.code(201).send(res);
  });

  app.post('/auth/activate', { config: { rateLimit: { max: rl.activatePerHour, timeWindow: 3_600_000 } } }, async (req) => {
    const body = parse(activateSchema, req.body);
    const id = normalizeSehatiId(body.sehatiId);
    const now = deps.now();
    if (!activateById.tryHit(id, now)) throw Errors.rateLimited('Terlalu banyak percobaan aktivasi untuk ID ini. Coba lagi nanti.');
    const issue = passwordIssue(body.password);
    if (issue) throw Errors.validation(issue);
    if (!WARGA_ID.test(id)) throw activationMismatch();
    const pw = await hashPassword(body.password);

    const result = await withTx(deps.pool, async (c) => {
      await lockEntityWrites(c);
      const user = await getEntity(c, 'user', id, true);
      const p = user?.payload;
      if (!user || user.deleted || !p || (p.role ?? 'WARGA') !== 'WARGA' || p.consentServerSync !== true || p.birthDate !== body.birthDate) {
        return null;
      }
      const existing = await getAccount(c, id, true);
      if (existing && existing.role !== 'WARGA') return null;
      if (existing?.hash) throw Errors.conflict('ALREADY_ACTIVE', 'Akun ini sudah aktif. Silakan masuk dengan kata sandi Anda.');

      const r = await c.query<AccountRow>(
        `INSERT INTO accounts (sehati_id, role, full_name, rw, village, salt, hash, iterations, consent_server_sync, registered_by, created_at, updated_at)
         VALUES ($1, 'WARGA', $2, $3, $4, $5, $6, $7, true, $8, $9, $9)
         ON CONFLICT (sehati_id) DO UPDATE SET salt = EXCLUDED.salt, hash = EXCLUDED.hash, iterations = EXCLUDED.iterations,
           failed_attempts = 0, locked_until = 0, updated_at = EXCLUDED.updated_at
         RETURNING *`,
        [id, String(p.fullName ?? ''), normalizeRw(String(p.rw ?? '')), String(p.village ?? ''), pw.salt, pw.hash, pw.iterations,
          typeof p.registeredBy === 'string' ? p.registeredBy : null, now],
      );
      // Tandai hasAccount=true (versi +1) agar perangkat warga & kader menerima statusnya lewat pull.
      const next = { ...p, hasAccount: true, updatedAt: now, version: user.version + 1 };
      await writeEntity(c, {
        type: 'user', entityId: id, subjectId: id, subjectRw: user.subject_rw, version: user.version + 1, payload: next,
        deleted: false, actorId: id, deviceId: body.deviceId, now,
      });
      await audit(c, { actorId: id, actorRole: 'WARGA', action: 'account_activate', subjectId: id }, now);
      return createSession(c, r.rows[0]!, body.deviceId, now);
    });
    if (!result) {
      // Pesan sama untuk "tidak ada" dan "tidak cocok" agar tidak bisa dipakai menebak ID terdaftar.
      await audit(deps.pool, { actorId: 'anonymous', actorRole: 'NONE', action: 'activate_failed', subjectId: null }, now);
      throw activationMismatch();
    }
    return result;
  });

  app.post('/auth/logout', { preHandler: requireAuth(deps) }, async (req, reply) => {
    const actor = actorOf(req);
    await deps.pool.query('DELETE FROM sessions WHERE token_hash = $1', [actor.sessionHash]);
    return reply.code(204).send();
  });

  app.get('/auth/me', { preHandler: requireAuth(deps) }, async (req) => {
    const acc = await getAccount(deps.pool, actorOf(req).id);
    if (!acc) throw Errors.unauthorized();
    return toUserDto(acc);
  });
}

