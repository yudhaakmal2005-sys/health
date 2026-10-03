import type { FastifyBaseLogger } from 'fastify';
import type { AppConfig } from '../config.js';
import type { Pool } from '../db/pool.js';
import { hashPassword } from '../auth/password.js';
import { bumpCounterPast } from '../sync/ids.js';
import { audit } from './audit.js';

/** Buat ADMIN pertama dari env bila belum ada ADMIN sama sekali. */
export async function ensureAdmin(pool: Pool, config: AppConfig, log: Pick<FastifyBaseLogger, 'info' | 'warn'>, now = Date.now()): Promise<void> {
  const r = await pool.query(`SELECT 1 FROM accounts WHERE role = 'ADMIN' LIMIT 1`);
  if ((r.rowCount ?? 0) > 0) return;
  let password = config.adminPassword;
  if (!password) {
    if (config.env === 'production') throw new Error('ADMIN_PASSWORD wajib diisi untuk membuat admin pertama');
    password = 'admin12345';
    log.warn(`ADMIN_PASSWORD tidak diatur; admin pengembangan ${config.adminId} dibuat dengan kata sandi bawaan "admin12345".`);
  }
  const pw = await hashPassword(password);
  await pool.query(
    `INSERT INTO accounts (sehati_id, role, full_name, rw, village, salt, hash, iterations, consent_server_sync, created_at, updated_at)
     VALUES ($1, 'ADMIN', 'Admin Puskesmas', '', '', $2, $3, $4, true, $5, $5) ON CONFLICT (sehati_id) DO NOTHING`,
    [config.adminId, pw.salt, pw.hash, pw.iterations, now],
  );
  await bumpCounterPast(pool, config.adminId);
  await audit(pool, { actorId: 'system', actorRole: 'SYSTEM', action: 'admin_bootstrap', subjectId: config.adminId }, now);
  log.info(`Admin pertama ${config.adminId} dibuat.`);
}
