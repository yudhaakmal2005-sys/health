import { useState } from 'react'
import { Link } from 'react-router'
import type { RwStat } from '../../api/types'
import { CountCell, DataTable } from '../../components/charts'
import { Icon } from '../../components/Icon'
import { Button, Callout, Card, CardHeader, ErrorState, PageHeader, Segmented, Skeleton, Suppressed, cx } from '../../components/ui'
import { num, pct } from '../../lib/format'
import { useRw } from '../../lib/hooks'

type Metric = 'bp' | 'coverage' | 'open'

const METRICS: Record<Metric, { label: string; short: string; bins: number[]; fmt: (v: number) => string; describe: string }> = {
  bp: {
    label: '% tekanan darah tinggi', short: 'TD tinggi', bins: [0.1, 0.2, 0.3, 0.4], fmt: (v) => pct(v),
    describe: 'Persentase warga terskrining dengan tekanan darah pada rentang tinggi (menurut ambang aktif).',
  },
  coverage: {
    label: 'Cakupan skrining', short: 'Cakupan', bins: [0.3, 0.45, 0.6, 0.75], fmt: (v) => pct(v),
    describe: 'Warga terskrining dibanding warga terdaftar di RW tersebut.',
  },
  open: {
    label: 'Tindak lanjut terbuka', short: 'Terbuka', bins: [3, 5, 8, 12], fmt: (v) => num(v),
    describe: 'Jumlah tindak lanjut yang belum selesai.',
  },
}

/** Intensitas satu hue (biru) per kelas: 0..4 */
const STEPS = [8, 20, 34, 50, 68]

function metricValue(r: RwStat, m: Metric): number | null {
  if (m === 'bp') return r.elevatedBpPct
  if (m === 'open') return r.followUpOpen
  return r.screened !== null && r.registered !== null && r.registered > 0 ? r.screened / r.registered : null
}

function binOf(v: number, bins: number[]) {
  let i = 0
  while (i < bins.length && v >= (bins[i] ?? Infinity)) i++
  return i
}

function tileBg(step: number) {
  return `color-mix(in srgb, var(--primary) ${STEPS[step]}%, var(--surface))`
}

function Tile({ r, metric, delay }: { r: RwStat; metric: Metric; delay: number }) {
  const v = metricValue(r, metric)
  const meta = METRICS[metric]
  const step = v === null ? -1 : binOf(v, meta.bins)
  const hidden = v === null
  return (
    <Link
      to={`/dasbor/tindak-lanjut?rw=${r.rw}`}
      className={cx(
        'group relative flex min-h-[148px] flex-col justify-between overflow-hidden rounded-2xl border p-4 transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-card animate-fade-up',
        hidden ? 'hatch border-line-strong border-dashed' : 'border-transparent',
      )}
      style={{ background: hidden ? undefined : tileBg(step), animationDelay: `${delay}ms` }}
      aria-label={`RW ${r.rw}: ${meta.label} ${hidden ? 'disembunyikan' : meta.fmt(v)}. Buka tindak lanjut RW ${r.rw}.`}
    >
      <div className="flex items-start justify-between gap-2">
        <span className={cx('rounded-lg px-2 py-0.5 text-xs font-bold', hidden ? 'bg-surface text-fg-2' : 'bg-surface/80 text-fg')}>RW {r.rw}</span>
        <span className="flex items-center gap-2 text-fg-2">
          {r.suppressed && !hidden && <span title="Sebagian angka disembunyikan"><Icon name="eyeOff" size={15} /></span>}
          <Icon name="arrowRight" size={15} className="opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100" />
        </span>
      </div>
      <div>
        {hidden ? (
          <>
            <p className="text-2xl font-extrabold text-fg-2">&lt; 5</p>
            <p className="text-xs font-medium text-fg-2">Terlalu sedikit untuk ditampilkan</p>
          </>
        ) : (
          <>
            <p className="text-[1.75rem] leading-none font-extrabold tracking-tight text-fg tabular">{meta.fmt(v)}</p>
            <p className="mt-1 text-xs font-medium text-fg-2">{meta.short}</p>
          </>
        )}
        <dl className="mt-3 grid grid-cols-3 gap-1 text-[0.7rem] text-fg-2">
          <div><dt className="opacity-80">Terdaftar</dt><dd className="font-bold tabular">{r.registered === null ? '< 5' : num(r.registered)}</dd></div>
          <div><dt className="opacity-80">Skrining</dt><dd className="font-bold tabular">{r.screened === null ? '< 5' : num(r.screened)}</dd></div>
          <div><dt className="opacity-80">Terbuka</dt><dd className="font-bold tabular">{r.followUpOpen === null ? '< 5' : num(r.followUpOpen)}</dd></div>
        </dl>
      </div>
    </Link>
  )
}

function Legend({ metric }: { metric: Metric }) {
  const meta = METRICS[metric]
  const labels = [`< ${meta.fmt(meta.bins[0]!)}`, ...meta.bins.slice(0, -1).map((b, i) => `${meta.fmt(b)}–${meta.fmt(meta.bins[i + 1]!)}`), `≥ ${meta.fmt(meta.bins[meta.bins.length - 1]!)}`]
  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-fg-2" aria-label={`Legenda ${meta.label}`}>
      <ul className="flex flex-wrap items-center gap-1">
        {labels.map((l, i) => (
          <li key={l} className="flex flex-col items-center gap-1">
            <span className="h-3 w-14 rounded" style={{ background: tileBg(i) }} aria-hidden="true" />
            <span className="tabular text-[0.68rem] text-muted">{l}</span>
          </li>
        ))}
      </ul>
      <span className="flex items-center gap-2"><span className="hatch h-3 w-8 rounded border border-dashed border-line-strong" aria-hidden="true" /> Disembunyikan (1–4 orang)</span>
    </div>
  )
}

export default function RwMapPage() {
  const q = useRw()
  const [metric, setMetric] = useState<Metric>('bp')
  const rows = q.data ?? []
  const hiddenCount = rows.filter((r) => r.suppressed).length

  return (
    <>
      <PageHeader
        eyebrow="Pemantauan"
        title="Peta RW"
        description="Bandingkan RW untuk menentukan prioritas Posyandu dan kunjungan rumah. Pilih RW untuk melihat tindak lanjutnya."
        actions={<Button variant="secondary" size="sm" icon="refresh" onClick={q.reload} loading={q.loading && !q.initial}>Muat ulang</Button>}
      />

      <Card className="pb-5">
        <div className="flex flex-col gap-4 px-5 pt-5 sm:px-6 md:flex-row md:items-start md:justify-between">
          <div className="min-w-0">
            <h2 className="text-[0.975rem] font-bold text-fg">{METRICS[metric].label}</h2>
            <p className="mt-0.5 text-[0.8125rem] text-muted">{METRICS[metric].describe}</p>
          </div>
          <Segmented
            label="Ukuran yang ditampilkan"
            value={metric}
            onChange={setMetric}
            options={[{ value: 'bp', label: 'TD tinggi' }, { value: 'coverage', label: 'Cakupan' }, { value: 'open', label: 'Tindak lanjut' }]}
          />
        </div>
        <div className="px-5 pt-5 sm:px-6">
          {q.error && !q.data ? (
            <ErrorState error={q.error} onRetry={q.reload} compact />
          ) : q.initial ? (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4" role="status" aria-label="Memuat peta RW">
              {Array.from({ length: 8 }, (_, i) => <Skeleton key={i} className="h-[148px] rounded-2xl" />)}
            </div>
          ) : rows.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted">Belum ada data RW.</p>
          ) : (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {rows.map((r, i) => <Tile key={r.rw} r={r} metric={metric} delay={i * 40} />)}
            </div>
          )}
          <div className="mt-5"><Legend metric={metric} /></div>
        </div>
      </Card>

      <div className="mt-5 grid items-start gap-5 lg:grid-cols-[1fr_1.4fr]">
        <Callout tone="neutral" icon="eyeOff" title="Mengapa ada kotak berarsir?">
          Untuk melindungi privasi, jumlah 1–4 orang tidak pernah ditampilkan (ambang minimum 5). Di RW dengan sedikit warga, angka tersebut
          bisa membuat seseorang dikenali. Persentase juga tidak dihitung bila pembilang atau penyebutnya terlalu kecil.
          {hiddenCount > 0 && <> Saat ini <strong className="font-semibold">{hiddenCount} RW</strong> memiliki angka yang disembunyikan.</>}
        </Callout>
        <Card className="pb-5">
          <CardHeader title="Tabel per RW" icon="table" description="Nilai yang sama dengan peta, dalam bentuk tabel." />
          <div className="px-5 pt-4 sm:px-6">
            {q.data ? (
              <DataTable
                caption="Statistik per RW"
                head={['RW', 'Terdaftar', 'Terskrining', 'Terbuka', '% TD tinggi']}
                rows={rows.map((r) => [
                  `RW ${r.rw}`,
                  <CountCell key="a" value={r.registered} />,
                  <CountCell key="b" value={r.screened} />,
                  <CountCell key="c" value={r.followUpOpen} />,
                  r.elevatedBpPct === null ? <Suppressed key="d" /> : pct(r.elevatedBpPct),
                ])}
              />
            ) : <Skeleton className="h-48" />}
          </div>
        </Card>
      </div>
    </>
  )
}
