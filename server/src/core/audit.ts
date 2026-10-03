import type { Queryable } from '../db/pool.js';

export interface AuditEntry {
  actorId: string;
  actorRole: string;
  action: string;
  subjectId?: string | null;
  /** Ringkas, tanpa data kesehatan / kata sandi / token. */
  detail?: string;
}

export async function audit(db: Queryable, e: AuditEntry, now = Date.now()): Promise<void> {
  await db.query(
    'INSERT INTO audit_log (at, actor_id, actor_role, action, subject_id, detail) VALUES ($1, $2, $3, $4, $5, $6)',
    [now, e.actorId, e.actorRole, e.action, e.subjectId ?? null, (e.detail ?? '').slice(0, 500)],
  );
}
