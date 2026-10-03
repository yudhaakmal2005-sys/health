import type { FollowUpStatus, FollowUpType, RiskLevel } from '../api/types'

export type Tone = 'green' | 'yellow' | 'orange' | 'red' | 'primary' | 'neutral'

/** Label sesuai Enums.kt di aplikasi. Kategori pemantauan, bukan diagnosis. */
export const RISK_LEVELS: { key: RiskLevel; label: string; short: string; tone: Tone }[] = [
  { key: 'HEALTHY_HABIT', label: 'Kebiasaan Sehat', short: 'Sehat', tone: 'green' },
  { key: 'RISK_AWARENESS', label: 'Waspada Faktor Risiko', short: 'Waspada', tone: 'yellow' },
  { key: 'HIGHER_MONITORING', label: 'Perlu Pemantauan Lebih', short: 'Pantau', tone: 'orange' },
  { key: 'MEDICAL_FOLLOW_UP', label: 'Perlu Tindak Lanjut Medis', short: 'Tindak lanjut', tone: 'red' },
]

export const FOLLOWUP_STATUS: Record<FollowUpStatus, { label: string; tone: Tone }> = {
  OPEN: { label: 'Perlu tindak lanjut', tone: 'orange' },
  SCHEDULED: { label: 'Terjadwal', tone: 'primary' },
  IN_PROGRESS: { label: 'Sedang berjalan', tone: 'yellow' },
  DONE: { label: 'Selesai', tone: 'green' },
  CANCELLED: { label: 'Dibatalkan', tone: 'neutral' },
}

export const FOLLOWUP_TYPE: Record<FollowUpType, string> = {
  REPEAT_MEASUREMENT: 'Ukur ulang',
  EDUCATION: 'Edukasi kesehatan',
  HOME_VISIT: 'Kunjungan rumah',
  PUSKESMAS_EVALUATION: 'Evaluasi Puskesmas',
}

export function statusInfo(s: string) {
  return FOLLOWUP_STATUS[s as FollowUpStatus] ?? { label: s, tone: 'neutral' as Tone }
}

export function typeLabel(t: string) {
  return FOLLOWUP_TYPE[t as FollowUpType] ?? t
}

export function priorityInfo(p: number): { label: string; tone: Tone } {
  if (p >= 3) return { label: 'Tinggi', tone: 'red' }
  if (p === 2) return { label: 'Sedang', tone: 'orange' }
  return { label: 'Rendah', tone: 'neutral' }
}

export const ROLE_LABEL: Record<string, string> = {
  ADMIN: 'Admin Puskesmas',
  KADER: 'Kader',
  WARGA: 'Warga',
  SYSTEM: 'Sistem',
}

export const AUDIT_ACTION: Record<string, string> = {
  LOGIN: 'Masuk',
  LOGIN_SUCCESS: 'Masuk',
  LOGIN_FAILED: 'Gagal masuk',
  LOGOUT: 'Keluar',
  ACCOUNT_LOCKED: 'Akun dikunci sementara',
  CADRE_CREATE: 'Kader ditambahkan',
  CADRE_CREATED: 'Kader ditambahkan',
  CADRE_UPDATE: 'Akun kader diubah',
  CADRE_ACTIVATE: 'Kader diaktifkan',
  CADRE_DEACTIVATE: 'Kader dinonaktifkan',
  FOLLOWUP_ASSIGN: 'Penugasan tindak lanjut',
  FOLLOWUP_SCHEDULE: 'Penjadwalan tindak lanjut',
  THRESHOLDS_UPDATE: 'Ambang klinis diubah',
  THRESHOLDS_RESET: 'Ambang dikembalikan ke bawaan',
  POSYANDU_CREATE: 'Jadwal Posyandu dibuat',
  POSYANDU_UPDATE: 'Jadwal Posyandu diubah',
  POSYANDU_DELETE: 'Jadwal Posyandu dihapus',
  USER_DELETE: 'Data warga dihapus (hak penghapusan)',
  DATA_DELETE: 'Data dihapus',
  REGISTER: 'Pendaftaran warga',
}

export function auditLabel(a: string) {
  return AUDIT_ACTION[a] ?? a.replace(/_/g, ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase())
}

export function auditTone(a: string): Tone {
  if (/FAIL|LOCK|DELETE/.test(a)) return 'red'
  if (/THRESHOLD/.test(a)) return 'orange'
  if (/LOGIN|LOGOUT/.test(a)) return 'neutral'
  return 'primary'
}
