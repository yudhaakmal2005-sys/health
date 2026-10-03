import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { count as fmtCount, num, SUPPRESSED_LABEL } from '../lib/format'
import type { Count } from '../api/types'
import type { Tone } from '../lib/domain'
import { cx, Segmented, Suppressed, toneDot } from './ui'

/* Grafik SVG ringan. Setiap grafik punya tabel alternatif, label teks, dan nilai yang dapat dibaca tanpa warna. */

function useWidth<T extends HTMLElement>(fallback = 600) {
  const ref = useRef<T>(null)
  const [w, setW] = useState(fallback)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver(([e]) => e && setW(Math.max(240, Math.round(e.contentRect.width))))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  return [ref, w] as const
}

/** Skala sumbu dengan langkah bulat (1, 2, 5 × 10^n) agar label tidak berdesimal. */
function niceScale(v: number, target = 4): { max: number; ticks: number[] } {
  const raw = Math.max(1, v) / target
  const exp = Math.pow(10, Math.floor(Math.log10(raw)))
  const f = raw / exp
  const step = Math.max(1, (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * exp)
  const max = step * Math.max(1, Math.ceil(Math.max(1, v) / step))
  const ticks: number[] = []
  for (let t = 0; t <= max + 1e-9; t += step) ticks.push(t)
  return { max, ticks }
}

/** Bingkai grafik dengan pilihan "Grafik / Tabel". */
export function ChartFrame({ chart, table, label }: { chart: ReactNode; table: ReactNode; label: string }) {
  const [view, setView] = useState<'chart' | 'table'>('chart')
  return (
    <div>
      <div className="no-print mb-3 flex justify-end">
        <Segmented
          size="sm"
          label={`Tampilan ${label}`}
          value={view}
          onChange={setView}
          options={[{ value: 'chart', label: 'Grafik' }, { value: 'table', label: 'Tabel' }]}
        />
      </div>
      {view === 'chart' ? chart : table}
    </div>
  )
}

export function DataTable({ head, rows, caption }: { head: string[]; rows: ReactNode[][]; caption: string }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-line">
      <table className="w-full text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-surface-2 text-left text-xs font-semibold text-muted">
          <tr>{head.map((h, i) => <th key={h} scope="col" className={cx('px-3 py-2', i > 0 && 'text-right')}>{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-line">
              {r.map((c, j) => (j === 0
                ? <th key={j} scope="row" className="px-3 py-2 text-left font-medium text-fg-2">{c}</th>
                : <td key={j} className="tabular px-3 py-2 text-right text-fg">{c}</td>))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function CountCell({ value }: { value: Count | undefined }) {
  return value === null ? <Suppressed /> : <>{fmtCount(value)}</>
}

/* ---------------- Grafik batang vertikal (per bulan / per hari) ---------------- */

export interface BarDatum { key: string; label: string; fullLabel: string; value: Count }

export function BarChart({ data, height = 220, unit = 'warga', ariaLabel, line }: {
  data: BarDatum[]
  height?: number
  unit?: string
  ariaLabel: string
  /** Seri garis opsional (mis. jumlah pengguna) — skala sumbu sama. */
  line?: { label: string; values: Count[] }
}) {
  const [ref, width] = useWidth<HTMLDivElement>()
  const [hover, setHover] = useState<number | null>(null)
  const pid = useId().replace(/:/g, '')
  const padL = 34, padR = 8, padT = 14, padB = 26
  const w = width - padL - padR
  const h = height - padT - padB
  const values = data.map((d) => d.value ?? 0).concat(line ? line.values.map((v) => v ?? 0) : [])
  const { max, ticks } = niceScale(Math.max(1, ...values))
  const step = w / Math.max(1, data.length)
  const barW = Math.max(3, Math.min(34, step * 0.62))
  const y = (v: number) => padT + h - (v / max) * h
  const labelEvery = Math.ceil(data.length / Math.max(2, Math.floor(w / 46)))
  const hv = hover !== null ? data[hover] : undefined

  return (
    <div ref={ref} className="relative select-none">
      <svg width={width} height={height} role="img" aria-label={ariaLabel} className="block overflow-visible">
        <defs>
          <pattern id={`h${pid}`} width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <rect width="6" height="6" fill="var(--surface-2)" />
            <line x1="0" y1="0" x2="0" y2="6" stroke="var(--muted)" strokeOpacity="0.55" strokeWidth="2" />
          </pattern>
        </defs>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={padL} x2={width - padR} y1={y(t)} y2={y(t)} stroke="var(--line)" strokeDasharray={t === 0 ? undefined : '3 4'} />
            <text x={padL - 8} y={y(t)} dy="0.32em" textAnchor="end" className="fill-muted text-[10.5px] tabular">{num(Math.round(t))}</text>
          </g>
        ))}
        {data.map((d, i) => {
          const cx0 = padL + step * i + step / 2
          const sup = d.value === null
          const v = d.value ?? 0
          const bh = sup ? Math.max(10, (4 / max) * h) : Math.max(v > 0 ? 2 : 0, (v / max) * h)
          const active = hover === i
          return (
            <g key={d.key}>
              <rect
                x={cx0 - barW / 2}
                y={padT + h - bh}
                width={barW}
                height={bh}
                rx={Math.min(4, barW / 2)}
                fill={sup ? `url(#h${pid})` : 'var(--primary)'}
                stroke={sup ? 'var(--line-strong)' : 'none'}
                opacity={hover === null || active ? 1 : 0.45}
                className="transition-opacity"
              />
              {i % labelEvery === 0 && (
                <text x={cx0} y={height - 8} textAnchor="middle" className="fill-muted text-[10.5px]">{d.label}</text>
              )}
              {/* Area sentuh lebih besar dari batang */}
              <rect
                x={padL + step * i}
                y={padT}
                width={step}
                height={h}
                fill="transparent"
                tabIndex={0}
                role="graphics-symbol"
                aria-label={`${d.fullLabel}: ${sup ? 'kurang dari 5 (disembunyikan)' : `${num(v)} ${unit}`}${line ? `, ${line.label}: ${fmtCount(line.values[i])}` : ''}`}
                onMouseEnter={() => setHover(i)}
                onMouseLeave={() => setHover(null)}
                onFocus={() => setHover(i)}
                onBlur={() => setHover(null)}
                className="outline-none focus-visible:stroke-[var(--ring)] focus-visible:stroke-2"
              />
            </g>
          )
        })}
        {line && (() => {
          const pts = line.values.map((v, i) => (v === null ? null : [padL + step * i + step / 2, y(v)] as const))
          const segs: string[] = []
          let cur = ''
          pts.forEach((p) => {
            if (!p) { if (cur) segs.push(cur); cur = ''; return }
            cur += `${cur ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`
          })
          if (cur) segs.push(cur)
          return (
            <g pointerEvents="none">
              {segs.map((s, i) => <path key={i} d={s} fill="none" stroke="var(--fg-2)" strokeWidth="2" strokeLinejoin="round" />)}
              {pts.map((p, i) => p && (hover === i || data.length <= 14) && (
                <circle key={i} cx={p[0]} cy={p[1]} r={hover === i ? 4.5 : 3} fill="var(--surface)" stroke="var(--fg-2)" strokeWidth="2" />
              ))}
            </g>
          )
        })()}
      </svg>
      {hv && hover !== null && (
        <div
          className="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full rounded-xl border border-line bg-surface px-3 py-2 text-xs shadow-pop"
          style={{ left: Math.min(width - 80, Math.max(80, padL + step * hover + step / 2)), top: padT + 4 }}
          aria-hidden="true"
        >
          <p className="font-semibold text-fg">{hv.fullLabel}</p>
          <p className="mt-0.5 flex items-center gap-1.5 text-fg-2">
            <span className="h-2 w-2 rounded-sm bg-primary" />
            {hv.value === null ? `${SUPPRESSED_LABEL} (disembunyikan)` : `${num(hv.value)} ${unit}`}
          </p>
          {line && (
            <p className="mt-0.5 flex items-center gap-1.5 text-fg-2">
              <span className="h-0.5 w-3 rounded bg-fg-2" />
              {line.label}: {fmtCount(line.values[hover])}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

/* ---------------- Batang bertumpuk 100% + legenda ---------------- */

export interface Segment { key: string; label: string; value: Count; tone: Tone; hint?: string }

const TONE_FILL: Record<Tone, string> = {
  green: 'var(--risk-green)', yellow: 'var(--risk-yellow)', orange: 'var(--risk-orange)', red: 'var(--risk-red)',
  primary: 'var(--primary)', neutral: 'var(--muted)',
}

export function StackedBar({ segments, ariaLabel }: { segments: Segment[]; ariaLabel: string }) {
  const total = segments.reduce((a, s) => a + (s.value ?? 0), 0)
  const [hover, setHover] = useState<string | null>(null)
  return (
    <div>
      <div className="flex h-4 w-full gap-[2px] overflow-hidden rounded-full bg-surface-2" role="img" aria-label={ariaLabel}>
        {segments.map((s) => {
          if (s.value === null) return <div key={s.key} className="hatch h-full w-3 shrink-0" title={`${s.label}: < 5`} />
          if (!s.value || !total) return null
          return (
            <div
              key={s.key}
              className="h-full transition-[opacity,flex-grow] duration-500 first:rounded-l-full last:rounded-r-full"
              style={{ flexGrow: s.value, background: TONE_FILL[s.tone], opacity: hover && hover !== s.key ? 0.4 : 1 }}
              title={`${s.label}: ${num(s.value)}`}
            />
          )
        })}
      </div>
      <ul className="mt-4 grid gap-1">
        {segments.map((s) => (
          <li
            key={s.key}
            onMouseEnter={() => setHover(s.key)}
            onMouseLeave={() => setHover(null)}
            className="flex items-center gap-3 rounded-lg px-2 py-1.5 -mx-2 hover:bg-surface-2"
          >
            <span className={cx('h-2.5 w-2.5 shrink-0 rounded-[3px]', toneDot(s.tone))} aria-hidden="true" />
            <span className="min-w-0 flex-1 text-sm text-fg-2">
              {s.label}
              {s.hint && <span className="block text-xs text-muted">{s.hint}</span>}
            </span>
            <span className="tabular text-sm font-semibold text-fg">
              {s.value === null ? <Suppressed /> : num(s.value)}
            </span>
            <span className="tabular w-11 text-right text-xs text-muted">
              {s.value !== null && total ? `${Math.round((s.value / total) * 100)}%` : ''}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/* ---------------- Batang horizontal ---------------- */

export function HBars({ items, max: maxIn }: { items: { key: string; label: string; value: Count; tone?: Tone; hint?: string }[]; max?: number }) {
  const max = maxIn ?? Math.max(1, ...items.map((i) => i.value ?? 0))
  return (
    <ul className="space-y-3.5">
      {items.map((it) => (
        <li key={it.key}>
          <div className="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
            <span className="text-fg-2">
              {it.label}
              {it.hint && <span className="ml-1.5 text-xs text-muted">{it.hint}</span>}
            </span>
            <span className="tabular font-semibold text-fg">{it.value === null ? <Suppressed /> : num(it.value)}</span>
          </div>
          <div className="h-2.5 w-full overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
            {it.value === null
              ? <div className="hatch h-full w-6 rounded-full" />
              : <div className="h-full rounded-full transition-[width] duration-700 ease-out" style={{ width: `${(it.value / max) * 100}%`, background: TONE_FILL[it.tone ?? 'primary'] }} />}
          </div>
        </li>
      ))}
    </ul>
  )
}

/* ---------------- Cincin persentase ---------------- */

export function Ring({ value, size = 64, label }: { value: number | null; size?: number; label: string }) {
  const r = size / 2 - 6
  const c = 2 * Math.PI * r
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`${label}: ${value === null ? 'tidak tersedia' : `${Math.round(value * 100)}%`}`}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--surface-2)" strokeWidth="6" />
      {value !== null && (
        <circle
          cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--primary)" strokeWidth="6" strokeLinecap="round"
          strokeDasharray={`${c * value} ${c}`} transform={`rotate(-90 ${size / 2} ${size / 2})`}
          className="transition-[stroke-dasharray] duration-700"
        />
      )}
      <text x="50%" y="50%" dy="0.35em" textAnchor="middle" className="fill-fg text-[13px] font-bold tabular">
        {value === null ? '—' : `${Math.round(value * 100)}%`}
      </text>
    </svg>
  )
}
