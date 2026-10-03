import type { FastifyReply, FastifyRequest } from 'fastify';
import type { Actor, Deps, Role } from '../core/types.js';
import { Errors } from '../core/errors.js';
import { hashToken } from './tokens.js';

const LAST_SEEN_THROTTLE_MS = 5 * 60_000;

function bearer(req: FastifyRequest): string | null {
  const h = req.headers.authorization;
  if (!h) return null;
  const m = /^Bearer\s+([A-Za-z0-9_-]{20,200})$/.exec(h.trim());
  return m ? m[1]! : null;
}

/** Cari aktor dari token Bearer; null bila tidak ada/tidak valid. */
export async function resolveActor(deps: Deps, req: FastifyRequest): Promise<Actor | null> {
  const token = bearer(req);
  if (!token) return null;
  const tokenHash = hashToken(token);
  const now = deps.now();
  const r = await deps.pool.query<{
    sehati_id: string; role: Role; full_name: string; rw: string; village: string; last_seen_at: number | null;
  }>(
    `SELECT a.sehati_id, a.role, a.full_name, a.rw, a.village, a.last_seen_at
       FROM sessions s JOIN accounts a ON a.sehati_id = s.account_id
      WHERE s.token_hash = $1 AND s.expires_at > $2 AND a.active`,
    [tokenHash, now],
  );
  const row = r.rows[0];
  if (!row) return null;
  if (row.last_seen_at === null || now - row.last_seen_at > LAST_SEEN_THROTTLE_MS) {
    await deps.pool.query('UPDATE accounts SET last_seen_at = $2 WHERE sehati_id = $1', [row.sehati_id, now]);
  }
  return { id: row.sehati_id, role: row.role, fullName: row.full_name, rw: row.rw, village: row.village, sessionHash: tokenHash };
}

/** preHandler: wajib masuk; opsional batasi role. */
export function requireAuth(deps: Deps, roles?: readonly Role[]) {
  return async (req: FastifyRequest, _reply: FastifyReply): Promise<void> => {
    const actor = await resolveActor(deps, req);
    if (!actor) throw Errors.unauthorized();
    if (roles && !roles.includes(actor.role)) throw Errors.forbidden();
    req.actor = actor;
  };
}

/** preHandler: auth opsional (mis. reservasi ID tanpa masuk). Token yang dikirim tapi tidak valid tetap 401. */
export function optionalAuth(deps: Deps) {
  return async (req: FastifyRequest): Promise<void> => {
    if (!req.headers.authorization) return;
    const actor = await resolveActor(deps, req);
    if (!actor) throw Errors.unauthorized();
    req.actor = actor;
  };
}

export function actorOf(req: FastifyRequest): Actor {
  if (!req.actor) throw Errors.unauthorized();
  return req.actor;
}
