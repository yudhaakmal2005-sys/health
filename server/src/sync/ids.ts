import type { PoolClient, Queryable } from '../db/pool.js';
import { formatId, idNumber } from '../core/ids.js';
import { DAY_MS } from '../core/time.js';

export const RESERVATION_TTL_MS = 30 * DAY_MS;

/** Pastikan penghitung prefix melewati nomor yang sudah terlihat (mis. ID dari penghitung lokal perangkat). */
export async function bumpCounterPast(db: Queryable, id: string): Promise<void> {
  const n = idNumber(id);
  if (n === null) return;
  const prefix = id.slice(0, 2);
  await db.query(
    `INSERT INTO id_counters (prefix, next) VALUES ($1, $2)
     ON CONFLICT (prefix) DO UPDATE SET next = GREATEST(id_counters.next, EXCLUDED.next)`,
    [prefix, n + 1],
  );
}

/** Apakah ID sudah dipakai oleh akun atau entitas user (termasuk yang sudah dihapus — nomor tidak dipakai ulang). */
export async function isIdUsed(db: Queryable, id: string): Promise<boolean> {
  const r = await db.query(
    `SELECT 1 FROM accounts WHERE sehati_id = $1
     UNION ALL SELECT 1 FROM entities WHERE type = 'user' AND entity_id = $1
     LIMIT 1`,
    [id],
  );
  return (r.rowCount ?? 0) > 0;
}

/**
 * Ambil `count` nomor baru dari penghitung (dalam transaksi), melewati nomor yang ternyata sudah terpakai,
 * lalu catat reservasinya untuk deviceId tersebut.
 */
export async function reserveIds(
  c: PoolClient,
  opts: { prefix: 'HM' | 'KD'; count: number; deviceId: string; reservedBy: string | null; now: number },
): Promise<{ ids: string[]; expiresAt: number }> {
  const ids: string[] = [];
  const expiresAt = opts.now + RESERVATION_TTL_MS;
  await c.query(`INSERT INTO id_counters (prefix, next) VALUES ($1, 1) ON CONFLICT (prefix) DO NOTHING`, [opts.prefix]);
  let guard = 0;
  while (ids.length < opts.count && guard++ < 1000) {
    const need = opts.count - ids.length;
    const r = await c.query<{ next: number }>(
      'UPDATE id_counters SET next = next + $2 WHERE prefix = $1 RETURNING next',
      [opts.prefix, need],
    );
    const end = r.rows[0]!.next;
    for (let n = end - need; n < end; n++) {
      if (n > 999_999) throw new Error('Nomor SEHATI ID habis untuk prefix ' + opts.prefix);
      const id = formatId(opts.prefix, n);
      const taken = await c.query('SELECT 1 FROM id_reservations WHERE sehati_id = $1', [id]);
      if ((taken.rowCount ?? 0) > 0 || (await isIdUsed(c, id))) continue;
      await c.query(
        `INSERT INTO id_reservations (sehati_id, prefix, device_id, reserved_by, created_at, expires_at)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [id, opts.prefix, opts.deviceId, opts.reservedBy, opts.now, expiresAt],
      );
      ids.push(id);
    }
  }
  return { ids, expiresAt };
}

export interface ReservationRow {
  device_id: string;
  expires_at: number;
  used_at: number | null;
}

export async function getReservation(db: Queryable, id: string): Promise<ReservationRow | null> {
  const r = await db.query<ReservationRow>('SELECT device_id, expires_at, used_at FROM id_reservations WHERE sehati_id = $1', [id]);
  return r.rows[0] ?? null;
}

export async function markReservationUsed(db: Queryable, id: string, now: number): Promise<void> {
  await db.query('UPDATE id_reservations SET used_at = COALESCE(used_at, $2) WHERE sehati_id = $1', [id, now]);
}
