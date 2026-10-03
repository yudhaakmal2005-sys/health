import type { Count } from '../api/types'

const nf = new Intl.NumberFormat('id-ID')
const pf = new Intl.NumberFormat('id-ID', { style: 'percent', maximumFractionDigits: 0 })
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des']
const MONTHS = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember']
const DAYS = ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu']

export const SUPPRESSED_LABEL = '< 5'
export const MIN_CELL = 5

export function num(n: number): string {
  return nf.format(n)
}

/** Hitungan agregat; `null` ditampilkan sebagai "< 5". */
export function count(n: Count | undefined): string {
  if (n === null) return SUPPRESSED_LABEL
  if (n === undefined) return '—'
  return nf.format(n)
}

export function pct(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) return '—'
  return pf.format(v)
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}

export function dateTime(ms: number | null | undefined): string {
  if (!ms) return '—'
  const d = new Date(ms)
  return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]} ${d.getFullYear()}, ${pad(d.getHours())}.${pad(d.getMinutes())}`
}

export function dateShort(ms: number | null | undefined): string {
  if (!ms) return '—'
  const d = new Date(ms)
  return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]} ${d.getFullYear()}`
}

/** 'YYYY-MM-DD' → Date lokal (tanpa geser zona waktu). */
export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1)
}

export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function dateLong(iso: string): string {
  const d = parseIsoDate(iso)
  return `${DAYS[d.getDay()]}, ${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`
}

export function dayShort(iso: string): string {
  const d = parseIsoDate(iso)
  return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}`
}

/** '2026-09' → 'Sep 2026' */
export function monthLabel(ym: string, withYear = true): string {
  const [y, m] = ym.split('-').map(Number)
  const name = MONTHS_SHORT[(m ?? 1) - 1] ?? ym
  return withYear ? `${name} ${y}` : name
}

export function monthLong(ym: string): string {
  const [y, m] = ym.split('-').map(Number)
  return `${MONTHS[(m ?? 1) - 1]} ${y}`
}

export function relative(ms: number | null | undefined, now = Date.now()): string {
  if (!ms) return 'Belum pernah'
  const diff = now - ms
  const min = Math.round(diff / 60000)
  if (min < 1) return 'Baru saja'
  if (min < 60) return `${min} menit lalu`
  const h = Math.round(min / 60)
  if (h < 24) return `${h} jam lalu`
  const d = Math.round(h / 24)
  if (d < 30) return `${d} hari lalu`
  return dateShort(ms)
}

export function daysUntil(ms: number, now = Date.now()): number {
  const a = new Date(ms); a.setHours(0, 0, 0, 0)
  const b = new Date(now); b.setHours(0, 0, 0, 0)
  return Math.round((a.getTime() - b.getTime()) / 86400000)
}

export function sum(values: Count[]): { total: number; anySuppressed: boolean } {
  let total = 0
  let anySuppressed = false
  for (const v of values) {
    if (v === null) anySuppressed = true
    else total += v
  }
  return { total, anySuppressed }
}
