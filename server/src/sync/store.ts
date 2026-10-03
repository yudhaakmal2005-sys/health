import type { Queryable } from '../db/pool.js';

export type JsonObject = Record<string, unknown>;

export interface EntityRow {
  type: string;
  entity_id: string;
  server_id: string;
  subject_id: string | null;
  subject_rw: string | null;
  version: number;
  payload: JsonObject | null;
  deleted: boolean;
  seq: number;
  updated_at: number;
}

export async function getEntity(db: Queryable, type: string, entityId: string, forUpdate = false): Promise<EntityRow | null> {
  const r = await db.query<EntityRow>(
    `SELECT type, entity_id, server_id, subject_id, subject_rw, version, payload, deleted, seq, updated_at
       FROM entities WHERE type = $1 AND entity_id = $2${forUpdate ? ' FOR UPDATE' : ''}`,
    [type, entityId],
  );
  return r.rows[0] ?? null;
}

export interface EntityWrite {
  type: string;
  entityId: string;
  subjectId: string | null;
  subjectRw: string | null;
  version: number;
  payload: JsonObject | null;
  deleted: boolean;
  actorId: string;
  deviceId: string | null;
  now: number;
}

/**
 * Sisipkan/perbarui entitas dan beri seq baru. Pemanggil wajib memegang lockEntityWrites()
 * dalam transaksi yang sama agar seq berurutan sesuai commit.
 */
export async function writeEntity(db: Queryable, w: EntityWrite): Promise<string> {
  const r = await db.query<{ server_id: string }>(
    `INSERT INTO entities (type, entity_id, subject_id, subject_rw, version, payload, deleted, seq, updated_by, device_id, updated_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, nextval('entity_seq'), $8, $9, $10)
     ON CONFLICT (type, entity_id) DO UPDATE SET
       subject_id = EXCLUDED.subject_id, subject_rw = EXCLUDED.subject_rw, version = EXCLUDED.version,
       payload = EXCLUDED.payload, deleted = EXCLUDED.deleted, seq = EXCLUDED.seq,
       updated_by = EXCLUDED.updated_by, device_id = EXCLUDED.device_id, updated_at = EXCLUDED.updated_at
     RETURNING server_id`,
    [w.type, w.entityId, w.subjectId, w.subjectRw, w.version, w.payload === null ? null : JSON.stringify(w.payload),
      w.deleted, w.actorId, w.deviceId, w.now],
  );
  return r.rows[0]!.server_id;
}

/** Pindahkan entitas subjek ke RW baru (dan beri seq baru agar kader RW baru menerimanya). */
export async function moveSubjectRw(db: Queryable, subjectId: string, rw: string, now: number): Promise<void> {
  await db.query(
    `UPDATE entities SET subject_rw = $2, seq = nextval('entity_seq'), updated_at = $3
      WHERE subject_id = $1 AND subject_rw IS DISTINCT FROM $2`,
    [subjectId, rw, now],
  );
}
