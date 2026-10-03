/**
 * Server tiruan di peramban untuk mode demo (VITE_MOCK=1). Mengikuti bentuk data docs/API.md.
 * Login demo: AD-000001 (ADMIN) dengan kata sandi apa pun; KD-000001 (KADER); HM-000127 (WARGA).
 * Kata sandi "kunci" mensimulasikan 429 ACCOUNT_LOCKED.
 */
import type { CadreCreate, ClinicalThresholds, FollowUp, PosyanduInput, Role } from '../api/types'
import { validateThresholds } from '../lib/thresholds'
import {
  aiUsage, audit, buildOverview, cadres, followups, newPosyanduId, posyandu, rwStats, thresholdState, users,
} from './data'

const json = (status: number, body: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
  })
const fail = (status: number, code: string, message: string) => json(status, { error: { code, message } })
const noContent = () => new Response(null, { status: 204 })
const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))

interface Ctx { role: Role; id: string }

function auth(init: RequestInit): Ctx | null {
  const h = (init.headers ?? {}) as Record<string, string>
  const m = /^Bearer mock\.(ADMIN|KADER|WARGA)\.([A-Z]{2}-\d{6})\./.exec(h.Authorization ?? '')
  return m ? { role: m[1] as Role, id: m[2] as string } : null
}

function body<T>(init: RequestInit): T {
  try {
    return JSON.parse(String(init.body ?? '{}')) as T
  } catch {
    return {} as T
  }
}

function log(actorId: string, action: string, subjectId: string | null, detail: string | null) {
  audit.unshift({ at: Date.now(), actorId, actorRole: 'ADMIN', action, subjectId, detail })
}

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/
const DATE = /^\d{4}-\d{2}-\d{2}$/

function validatePosyandu(p: Partial<PosyanduInput>, partial: boolean): string | null {
  const req = ['rw', 'date', 'startTime', 'endTime', 'location'] as const
  if (!partial) for (const k of req) if (!p[k]) return 'Lengkapi RW, tanggal, jam, dan lokasi.'
  if (p.rw !== undefined && !/^\d{2}$/.test(p.rw)) return 'RW harus dua digit, misalnya 02.'
  if (p.date !== undefined && !DATE.test(p.date)) return 'Format tanggal tidak valid.'
  if ((p.startTime && !TIME.test(p.startTime)) || (p.endTime && !TIME.test(p.endTime))) return 'Format jam tidak valid.'
  if (p.startTime && p.endTime && p.endTime <= p.startTime) return 'Jam selesai harus setelah jam mulai.'
  return null
}

export async function mockFetch(path: string, init: RequestInit): Promise<Response> {
  await delay(220 + Math.random() * 380)
  const method = (init.method ?? 'GET').toUpperCase()
  const url = new URL(path, 'http://mock')
  const p = url.pathname
  const q = url.searchParams

  if (p === '/health') return json(200, { status: 'ok', version: '2.0.0-mock', time: Date.now(), ai: true })

  if (p === '/auth/login' && method === 'POST') {
    const b = body<{ sehatiId?: string; password?: string }>(init)
    const id = (b.sehatiId ?? '').trim().toUpperCase()
    if (b.password === 'kunci')
      return fail(429, 'ACCOUNT_LOCKED', 'Akun dikunci sementara selama 15 menit karena terlalu banyak percobaan masuk.')
    const user = users[id as keyof typeof users]
    if (!user || !b.password) return fail(401, 'INVALID_CREDENTIALS', 'SEHATI ID atau kata sandi salah.')
    const ttl = user.role === 'WARGA' ? 30 * 86400e3 : 12 * 3600e3
    return json(200, {
      token: `mock.${user.role}.${user.sehatiId}.${Math.random().toString(36).slice(2)}`,
      expiresAt: Date.now() + ttl,
      user,
    })
  }

  const ctx = auth(init)
  if (!ctx) return fail(401, 'UNAUTHORIZED', 'Sesi Anda telah berakhir. Silakan masuk kembali.')

  if (p === '/auth/logout') return noContent()
  if (p === '/auth/me') return json(200, users[ctx.id as keyof typeof users] ?? null)
  if (p === '/config')
    return json(200, {
      thresholds: thresholdState.thresholds,
      thresholdsVersion: thresholdState.version,
      emergencyNumbers: '119 atau 112',
      posyandu: posyandu.filter((s) => s.date >= new Date().toISOString().slice(0, 10)),
    })

  if (!p.startsWith('/admin/')) return fail(404, 'NOT_FOUND', 'Endpoint tidak ditemukan.')
  if (ctx.role !== 'ADMIN') return fail(403, 'FORBIDDEN', 'Hanya Admin Puskesmas yang dapat membuka dashboard.')

  /* ---- Ringkasan ---- */
  if (p === '/admin/overview') return json(200, buildOverview(followups))
  if (p === '/admin/rw') return json(200, rwStats)

  /* ---- Tindak lanjut ---- */
  if (p === '/admin/followups' && method === 'GET') {
    const status = q.get('status') ?? 'ALL'
    const rw = q.get('rw')
    return json(200, followups.filter((f) => (status === 'ALL' || f.status === status) && (!rw || f.rw === rw)))
  }
  let m = /^\/admin\/followups\/([^/]+)\/(assign|schedule)$/.exec(p)
  if (m && method === 'POST') {
    const f = followups.find((x) => x.id === decodeURIComponent(m![1]!))
    if (!f) return fail(404, 'NOT_FOUND', 'Tindak lanjut tidak ditemukan.')
    if (m[2] === 'assign') {
      const { cadreId } = body<{ cadreId: string | null }>(init)
      if (cadreId === null) {
        f.assignedCadreId = null
        f.assignedCadreName = null
      } else {
        const c = cadres.find((x) => x.sehatiId === cadreId)
        if (!c) return fail(404, 'CADRE_NOT_FOUND', 'Kader tidak ditemukan.')
        if (!c.active) return fail(409, 'CADRE_INACTIVE', 'Kader ini sedang nonaktif.')
        f.assignedCadreId = c.sehatiId
        f.assignedCadreName = c.fullName
      }
      log(ctx.id, 'FOLLOWUP_ASSIGN', f.id, cadreId ? `Ditugaskan ke ${cadreId}` : 'Penugasan dilepas')
    } else {
      const { dueAt } = body<{ dueAt: number }>(init)
      if (typeof dueAt !== 'number' || !Number.isFinite(dueAt)) return fail(400, 'VALIDATION', 'Tanggal jadwal tidak valid.')
      f.dueAt = dueAt
      if (f.status === 'OPEN') f.status = 'SCHEDULED'
      log(ctx.id, 'FOLLOWUP_SCHEDULE', f.id, `Jadwal ${new Date(dueAt).toISOString().slice(0, 10)}`)
    }
    return json(200, f satisfies FollowUp)
  }

  /* ---- Kader ---- */
  if (p === '/admin/cadres' && method === 'GET') return json(200, cadres)
  if (p === '/admin/cadres' && method === 'POST') {
    const b = body<Partial<CadreCreate>>(init)
    if (!b.fullName?.trim() || !b.rw || !b.password) return fail(400, 'VALIDATION', 'Nama, RW, dan kata sandi wajib diisi.')
    if (b.password.length < 8) return fail(400, 'WEAK_PASSWORD', 'Kata sandi minimal 8 karakter.')
    const next = Math.max(...cadres.map((c) => Number(c.sehatiId.slice(3)))) + 1
    const sehatiId = `KD-${String(next).padStart(6, '0')}`
    cadres.push({ sehatiId, fullName: b.fullName.trim(), rw: b.rw, active: true, lastSeenAt: null })
    log(ctx.id, 'CADRE_CREATE', sehatiId, `RW ${b.rw}`)
    return json(201, { sehatiId })
  }
  m = /^\/admin\/cadres\/([^/]+)$/.exec(p)
  if (m && method === 'PATCH') {
    const c = cadres.find((x) => x.sehatiId === decodeURIComponent(m![1]!))
    if (!c) return fail(404, 'NOT_FOUND', 'Kader tidak ditemukan.')
    const { active } = body<{ active: boolean }>(init)
    c.active = active
    log(ctx.id, active ? 'CADRE_ACTIVATE' : 'CADRE_DEACTIVATE', c.sehatiId, null)
    return json(200, c)
  }

  /* ---- Posyandu ---- */
  if (p === '/admin/posyandu' && method === 'GET') return json(200, [...posyandu].sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime)))
  if (p === '/admin/posyandu' && method === 'POST') {
    const b = body<PosyanduInput>(init)
    const err = validatePosyandu(b, false)
    if (err) return fail(400, 'VALIDATION', err)
    const s = { ...b, notes: b.notes || null, id: newPosyanduId() }
    posyandu.push(s)
    log(ctx.id, 'POSYANDU_CREATE', s.id, `RW ${s.rw}, ${s.date}`)
    return json(201, s)
  }
  m = /^\/admin\/posyandu\/([^/]+)$/.exec(p)
  if (m) {
    const idx = posyandu.findIndex((x) => x.id === decodeURIComponent(m![1]!))
    if (idx < 0) return fail(404, 'NOT_FOUND', 'Jadwal tidak ditemukan.')
    if (method === 'DELETE') {
      const [s] = posyandu.splice(idx, 1)
      log(ctx.id, 'POSYANDU_DELETE', s!.id, `RW ${s!.rw}, ${s!.date}`)
      return noContent()
    }
    if (method === 'PATCH') {
      const b = body<Partial<PosyanduInput>>(init)
      const merged = { ...posyandu[idx]!, ...b }
      const err = validatePosyandu(merged, false)
      if (err) return fail(400, 'VALIDATION', err)
      posyandu[idx] = merged
      log(ctx.id, 'POSYANDU_UPDATE', merged.id, `RW ${merged.rw}, ${merged.date}`)
      return json(200, merged)
    }
  }

  /* ---- Ambang ---- */
  if (p === '/admin/thresholds' && method === 'GET') return json(200, thresholdState)
  if (p === '/admin/thresholds' && method === 'PUT') {
    const { thresholds } = body<{ thresholds: ClinicalThresholds | null }>(init)
    if (thresholds) {
      const issue = validateThresholds(thresholds)
      if (issue) return fail(400, 'INVALID_THRESHOLDS', issue.message)
    }
    thresholdState.thresholds = thresholds
    thresholdState.version += 1
    log(ctx.id, thresholds ? 'THRESHOLDS_UPDATE' : 'THRESHOLDS_RESET', 'ClinicalThresholds', `Versi ${thresholdState.version}`)
    return json(200, thresholdState)
  }

  /* ---- Audit, AI, laporan ---- */
  if (p === '/admin/audit') return json(200, audit.slice(0, Number(q.get('limit') ?? 100)))
  if (p === '/admin/ai-usage') return json(200, aiUsage(Math.min(90, Number(q.get('days') ?? 30))))
  if (p === '/admin/reports/summary.csv') {
    const cell = (v: number | null) => (v === null ? '<5' : String(v))
    const rows = rwStats.map((r) =>
      [r.rw, cell(r.registered), cell(r.screened), cell(r.followUpOpen), r.elevatedBpPct === null ? '' : (r.elevatedBpPct * 100).toFixed(0)].join(','))
    const csv = ['# DATA SINTETIS (mode demo)', 'rw,terdaftar,terskrining,tindak_lanjut_terbuka,persen_td_tinggi', ...rows].join('\n')
    return new Response(csv, { status: 200, headers: { 'Content-Type': 'text/csv; charset=utf-8' } })
  }

  return fail(404, 'NOT_FOUND', 'Endpoint tidak ditemukan.')
}
