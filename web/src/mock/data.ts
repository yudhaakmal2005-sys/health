/**
 * DATA SINTETIS untuk mode demo (VITE_MOCK=1). Bukan data warga sungguhan.
 * Semua nama kader diberi akhiran "(demo)" dan ID mengikuti rentang demo aplikasi.
 */
import type {
  AiUsageDay, AuditEntry, Cadre, ClinicalThresholds, Count, FollowUp, Overview, PosyanduSchedule, RwStat,
} from '../api/types'
import { isoDate } from '../lib/format'

/** PRNG deterministik agar tangkapan layar konsisten. */
function mulberry32(seed: number) {
  let a = seed
  return () => {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const rand = mulberry32(20261003)
const int = (lo: number, hi: number) => lo + Math.floor(rand() * (hi - lo + 1))
const pick = <T,>(arr: readonly T[]): T => arr[Math.floor(rand() * arr.length)] as T

const DAY = 86400000
const now = Date.now()
const startOfToday = (() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d.getTime() })()

/** Sel kecil 1–4 → null (MIN_CELL = 5), sama dengan perilaku server. */
export const suppress = (n: number): Count => (n >= 1 && n <= 4 ? null : n)

export const VILLAGE = 'Desa Mirigambar'

export const users = {
  'AD-000001': { sehatiId: 'AD-000001', fullName: 'Admin Puskesmas (demo)', role: 'ADMIN' as const, rw: null, village: VILLAGE },
  'KD-000001': { sehatiId: 'KD-000001', fullName: 'Sri Wahyuni (demo)', role: 'KADER' as const, rw: '01', village: VILLAGE },
  'HM-000127': { sehatiId: 'HM-000127', fullName: 'Warga Contoh (demo)', role: 'WARGA' as const, rw: '02', village: VILLAGE },
}

export const RWS = ['01', '02', '03', '04', '05', '06', '07', '08']

/* ---------------- Kader ---------------- */
export const cadres: Cadre[] = [
  { sehatiId: 'KD-000001', fullName: 'Sri Wahyuni (demo)', rw: '01', active: true, lastSeenAt: now - 2 * 3600e3 },
  { sehatiId: 'KD-000002', fullName: 'Endang Lestari (demo)', rw: '02', active: true, lastSeenAt: now - 26 * 3600e3 },
  { sehatiId: 'KD-000003', fullName: 'Siti Aminah (demo)', rw: '03', active: true, lastSeenAt: now - 50 * 60e3 },
  { sehatiId: 'KD-000004', fullName: 'Tri Handayani (demo)', rw: '04', active: true, lastSeenAt: now - 4 * DAY },
  { sehatiId: 'KD-000005', fullName: 'Dwi Rahmawati (demo)', rw: '05', active: true, lastSeenAt: now - 7 * 3600e3 },
  { sehatiId: 'KD-000006', fullName: 'Nur Hidayah (demo)', rw: '06', active: false, lastSeenAt: now - 41 * DAY },
  { sehatiId: 'KD-000007', fullName: 'Yuli Astuti (demo)', rw: '02', active: true, lastSeenAt: null },
]

/* ---------------- Statistik per RW ---------------- */
const rwRaw: { rw: string; registered: number; screened: number; open: number; elevated: number }[] = [
  { rw: '01', registered: 72, screened: 48, open: 7, elevated: 14 },
  { rw: '02', registered: 88, screened: 61, open: 9, elevated: 22 },
  { rw: '03', registered: 64, screened: 39, open: 6, elevated: 9 },
  { rw: '04', registered: 57, screened: 30, open: 5, elevated: 11 },
  { rw: '05', registered: 69, screened: 52, open: 8, elevated: 19 },
  { rw: '06', registered: 41, screened: 18, open: 2, elevated: 6 },
  { rw: '07', registered: 17, screened: 4, open: 0, elevated: 1 },
  { rw: '08', registered: 4, screened: 3, open: 0, elevated: 0 },
]

export const rwStats: RwStat[] = rwRaw.map((r) => {
  const registered = suppress(r.registered)
  const screened = suppress(r.screened)
  const followUpOpen = suppress(r.open)
  const suppressed = registered === null || screened === null || followUpOpen === null
  // Persentase hanya bila penyebut dan pembilang cukup besar.
  const elevatedBpPct = screened !== null && r.elevated >= 5 ? Math.round((r.elevated / r.screened) * 100) / 100 : null
  return { rw: r.rw, registered, screened, followUpOpen, elevatedBpPct, suppressed }
})

/* ---------------- Ringkasan ---------------- */
function lastMonths(n: number): string[] {
  const out: string[] = []
  const d = new Date()
  d.setDate(1)
  for (let i = n - 1; i >= 0; i--) {
    const m = new Date(d.getFullYear(), d.getMonth() - i, 1)
    out.push(`${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}`)
  }
  return out
}
const monthly = [3, 18, 27, 34, 22, 41, 38, 52, 47, 63, 71, 29]

export function buildOverview(followups: FollowUp[]): Overview {
  const open = followups.filter((f) => f.status === 'OPEN' || f.status === 'SCHEDULED' || f.status === 'IN_PROGRESS')
  const overdue = open.filter((f) => f.dueAt !== null && f.dueAt < startOfToday)
  return {
    registered: suppress(rwRaw.reduce((a, r) => a + r.registered, 0)),
    screened30d: 146,
    followUpOpen: suppress(open.length),
    followUpOverdue: suppress(overdue.length),
    levels: { HEALTHY_HABIT: 168, RISK_AWARENESS: 139, HIGHER_MONITORING: 72, MEDICAL_FOLLOW_UP: 33 },
    bp: { normal: 198, elevated: 104, high: 81 },
    smokers: 94,
    byMonth: lastMonths(12).map((month, i) => ({ month, screened: suppress(monthly[i] ?? 0) })),
    updatedAt: now - 7 * 60e3,
  }
}

/* ---------------- Tindak lanjut ---------------- */
const REASONS: { type: FollowUp['type']; reason: string; priority: number }[] = [
  { type: 'PUSKESMAS_EVALUATION', reason: 'Tekanan darah sangat tinggi', priority: 3 },
  { type: 'HOME_VISIT', reason: 'Tekanan darah tinggi berulang', priority: 3 },
  { type: 'REPEAT_MEASUREMENT', reason: 'Tekanan darah perlu diukur ulang', priority: 2 },
  { type: 'REPEAT_MEASUREMENT', reason: 'Tekanan darah perlu diukur ulang', priority: 2 },
  { type: 'PUSKESMAS_EVALUATION', reason: 'Gula darah tinggi, perlu ditinjau tenaga kesehatan', priority: 2 },
  { type: 'REPEAT_MEASUREMENT', reason: 'Gula darah perlu diukur ulang', priority: 1 },
  { type: 'EDUCATION', reason: 'Kolesterol total tinggi', priority: 1 },
  { type: 'REPEAT_MEASUREMENT', reason: 'Tekanan darah rendah, ukur ulang', priority: 1 },
  { type: 'EDUCATION', reason: 'Edukasi berhenti merokok', priority: 1 },
]
const STATUSES: FollowUp['status'][] = ['OPEN', 'OPEN', 'OPEN', 'SCHEDULED', 'SCHEDULED', 'IN_PROGRESS', 'DONE', 'DONE', 'CANCELLED']

export const followups: FollowUp[] = Array.from({ length: 52 }, (_, i) => {
  const r = pick(REASONS)
  const rw = pick(['01', '01', '02', '02', '02', '03', '04', '05', '05', '06'])
  const status = pick(STATUSES)
  const createdAt = now - int(1, 60) * DAY - int(0, 600) * 60e3
  const due = status === 'DONE' || status === 'CANCELLED'
    ? createdAt + int(3, 20) * DAY
    : startOfToday + int(-9, 21) * DAY + 9 * 3600e3
  const cadre = cadres.find((c) => c.rw === rw && c.active)
  const assigned = status !== 'OPEN' || rand() > 0.55 ? cadre ?? null : null
  return {
    id: `fu-demo-${String(i + 1).padStart(4, '0')}`,
    sehatiId: `HM-${String(100 + int(0, 99)).padStart(6, '0')}`,
    rw,
    type: r.type,
    reason: r.reason,
    priority: r.priority,
    status,
    dueAt: status === 'OPEN' && rand() > 0.8 ? null : due,
    assignedCadreId: assigned?.sehatiId ?? null,
    assignedCadreName: assigned?.fullName ?? null,
    createdAt,
  }
}).sort((a, b) => b.priority - a.priority || (a.dueAt ?? Infinity) - (b.dueAt ?? Infinity))

/* ---------------- Jadwal Posyandu ---------------- */
const LOCS: Record<string, string> = {
  '01': 'Balai RW 01', '02': 'Balai RW 02', '03': 'Rumah Bu RT 03/02', '04': 'Mushola Al-Ikhlas',
  '05': 'Pos Kamling RW 05', '06': 'Balai RW 06', '07': 'Balai Desa Mirigambar', '08': 'Balai Desa Mirigambar',
}
let posyanduSeq = 1
export const newPosyanduId = () => `ps-demo-${String(posyanduSeq++).padStart(3, '0')}`

export const posyandu: PosyanduSchedule[] = [
  [-6, '04'], [-2, '03'], [2, '02'], [3, '01'], [6, '05'], [9, '03'], [12, '06'], [16, '04'], [20, '07'], [27, '02'],
].map(([offset, rw]) => {
  const d = new Date(startOfToday + (offset as number) * DAY)
  return {
    id: newPosyanduId(),
    rw: rw as string,
    date: isoDate(d),
    startTime: '08:00',
    endTime: (rw as string) === '06' ? '10:30' : '11:00',
    location: LOCS[rw as string] ?? 'Balai Desa',
    notes: pick(['Bawa QR SEHATI', 'Skrining dewasa & lansia: tensi, gula, kolesterol', 'Puasa 8 jam bagi yang cek gula puasa', null]),
  }
})

/* ---------------- Ambang klinis ---------------- */
export const thresholdState: { thresholds: ClinicalThresholds | null; version: number } = { thresholds: null, version: 3 }

/* ---------------- Audit ---------------- */
const AUDIT_TEMPLATES: { action: string; role: string; actor: () => string; subject: () => string | null; detail: () => string | null }[] = [
  { action: 'LOGIN', role: 'ADMIN', actor: () => 'AD-000001', subject: () => null, detail: () => 'Dashboard web' },
  { action: 'LOGIN', role: 'KADER', actor: () => pick(cadres).sehatiId, subject: () => null, detail: () => 'Aplikasi Android' },
  { action: 'LOGIN_FAILED', role: 'KADER', actor: () => pick(cadres).sehatiId, subject: () => null, detail: () => 'Kata sandi salah' },
  { action: 'FOLLOWUP_ASSIGN', role: 'ADMIN', actor: () => 'AD-000001', subject: () => pick(followups).id, detail: () => `Ditugaskan ke ${pick(cadres).sehatiId}` },
  { action: 'FOLLOWUP_SCHEDULE', role: 'ADMIN', actor: () => 'AD-000001', subject: () => pick(followups).id, detail: () => 'Jadwal diubah' },
  { action: 'POSYANDU_CREATE', role: 'ADMIN', actor: () => 'AD-000001', subject: () => `RW ${pick(RWS)}`, detail: () => 'Jadwal baru' },
  { action: 'CADRE_CREATE', role: 'ADMIN', actor: () => 'AD-000001', subject: () => pick(cadres).sehatiId, detail: () => 'Akun kader baru' },
  { action: 'USER_DELETE', role: 'WARGA', actor: () => `HM-${String(100 + int(0, 99)).padStart(6, '0')}`, subject: () => 'diri sendiri', detail: () => 'Hak penghapusan data' },
]
export const audit: AuditEntry[] = [
  { at: now - 41 * DAY, actorId: 'AD-000001', actorRole: 'ADMIN', action: 'THRESHOLDS_RESET', subjectId: 'ClinicalThresholds', detail: 'Versi 3 — kembali ke bawaan aplikasi' },
  { at: now - 40 * DAY, actorId: 'AD-000001', actorRole: 'ADMIN', action: 'CADRE_DEACTIVATE', subjectId: 'KD-000006', detail: 'Akun kader dinonaktifkan' },
  ...Array.from({ length: 58 }, () => {
    const t = pick(AUDIT_TEMPLATES)
    return { at: now - int(5, 38 * 24 * 60) * 60e3, actorId: t.actor(), actorRole: t.role, action: t.action, subjectId: t.subject(), detail: t.detail() }
  }),
].sort((a, b) => b.at - a.at)

/* ---------------- Pemakaian AI ---------------- */
export function aiUsage(days: number): AiUsageDay[] {
  const r = mulberry32(77)
  return Array.from({ length: days }, (_, i) => {
    const d = new Date(startOfToday - (days - 1 - i) * DAY)
    const weekend = d.getDay() === 0 || d.getDay() === 6
    const trend = 18 + i * 1.4
    const messages = Math.round(trend * (weekend ? 0.7 : 1) + r() * 22)
    const usersN = Math.max(5, Math.round(messages / (2.4 + r())))
    const em = r() > 0.72 ? Math.floor(r() * 7) : 0
    return { day: isoDate(d), messages: suppress(messages), users: suppress(usersN), emergencies: suppress(em) }
  })
}
