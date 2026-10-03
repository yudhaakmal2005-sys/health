/**
 * Tipe sesuai docs/API.md (Kontrak API SEHATI v2). Waktu = epoch milidetik; tanggal = 'YYYY-MM-DD'.
 * Hitungan agregat dapat bernilai `null` bila disembunyikan (sel kecil 1–4, MIN_CELL = 5).
 */

export type Role = 'WARGA' | 'KADER' | 'ADMIN'

/** Hitungan agregat; `null` = disembunyikan (1–4). */
export type Count = number | null

export interface ApiErrorBody {
  error: { code: string; message: string }
}

export interface User {
  sehatiId: string
  fullName: string
  role: Role
  rw: string | null
  village: string | null
}

export interface LoginRequest {
  sehatiId: string
  password: string
  deviceId: string
}

export interface LoginResponse {
  token: string
  expiresAt: number
  user: User
}

export interface Health {
  status: string
  version: string
  time: number
  ai: boolean
}

export interface PosyanduSchedule {
  id: string
  rw: string
  date: string
  startTime: string
  endTime: string
  location: string
  notes: string | null
}

export type PosyanduInput = Omit<PosyanduSchedule, 'id'>

export interface ClinicalThresholds {
  bpLowSys: number
  bpLowDia: number
  bpNormalSys: number
  bpNormalDia: number
  bpHighSys: number
  bpHighDia: number
  bpStage2Sys: number
  bpStage2Dia: number
  bpUrgentSys: number
  bpUrgentDia: number
  glucoseLow: number
  gdsElevated: number
  gdsHigh: number
  gdpElevated: number
  gdpHigh: number
  cholBorderline: number
  cholHigh: number
  bmiUnder: number
  bmiOver: number
  bmiObese1: number
  bmiObese2: number
  waistMale: number
  waistFemale: number
  activeMinutesPerWeekGoal: number
  sedentaryHoursHigh: number
}

export interface AppConfig {
  thresholds: ClinicalThresholds | null
  thresholdsVersion: number
  emergencyNumbers: string
  posyandu: PosyanduSchedule[]
}

export type RiskLevel = 'HEALTHY_HABIT' | 'RISK_AWARENESS' | 'HIGHER_MONITORING' | 'MEDICAL_FOLLOW_UP'

export interface Overview {
  registered: Count
  screened30d: Count
  followUpOpen: Count
  followUpOverdue: Count
  levels: Partial<Record<RiskLevel, Count>>
  bp: { normal: Count; elevated: Count; high: Count }
  smokers: Count
  byMonth: { month: string; screened: Count }[]
  updatedAt: number
}

export interface RwStat {
  rw: string
  registered: Count
  screened: Count
  followUpOpen: Count
  elevatedBpPct: number | null
  suppressed: boolean
}

export type FollowUpStatus = 'OPEN' | 'SCHEDULED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
export type FollowUpType = 'REPEAT_MEASUREMENT' | 'EDUCATION' | 'HOME_VISIT' | 'PUSKESMAS_EVALUATION'
export type FollowUpFilter = 'OPEN' | 'SCHEDULED' | 'DONE' | 'ALL'

export interface FollowUp {
  id: string
  sehatiId: string
  rw: string
  type: FollowUpType | string
  reason: string
  /** 1 = rendah … 3 = tertinggi (FollowUpRules.kt) */
  priority: number
  status: FollowUpStatus | string
  dueAt: number | null
  assignedCadreId: string | null
  assignedCadreName: string | null
  createdAt: number
}

export interface Cadre {
  sehatiId: string
  fullName: string
  rw: string
  active: boolean
  lastSeenAt: number | null
}

export interface CadreCreate {
  fullName: string
  rw: string
  password: string
}

export interface ThresholdsResponse {
  thresholds: ClinicalThresholds | null
  version: number
}

export interface AuditEntry {
  at: number
  actorId: string
  actorRole: Role | string
  action: string
  subjectId: string | null
  detail: string | null
}

export interface AiUsageDay {
  day: string
  messages: Count
  users: Count
  emergencies: Count
}
