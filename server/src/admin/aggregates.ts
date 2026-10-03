import type { Queryable } from '../db/pool.js';
import { addDays, DAY_MS, localDate } from '../core/time.js';
import type { ClinicalThresholds } from './thresholds.js';

/** Sel kecil: hitungan 1–4 tidak dikirim (null + suppressed), mencegah identifikasi individu. */
export const MIN_CELL = 5;

export const RISK_LEVELS = ['HEALTHY_HABIT', 'RISK_AWARENESS', 'HIGHER_MONITORING', 'MEDICAL_FOLLOW_UP'] as const;

export class Suppressor {
  suppressed = false;
  readonly fields: string[] = [];
  cell(n: number, field?: string): number | null {
    if (n >= 1 && n < MIN_CELL) {
      this.suppressed = true;
      if (field) this.fields.push(field);
      return null;
    }
    return n;
  }
}

export interface RwStats {
  rw: string;
  registered: number;
  screened30d: number;
  followUpOpen: number;
  followUpOverdue: number;
  bpNormal: number;
  bpElevated: number;
  bpHigh: number;
  smokers: number;
  levels: Record<string, number>;
}

type BpClass = 'normal' | 'elevated' | 'high';

/** Klasifikasi seperti BloodPressureRules: high = ≥ ambang tinggi; elevated = ≥ normal tetapi < tinggi. */
export function classifyBp(sys: number, dia: number, t: ClinicalThresholds): BpClass {
  if (sys >= t.bpHighSys || dia >= t.bpHighDia) return 'high';
  if (sys >= t.bpNormalSys || dia >= t.bpNormalDia) return 'elevated';
  return 'normal';
}

const OPEN_SQL = `COALESCE(e.payload->>'status', 'OPEN') NOT IN ('DONE', 'CANCELLED')`;

/** Statistik mentah per RW untuk warga yang terdaftar (role WARGA). Data dihapus (tombstone) tidak dihitung. */
export async function rwStats(db: Queryable, t: ClinicalThresholds, now: number): Promise<RwStats[]> {
  const since = now - 30 * DAY_MS;
  const map = new Map<string, RwStats>();
  const get = (rw: string): RwStats => {
    let s = map.get(rw);
    if (!s) {
      s = { rw, registered: 0, screened30d: 0, followUpOpen: 0, followUpOverdue: 0, bpNormal: 0, bpElevated: 0, bpHigh: 0, smokers: 0,
        levels: Object.fromEntries(RISK_LEVELS.map((l) => [l, 0])) };
      map.set(rw, s);
    }
    return s;
  };

  const reg = await db.query<{ rw: string; n: number }>(
    `SELECT rw, count(*)::int AS n FROM accounts WHERE role = 'WARGA' GROUP BY rw`,
  );
  for (const r of reg.rows) get(r.rw).registered = r.n;

  const screened = await db.query<{ rw: string; n: number }>(
    `SELECT a.rw, count(DISTINCT e.subject_id)::int AS n
       FROM entities e JOIN accounts a ON a.sehati_id = e.subject_id AND a.role = 'WARGA'
      WHERE e.type = 'measurement' AND NOT e.deleted AND sehati_num(e.payload->>'measuredAt') >= $1
      GROUP BY a.rw`,
    [since],
  );
  for (const r of screened.rows) get(r.rw).screened30d = r.n;

  const fu = await db.query<{ rw: string; open: number; overdue: number }>(
    `SELECT a.rw, count(*)::int AS open,
            count(*) FILTER (WHERE sehati_num(e.payload->>'dueAt') < $1)::int AS overdue
       FROM entities e JOIN accounts a ON a.sehati_id = e.subject_id AND a.role = 'WARGA'
      WHERE e.type = 'followup' AND NOT e.deleted AND ${OPEN_SQL}
      GROUP BY a.rw`,
    [now],
  );
  for (const r of fu.rows) {
    get(r.rw).followUpOpen = r.open;
    get(r.rw).followUpOverdue = r.overdue;
  }

  // Tekanan darah terbaru per warga (detail pemeriksaan + waktu dari header pemeriksaan).
  const bp = await db.query<{ rw: string; sys: number | null; dia: number | null }>(
    `SELECT DISTINCT ON (d.subject_id) a.rw,
            sehati_num(d.payload->'bloodPressure'->>'systolic') AS sys,
            sehati_num(d.payload->'bloodPressure'->>'diastolic') AS dia
       FROM entities d
       JOIN accounts a ON a.sehati_id = d.subject_id AND a.role = 'WARGA'
       LEFT JOIN entities m ON m.type = 'measurement' AND m.entity_id = d.entity_id AND NOT m.deleted
      WHERE d.type = 'measurement_detail' AND NOT d.deleted AND jsonb_typeof(d.payload->'bloodPressure') = 'object'
      ORDER BY d.subject_id, sehati_num(m.payload->>'measuredAt') DESC NULLS LAST, d.updated_at DESC`,
  );
  for (const r of bp.rows) {
    if (r.sys === null || r.dia === null) continue;
    const c = classifyBp(r.sys, r.dia, t);
    const s = get(r.rw);
    if (c === 'high') s.bpHigh++;
    else if (c === 'elevated') s.bpElevated++;
    else s.bpNormal++;
  }

  const smokers = await db.query<{ rw: string; n: number }>(
    `SELECT rw, count(*)::int AS n FROM (
       SELECT DISTINCT ON (e.subject_id) a.rw, e.payload->>'smokingStatus' AS status
         FROM entities e JOIN accounts a ON a.sehati_id = e.subject_id AND a.role = 'WARGA'
        WHERE e.type = 'assessment' AND NOT e.deleted
        ORDER BY e.subject_id, sehati_num(e.payload->>'takenAt') DESC NULLS LAST
     ) latest WHERE status = 'CURRENT' GROUP BY rw`,
  );
  for (const r of smokers.rows) get(r.rw).smokers = r.n;

  const levels = await db.query<{ rw: string; level: string; n: number }>(
    `SELECT a.rw, e.payload->>'level' AS level, count(*)::int AS n
       FROM entities e JOIN accounts a ON a.sehati_id = e.subject_id AND a.role = 'WARGA'
      WHERE e.type = 'profile' AND NOT e.deleted
      GROUP BY a.rw, e.payload->>'level'`,
  );
  for (const r of levels.rows) {
    if ((RISK_LEVELS as readonly string[]).includes(r.level)) get(r.rw).levels[r.level] = r.n;
  }

  return [...map.values()].sort((a, b) => a.rw.localeCompare(b.rw));
}

/** Jumlah warga terskrining (distinct) per bulan, 6 bulan terakhir termasuk bulan ini. */
export async function screenedByMonth(db: Queryable, now: number, timeZone: string): Promise<{ month: string; n: number }[]> {
  const months: string[] = [];
  let d = localDate(now, timeZone).slice(0, 7) + '-01';
  for (let i = 0; i < 6; i++) {
    months.unshift(d.slice(0, 7));
    d = addDays(d, -1).slice(0, 7) + '-01';
  }
  const r = await db.query<{ month: string; n: number }>(
    `SELECT to_char(to_timestamp(sehati_num(e.payload->>'measuredAt') / 1000.0) AT TIME ZONE $1, 'YYYY-MM') AS month,
            count(DISTINCT e.subject_id)::int AS n
       FROM entities e JOIN accounts a ON a.sehati_id = e.subject_id AND a.role = 'WARGA'
      WHERE e.type = 'measurement' AND NOT e.deleted AND sehati_num(e.payload->>'measuredAt') >= $2
      GROUP BY 1`,
    [timeZone, now - 200 * DAY_MS],
  );
  const byMonth = new Map(r.rows.map((x) => [x.month, x.n]));
  return months.map((m) => ({ month: m, n: byMonth.get(m) ?? 0 }));
}

export function sumStats(rows: RwStats[]) {
  const total = { registered: 0, screened30d: 0, followUpOpen: 0, followUpOverdue: 0, bpNormal: 0, bpElevated: 0, bpHigh: 0, smokers: 0,
    levels: Object.fromEntries(RISK_LEVELS.map((l) => [l, 0])) as Record<string, number> };
  for (const r of rows) {
    total.registered += r.registered;
    total.screened30d += r.screened30d;
    total.followUpOpen += r.followUpOpen;
    total.followUpOverdue += r.followUpOverdue;
    total.bpNormal += r.bpNormal;
    total.bpElevated += r.bpElevated;
    total.bpHigh += r.bpHigh;
    total.smokers += r.smokers;
    for (const l of RISK_LEVELS) total.levels[l]! += r.levels[l] ?? 0;
  }
  return total;
}

export interface RwRow {
  rw: string;
  registered: number | null;
  screened: number | null;
  followUpOpen: number | null;
  elevatedBpPct: number | null;
  suppressed: boolean;
}

/** Baris per RW tersupresi. RW dengan 1–4 warga terdaftar disembunyikan seluruhnya. */
export function rwRow(s: RwStats): RwRow {
  if (s.registered >= 1 && s.registered < MIN_CELL) {
    return { rw: s.rw, registered: null, screened: null, followUpOpen: null, elevatedBpPct: null, suppressed: true };
  }
  const sup = new Suppressor();
  const measured = s.bpNormal + s.bpElevated + s.bpHigh;
  // Persentase hanya bila penyebut ≥ 5 dan pembilang bukan sel kecil.
  let pct: number | null = null;
  if (measured >= MIN_CELL && !(s.bpHigh >= 1 && s.bpHigh < MIN_CELL)) pct = Math.round((s.bpHigh / measured) * 100) / 100;
  else if (measured > 0) sup.suppressed = true;
  return {
    rw: s.rw,
    registered: sup.cell(s.registered),
    screened: sup.cell(s.screened30d),
    followUpOpen: sup.cell(s.followUpOpen),
    elevatedBpPct: pct,
    suppressed: sup.suppressed,
  };
}
