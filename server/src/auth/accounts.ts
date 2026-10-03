import type { Queryable } from '../db/pool.js';
import type { Role } from '../core/types.js';
import { DAY_MS } from '../core/time.js';
import { hashToken, newToken } from './tokens.js';

export interface AccountRow {
  sehati_id: string;
  role: Role;
  full_name: string;
  rw: string;
  village: string;
  salt: string | null;
  hash: string | null;
  iterations: number | null;
  active: boolean;
  consent_server_sync: boolean;
  registered_by: string | null;
  failed_attempts: number;
  locked_until: number;
  last_seen_at: number | null;
  created_at: number;
  updated_at: number;
}

export interface UserDto {
  sehatiId: string;
  fullName: string;
  role: Role;
  rw: string;
  village: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: number;
  user: UserDto;
}

/** Masa berlaku sama dengan aplikasi: WARGA 30 hari, staf 12 jam. */
export function sessionTtl(role: Role): number {
  return role === 'WARGA' ? 30 * DAY_MS : 12 * 3_600_000;
}

export function toUserDto(a: AccountRow): UserDto {
  return { sehatiId: a.sehati_id, fullName: a.full_name, role: a.role, rw: a.rw, village: a.village };
}

export async function getAccount(db: Queryable, id: string, forUpdate = false): Promise<AccountRow | null> {
  const r = await db.query<AccountRow>(`SELECT * FROM accounts WHERE sehati_id = $1${forUpdate ? ' FOR UPDATE' : ''}`, [id]);
  return r.rows[0] ?? null;
}

export async function createSession(db: Queryable, a: AccountRow, deviceId: string | null, now: number): Promise<LoginResponse> {
  const token = newToken();
  const expiresAt = now + sessionTtl(a.role);
  await db.query(
    'INSERT INTO sessions (token_hash, account_id, device_id, created_at, expires_at) VALUES ($1, $2, $3, $4, $5)',
    [hashToken(token), a.sehati_id, deviceId, now, expiresAt],
  );
  // Bersihkan sesi kedaluwarsa milik akun ini secara oportunistik.
  await db.query('DELETE FROM sessions WHERE account_id = $1 AND expires_at < $2', [a.sehati_id, now]);
  return { token, expiresAt, user: toUserDto(a) };
}

export async function revokeSessions(db: Queryable, accountId: string): Promise<void> {
  await db.query('DELETE FROM sessions WHERE account_id = $1', [accountId]);
}
