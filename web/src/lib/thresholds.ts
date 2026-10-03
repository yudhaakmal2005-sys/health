import type { ClinicalThresholds } from '../api/types'

/** Nilai bawaan = `ClinicalThresholds()` di app/.../domain/rules/ClinicalRules.kt */
export const DEFAULT_THRESHOLDS: ClinicalThresholds = {
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
}

export type ThresholdKey = keyof ClinicalThresholds

export interface FieldMeta {
  key: ThresholdKey
  label: string
  unit: string
  /** Int di Kotlin → bilangan bulat */
  int: boolean
  hint?: string
}

export interface FieldGroup {
  title: string
  description: string
  fields: FieldMeta[]
  /** tata letak berpasangan sistolik/diastolik */
  paired?: boolean
}

export const THRESHOLD_GROUPS: FieldGroup[] = [
  {
    title: 'Tekanan darah',
    description: 'Batas bawah tiap kategori satu kali pengukuran. Hasil satu kali ukur adalah data skrining, bukan diagnosis hipertensi.',
    paired: true,
    fields: [
      { key: 'bpLowSys', label: 'Rendah — sistolik di bawah', unit: 'mmHg', int: true },
      { key: 'bpLowDia', label: 'Rendah — diastolik di bawah', unit: 'mmHg', int: true },
      { key: 'bpNormalSys', label: 'Normal-tinggi — sistolik mulai', unit: 'mmHg', int: true },
      { key: 'bpNormalDia', label: 'Normal-tinggi — diastolik mulai', unit: 'mmHg', int: true },
      { key: 'bpHighSys', label: 'Tinggi — sistolik mulai', unit: 'mmHg', int: true, hint: 'Rentang wajar 120–200' },
      { key: 'bpHighDia', label: 'Tinggi — diastolik mulai', unit: 'mmHg', int: true, hint: 'Rentang wajar 70–130' },
      { key: 'bpStage2Sys', label: 'Tingkat lanjut — sistolik mulai', unit: 'mmHg', int: true },
      { key: 'bpStage2Dia', label: 'Tingkat lanjut — diastolik mulai', unit: 'mmHg', int: true },
      { key: 'bpUrgentSys', label: 'Sangat tinggi — sistolik mulai', unit: 'mmHg', int: true },
      { key: 'bpUrgentDia', label: 'Sangat tinggi — diastolik mulai', unit: 'mmHg', int: true },
    ],
  },
  {
    title: 'Gula darah',
    description: 'Ambang skrining gula darah sewaktu (GDS) dan puasa (GDP). Diagnosis memerlukan pemeriksaan laboratorium.',
    fields: [
      { key: 'glucoseLow', label: 'Rendah — di bawah', unit: 'mg/dL', int: false },
      { key: 'gdsElevated', label: 'GDS di atas normal — mulai', unit: 'mg/dL', int: false },
      { key: 'gdsHigh', label: 'GDS tinggi — mulai', unit: 'mg/dL', int: false },
      { key: 'gdpElevated', label: 'GDP di atas normal — mulai', unit: 'mg/dL', int: false },
      { key: 'gdpHigh', label: 'GDP tinggi — mulai', unit: 'mg/dL', int: false },
    ],
  },
  {
    title: 'Kolesterol total',
    description: 'Kolesterol total saja tidak cukup untuk menilai risiko jantung.',
    fields: [
      { key: 'cholBorderline', label: 'Batas tinggi — mulai', unit: 'mg/dL', int: false },
      { key: 'cholHigh', label: 'Tinggi — mulai', unit: 'mg/dL', int: false },
    ],
  },
  {
    title: 'Indeks massa tubuh (IMT)',
    description: 'Klasifikasi Asia-Pasifik. IMT tidak membedakan massa otot dan lemak.',
    fields: [
      { key: 'bmiUnder', label: 'Berat badan kurang — di bawah', unit: 'kg/m²', int: false },
      { key: 'bmiOver', label: 'Berat badan lebih — mulai', unit: 'kg/m²', int: false },
      { key: 'bmiObese1', label: 'Obesitas I — mulai', unit: 'kg/m²', int: false },
      { key: 'bmiObese2', label: 'Obesitas II — mulai', unit: 'kg/m²', int: false },
    ],
  },
  {
    title: 'Lingkar perut',
    description: 'Batas obesitas sentral menurut jenis kelamin.',
    fields: [
      { key: 'waistMale', label: 'Laki-laki — mulai', unit: 'cm', int: false, hint: 'Rentang wajar 70–130' },
      { key: 'waistFemale', label: 'Perempuan — mulai', unit: 'cm', int: false, hint: 'Rentang wajar 60–120' },
    ],
  },
  {
    title: 'Aktivitas',
    description: 'Target edukasi aktivitas fisik dan batas duduk lama.',
    fields: [
      { key: 'activeMinutesPerWeekGoal', label: 'Target aktif per minggu', unit: 'menit', int: true, hint: 'Rentang wajar 30–600' },
      { key: 'sedentaryHoursHigh', label: 'Duduk lama — mulai', unit: 'jam/hari', int: true },
    ],
  },
]

export const ALL_FIELDS: FieldMeta[] = THRESHOLD_GROUPS.flatMap((g) => g.fields)

export interface ValidationIssue {
  message: string
  fields: ThresholdKey[]
}

const inRange = (v: number, lo: number, hi: number) => v >= lo && v <= hi

/**
 * Cermin `ClinicalThresholds.validate()` di Kotlin: urutan dan pesan sama.
 * Mengembalikan galat pertama (sama seperti `when` di Kotlin) atau null.
 */
export function validateThresholds(t: ClinicalThresholds): ValidationIssue | null {
  if (!Number.isInteger(t.bpHighSys) || !inRange(t.bpHighSys, 120, 200) || !Number.isInteger(t.bpHighDia) || !inRange(t.bpHighDia, 70, 130))
    return { message: 'Ambang tekanan darah tinggi di luar rentang wajar.', fields: ['bpHighSys', 'bpHighDia'] }
  if (!(t.bpNormalSys < t.bpHighSys && t.bpHighSys < t.bpStage2Sys && t.bpStage2Sys < t.bpUrgentSys))
    return { message: 'Ambang sistolik harus berurutan: normal < tinggi < tingkat lanjut < sangat tinggi.', fields: ['bpNormalSys', 'bpHighSys', 'bpStage2Sys', 'bpUrgentSys'] }
  if (!(t.bpNormalDia < t.bpHighDia && t.bpHighDia < t.bpStage2Dia && t.bpStage2Dia < t.bpUrgentDia))
    return { message: 'Ambang diastolik harus berurutan: normal < tinggi < tingkat lanjut < sangat tinggi.', fields: ['bpNormalDia', 'bpHighDia', 'bpStage2Dia', 'bpUrgentDia'] }
  if (!(t.glucoseLow < t.gdsElevated && t.gdsElevated < t.gdsHigh))
    return { message: 'Ambang gula darah sewaktu harus berurutan: rendah < di atas normal < tinggi.', fields: ['glucoseLow', 'gdsElevated', 'gdsHigh'] }
  if (!(t.gdpElevated < t.gdpHigh))
    return { message: 'Ambang gula darah puasa harus berurutan.', fields: ['gdpElevated', 'gdpHigh'] }
  if (!(t.cholBorderline < t.cholHigh))
    return { message: 'Ambang kolesterol harus berurutan: batas < tinggi.', fields: ['cholBorderline', 'cholHigh'] }
  if (!(t.bmiUnder < t.bmiOver && t.bmiOver < t.bmiObese1 && t.bmiObese1 < t.bmiObese2))
    return { message: 'Ambang IMT harus berurutan.', fields: ['bmiUnder', 'bmiOver', 'bmiObese1', 'bmiObese2'] }
  if (!inRange(t.waistMale, 70, 130) || !inRange(t.waistFemale, 60, 120))
    return { message: 'Ambang lingkar perut di luar rentang wajar.', fields: ['waistMale', 'waistFemale'] }
  if (!Number.isInteger(t.activeMinutesPerWeekGoal) || !inRange(t.activeMinutesPerWeekGoal, 30, 600))
    return { message: 'Target aktivitas mingguan di luar rentang wajar.', fields: ['activeMinutesPerWeekGoal'] }
  return null
}

export type DraftValues = Record<ThresholdKey, string>

export function toDraft(t: ClinicalThresholds): DraftValues {
  const out = {} as DraftValues
  for (const f of ALL_FIELDS) out[f.key] = String(t[f.key]).replace('.', ',')
  return out
}

export interface ParseResult {
  value: ClinicalThresholds | null
  fieldErrors: Partial<Record<ThresholdKey, string>>
}

/** Ubah isian teks (koma atau titik desimal) menjadi angka; galat per kolom. */
export function parseDraft(d: DraftValues): ParseResult {
  const fieldErrors: Partial<Record<ThresholdKey, string>> = {}
  const out = {} as ClinicalThresholds
  for (const f of ALL_FIELDS) {
    const raw = d[f.key].trim().replace(',', '.')
    if (raw === '') {
      fieldErrors[f.key] = 'Wajib diisi.'
      continue
    }
    const n = Number(raw)
    if (!Number.isFinite(n) || n <= 0) {
      fieldErrors[f.key] = 'Masukkan angka lebih dari 0.'
      continue
    }
    if (f.int && !Number.isInteger(n)) {
      fieldErrors[f.key] = 'Gunakan bilangan bulat.'
      continue
    }
    out[f.key] = n
  }
  return { value: Object.keys(fieldErrors).length ? null : out, fieldErrors }
}

export function sameThresholds(a: ClinicalThresholds, b: ClinicalThresholds): boolean {
  return ALL_FIELDS.every((f) => a[f.key] === b[f.key])
}
