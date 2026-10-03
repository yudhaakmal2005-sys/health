import type { Queryable } from '../db/pool.js';
import type { Actor } from '../core/types.js';
import { expandRwSpec } from '../core/rw.js';

export interface PosyanduDto {
  id: string;
  rw: string;
  date: string;
  startTime: string;
  endTime: string;
  location: string;
  notes: string;
}

interface Row {
  id: string; rw: string; date: string; start_time: string; end_time: string; location: string; notes: string;
}

const toDto = (r: Row): PosyanduDto => ({
  id: r.id, rw: r.rw, date: r.date, startTime: r.start_time, endTime: r.end_time, location: r.location, notes: r.notes,
});

const COLS = 'id, rw, date, start_time, end_time, location, notes';

/** Jadwal mendatang (hari ini dan sesudahnya) sesuai visibilitas: WARGA RW-nya, KADER RW tugasnya, ADMIN semua. */
export async function upcomingFor(db: Queryable, actor: Actor, today: string): Promise<PosyanduDto[]> {
  const params: unknown[] = [today];
  let filter = '';
  if (actor.role !== 'ADMIN') {
    params.push(actor.role === 'KADER' ? expandRwSpec(actor.rw) : [actor.rw]);
    filter = 'AND rw = ANY($2::text[])';
  }
  const r = await db.query<Row>(
    `SELECT ${COLS} FROM posyandu_schedule WHERE date >= $1::date ${filter} ORDER BY date, start_time, rw`,
    params,
  );
  return r.rows.map(toDto);
}

export async function listAll(db: Queryable): Promise<PosyanduDto[]> {
  const r = await db.query<Row>(`SELECT ${COLS} FROM posyandu_schedule ORDER BY date, start_time, rw`);
  return r.rows.map(toDto);
}

export interface PosyanduInput {
  rw: string; date: string; startTime: string; endTime: string; location: string; notes: string;
}

export async function createSchedule(db: Queryable, input: PosyanduInput, actorId: string, now: number): Promise<PosyanduDto> {
  const r = await db.query<Row>(
    `INSERT INTO posyandu_schedule (rw, date, start_time, end_time, location, notes, created_by, created_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $8) RETURNING ${COLS}`,
    [input.rw, input.date, input.startTime, input.endTime, input.location, input.notes, actorId, now],
  );
  return toDto(r.rows[0]!);
}

export async function getSchedule(db: Queryable, id: string): Promise<PosyanduDto | null> {
  const r = await db.query<Row>(`SELECT ${COLS} FROM posyandu_schedule WHERE id = $1`, [id]);
  return r.rows[0] ? toDto(r.rows[0]) : null;
}

export async function updateSchedule(db: Queryable, id: string, v: PosyanduInput, now: number): Promise<PosyanduDto | null> {
  const r = await db.query<Row>(
    `UPDATE posyandu_schedule SET rw = $2, date = $3, start_time = $4, end_time = $5, location = $6, notes = $7, updated_at = $8
      WHERE id = $1 RETURNING ${COLS}`,
    [id, v.rw, v.date, v.startTime, v.endTime, v.location, v.notes, now],
  );
  return r.rows[0] ? toDto(r.rows[0]) : null;
}

export async function deleteSchedule(db: Queryable, id: string): Promise<boolean> {
  const r = await db.query('DELETE FROM posyandu_schedule WHERE id = $1', [id]);
  return (r.rowCount ?? 0) > 0;
}
