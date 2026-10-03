import type { Queryable } from '../db/pool.js';

/**
 * Cermin ClinicalThresholds aplikasi (app/.../domain/rules/ClinicalRules.kt): nama field, nilai bawaan,
 * tipe (Int vs Float) dan aturan validate() harus sama persis.
 */
export interface ClinicalThresholds {
  bpLowSys: number; bpLowDia: number;
  bpNormalSys: number; bpNormalDia: number;
  bpHighSys: number; bpHighDia: number;
  bpStage2Sys: number; bpStage2Dia: number;
  bpUrgentSys: number; bpUrgentDia: number;
  glucoseLow: number;
  gdsElevated: number; gdsHigh: number;
  gdpElevated: number; gdpHigh: number;
  cholBorderline: number; cholHigh: number;
  bmiUnder: number; bmiOver: number; bmiObese1: number; bmiObese2: number;
  waistMale: number; waistFemale: number;
  activeMinutesPerWeekGoal: number;
  sedentaryHoursHigh: number;
}

export const DEFAULT_THRESHOLDS: Readonly<ClinicalThresholds> = Object.freeze({
  bpLowSys: 90, bpLowDia: 60,
  bpNormalSys: 120, bpNormalDia: 80,
  bpHighSys: 140, bpHighDia: 90,
  bpStage2Sys: 160, bpStage2Dia: 100,
  bpUrgentSys: 180, bpUrgentDia: 110,
  glucoseLow: 70,
  gdsElevated: 140, gdsHigh: 200,
  gdpElevated: 100, gdpHigh: 126,
  cholBorderline: 200, cholHigh: 240,
  bmiUnder: 18.5, bmiOver: 23, bmiObese1: 25, bmiObese2: 30,
  waistMale: 90, waistFemale: 80,
  activeMinutesPerWeekGoal: 150,
  sedentaryHoursHigh: 8,
});

/** Field Int di Kotlin; sisanya Float. */
const INT_FIELDS = new Set<keyof ClinicalThresholds>([
  'bpLowSys', 'bpLowDia', 'bpNormalSys', 'bpNormalDia', 'bpHighSys', 'bpHighDia', 'bpStage2Sys', 'bpStage2Dia',
  'bpUrgentSys', 'bpUrgentDia', 'activeMinutesPerWeekGoal', 'sedentaryHoursHigh',
]);
const FIELDS = Object.keys(DEFAULT_THRESHOLDS) as (keyof ClinicalThresholds)[];

const inRange = (v: number, lo: number, hi: number) => v >= lo && v <= hi;

/** Sama dengan ClinicalThresholds.validate() di aplikasi. null = valid. */
export function validateThresholds(t: ClinicalThresholds): string | null {
  if (!inRange(t.bpHighSys, 120, 200) || !inRange(t.bpHighDia, 70, 130)) return 'Ambang tekanan darah tinggi di luar rentang wajar.';
  if (!(t.bpNormalSys < t.bpHighSys && t.bpHighSys < t.bpStage2Sys && t.bpStage2Sys < t.bpUrgentSys)) {
    return 'Ambang sistolik harus berurutan: normal < tinggi < tingkat lanjut < sangat tinggi.';
  }
  if (!(t.bpNormalDia < t.bpHighDia && t.bpHighDia < t.bpStage2Dia && t.bpStage2Dia < t.bpUrgentDia)) {
    return 'Ambang diastolik harus berurutan: normal < tinggi < tingkat lanjut < sangat tinggi.';
  }
  if (!(t.glucoseLow < t.gdsElevated && t.gdsElevated < t.gdsHigh)) {
    return 'Ambang gula darah sewaktu harus berurutan: rendah < di atas normal < tinggi.';
  }
  if (!(t.gdpElevated < t.gdpHigh)) return 'Ambang gula darah puasa harus berurutan.';
  if (!(t.cholBorderline < t.cholHigh)) return 'Ambang kolesterol harus berurutan: batas < tinggi.';
  if (!(t.bmiUnder < t.bmiOver && t.bmiOver < t.bmiObese1 && t.bmiObese1 < t.bmiObese2)) return 'Ambang IMT harus berurutan.';
  if (!inRange(t.waistMale, 70, 130) || !inRange(t.waistFemale, 60, 120)) return 'Ambang lingkar perut di luar rentang wajar.';
  if (!inRange(t.activeMinutesPerWeekGoal, 30, 600)) return 'Target aktivitas mingguan di luar rentang wajar.';
  return null;
}

/**
 * Terima objek (boleh sebagian — field yang tidak ada memakai bawaan, seperti dekode kotlinx dengan default),
 * tolak field tak dikenal / tipe salah, lalu validasi.
 */
export function coerceThresholds(input: unknown): { value: ClinicalThresholds } | { error: string } {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) return { error: 'Ambang harus berupa objek.' };
  const obj = input as Record<string, unknown>;
  for (const k of Object.keys(obj)) {
    if (!FIELDS.includes(k as keyof ClinicalThresholds)) return { error: `Field ambang tidak dikenal: ${k.slice(0, 40)}.` };
  }
  const out = { ...DEFAULT_THRESHOLDS } as ClinicalThresholds;
  for (const k of FIELDS) {
    if (!(k in obj)) continue;
    const v = obj[k];
    if (typeof v !== 'number' || !Number.isFinite(v) || v < 0 || v > 10_000) return { error: `Nilai ${k} tidak valid.` };
    if (INT_FIELDS.has(k) && !Number.isInteger(v)) return { error: `Nilai ${k} harus bilangan bulat.` };
    out[k] = v;
  }
  const err = validateThresholds(out);
  return err ? { error: err } : { value: out };
}

export interface ThresholdSetting {
  thresholds: ClinicalThresholds | null;
  version: number;
}

export async function getThresholds(db: Queryable): Promise<ThresholdSetting> {
  const r = await db.query<{ value: ClinicalThresholds | null; version: number }>(
    `SELECT value, version FROM settings WHERE key = 'thresholds'`,
  );
  const row = r.rows[0];
  return { thresholds: row?.value ?? null, version: row?.version ?? 0 };
}

/** Ambang efektif (tersimpan atau bawaan) untuk agregat dashboard. */
export async function effectiveThresholds(db: Queryable): Promise<ClinicalThresholds> {
  return (await getThresholds(db)).thresholds ?? { ...DEFAULT_THRESHOLDS };
}

export async function setThresholds(db: Queryable, value: ClinicalThresholds | null, actorId: string, now: number): Promise<number> {
  const r = await db.query<{ version: number }>(
    `INSERT INTO settings (key, value, version, updated_at, updated_by) VALUES ('thresholds', $1, 1, $2, $3)
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, version = settings.version + 1,
       updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by
     RETURNING version`,
    [value === null ? null : JSON.stringify(value), now, actorId],
  );
  return r.rows[0]!.version;
}
