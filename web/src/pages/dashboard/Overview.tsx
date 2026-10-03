import { Link } from 'react-router'
import type { Count, Overview } from '../../api/types'
import { BarChart, ChartFrame, CountCell, DataTable, HBars, StackedBar } from '../../components/charts'
import { Icon, type IconName } from '../../components/Icon'
import { Button, Card, CardHeader, CountUp, ErrorState, PageHeader, Skeleton, Suppressed, cx } from '../../components/ui'
import { RISK_LEVELS, type Tone } from '../../lib/domain'
import { dateTime, monthLabel, monthLong, num, pct, relative } from '../../lib/format'
import { useOverview } from '../../lib/hooks'

function Kpi({ icon, label, value, sub, tone = 'primary', to, delay = 0 }: {
  icon: IconName; label: string; value: Count | undefined; sub?: React.ReactNode; tone?: Tone; to?: string; delay?: number
}) {
  const iconTone: Record<string, string> = {
    primary: 'bg-primary-soft text-accent', green: 'bg-green-bg text-green-fg', orange: 'bg-orange-bg text-orange-fg', red: 'bg-red-bg text-red-fg',
  }
  const body = (
    <>
      <div className="flex items-start justify-between gap-2">
        <p className="text-[0.8125rem] leading-snug font-semibold text-muted">{label}</p>
        <span className={cx('inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl', iconTone[tone] ?? iconTone.primary)}>
          <Icon name={icon} size={18} />
        </span>
      </div>
      <div className="mt-2 text-[1.7rem] leading-none sm:text-[2rem] font-extrabold tracking-tight text-fg tabular">
        {value === undefined ? <Skeleton className="h-8 w-20" /> : value === null ? <Suppressed className="text-base" /> : <CountUp value={value} />}
      </div>
      {sub && <p className="mt-2.5 text-xs leading-snug text-muted">{sub}</p>}
    </>
  )
  const cls = 'block rounded-2xl border border-line bg-surface p-4 shadow-soft animate-fade-up sm:p-5'
  return to ? (
    <Link to={to} className={cx(cls, 'transition-[box-shadow,border-color] hover:border-primary/40 hover:shadow-card')} style={{ animationDelay: `${delay}ms` }}>{body}</Link>
  ) : (
    <div className={cls} style={{ animationDelay: `${delay}ms` }}>{body}</div>
  )
}

export function OverviewSkeleton() {
  return (
    <div className="space-y-5" role="status" aria-label="Memuat ringkasan">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[132px] rounded-2xl" />)}
      </div>
      <div className="grid gap-5 lg:grid-cols-12">
        <Skeleton className="h-[300px] rounded-2xl lg:col-span-7" />
        <Skeleton className="h-[300px] rounded-2xl lg:col-span-5" />
      </div>
      <Skeleton className="h-[320px] rounded-2xl" />
    </div>
  )
}

function ratio(a: Count | undefined, b: Count | undefined) {
  return typeof a === 'number' && typeof b === 'number' && b > 0 ? a / b : null
}

function AttentionNote({ d }: { d: Overview }) {
  const hi = d.levels.HIGHER_MONITORING
  const med = d.levels.MEDICAL_FOLLOW_UP
  if (typeof hi !== 'number' || typeof med !== 'number') return null
  const total = RISK_LEVELS.reduce((a, l) => a + (d.levels[l.key] ?? 0), 0)
  return (
    <div className="mt-4 flex items-start gap-3 rounded-xl bg-orange-bg p-3.5 text-sm">
      <Icon name="info" size={17} className="mt-0.5 shrink-0 text-orange-fg" />
      <p className="leading-relaxed text-fg-2">
        <strong className="font-semibold text-fg">{num(hi + med)} warga ({total ? pct((hi + med) / total) : '—'})</strong> berada pada kategori
        “Perlu Pemantauan Lebih” atau “Perlu Tindak Lanjut Medis”. Pastikan mereka masuk registri tindak lanjut dan jadwal Posyandu berikutnya.
      </p>
    </div>
  )
}

function OverviewBody({ d }: { d: Overview }) {
  const coverage = ratio(d.screened30d, d.registered)
  const bpTotal = [d.bp.normal, d.bp.elevated, d.bp.high].reduce<number>((a, v) => a + (v ?? 0), 0)
  const smokersShare = ratio(d.smokers, d.registered)
  const months = d.byMonth.map((m) => ({ key: m.month, label: monthLabel(m.month, false), fullLabel: monthLong(m.month), value: m.screened }))
  const anyMonthSuppressed = d.byMonth.some((m) => m.screened === null)
  const recent = d.byMonth.slice(-3).reduce((a, m) => a + (m.screened ?? 0), 0)

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <Kpi icon="users" label="Warga terdaftar" value={d.registered} sub="Akun warga yang menyetujui sinkronisasi" />
        <Kpi icon="heartPulse" label="Terskrining 30 hari" value={d.screened30d} tone="green" delay={60}
          sub={coverage !== null ? <><strong className="font-semibold text-fg-2">{pct(coverage)}</strong> dari warga terdaftar</> : 'Pengukuran di Posyandu atau kunjungan'} />
        <Kpi icon="clipboard" label="Tindak lanjut terbuka" value={d.followUpOpen} tone="orange" delay={120} to="/dasbor/tindak-lanjut"
          sub={<span className="inline-flex items-center gap-1">Buka registri <Icon name="arrowRight" size={12} /></span>} />
        <Kpi icon="clock" label="Melewati jadwal" value={d.followUpOverdue} tone="red" delay={180} to="/dasbor/tindak-lanjut"
          sub="Tindak lanjut yang tanggalnya sudah lewat" />
      </div>

      <div className="grid gap-5 lg:grid-cols-12">
        <Card className="pb-5 lg:col-span-7 animate-fade-up [animation-delay:120ms]">
          <CardHeader title="Sebaran profil SEHATI" icon="heart" description="Kategori pemantauan warga terdaftar berdasarkan data terakhir — bukan diagnosis." />
          <div className="px-5 pt-5 sm:px-6">
            <StackedBar
              ariaLabel="Sebaran profil SEHATI"
              segments={RISK_LEVELS.map((l) => ({ key: l.key, label: l.label, tone: l.tone, value: d.levels[l.key] ?? 0 }))}
            />
            <AttentionNote d={d} />
          </div>
        </Card>

        <Card className="pb-5 lg:col-span-5 animate-fade-up [animation-delay:180ms]">
          <CardHeader title="Tekanan darah terakhir" icon="activity" description={`${num(bpTotal)} warga dengan pengukuran, menurut ambang klinis aktif.`} />
          <div className="px-5 pt-5 sm:px-6">
            <HBars
              max={Math.max(1, d.bp.normal ?? 0, d.bp.elevated ?? 0, d.bp.high ?? 0)}
              items={[
                { key: 'normal', label: 'Normal', value: d.bp.normal, tone: 'green' },
                { key: 'elevated', label: 'Normal-tinggi', value: d.bp.elevated, tone: 'yellow' },
                { key: 'high', label: 'Tinggi', hint: 'perlu konfirmasi', value: d.bp.high, tone: 'red' },
              ]}
            />
            <div className="mt-5 flex items-center gap-3 rounded-xl bg-surface-2 p-3.5">
              <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-surface text-fg-2 ring-1 ring-line"><Icon name="cigarette" size={18} /></span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-fg">Perokok aktif</p>
                <p className="text-xs text-muted">{smokersShare !== null ? `${pct(smokersShare)} dari warga terdaftar` : 'Dari asesmen warga'}</p>
              </div>
              <p className="text-xl font-extrabold text-fg tabular">{d.smokers === null ? <Suppressed /> : <CountUp value={d.smokers} />}</p>
            </div>
          </div>
        </Card>
      </div>

      <Card className="pb-5 animate-fade-up [animation-delay:240ms]">
        <CardHeader
          title="Skrining per bulan"
          icon="bars"
          description={<>Jumlah warga yang diukur setiap bulan · 3 bulan terakhir: <strong className="font-semibold text-fg-2">{num(recent)}</strong>{anyMonthSuppressed && <> · bulan berarsir berjumlah 1–4</>}</>}
        />
        <div className="px-5 pt-3 sm:px-6">
          <ChartFrame
            label="skrining per bulan"
            chart={<BarChart data={months} ariaLabel="Grafik batang jumlah warga terskrining per bulan, 12 bulan terakhir" />}
            table={<DataTable caption="Skrining per bulan" head={['Bulan', 'Terskrining']} rows={d.byMonth.map((m) => [monthLong(m.month), <CountCell key={m.month} value={m.screened} />])} />}
          />
        </div>
      </Card>

      <p className="flex items-start gap-2 text-xs leading-relaxed text-muted">
        <Icon name="info" size={14} className="mt-px shrink-0" />
        Profil SEHATI adalah hasil pemantauan berbasis data yang dimasukkan dan bukan diagnosis medis. Angka 1–4 ditampilkan sebagai “&lt; 5” untuk melindungi privasi.
      </p>
    </div>
  )
}

export default function OverviewPage() {
  const q = useOverview()
  return (
    <>
      <PageHeader
        eyebrow="Pemantauan"
        title="Ringkasan desa"
        description="Gambaran cakupan skrining dan faktor risiko jantung koroner warga — semua dalam angka agregat."
        actions={
          <>
            {q.data && (
              <span className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1.5 text-xs text-muted" title={dateTime(q.data.updatedAt)}>
                <span className="h-1.5 w-1.5 rounded-full bg-wellness" aria-hidden="true" />
                Data per {dateTime(q.data.updatedAt)} · {relative(q.data.updatedAt)}
              </span>
            )}
            <Button variant="secondary" size="sm" icon="refresh" onClick={q.reload} loading={q.loading && !q.initial}>Muat ulang</Button>
          </>
        }
      />
      {q.data ? <OverviewBody d={q.data} /> : q.error ? <Card><ErrorState error={q.error} onRetry={q.reload} /></Card> : <OverviewSkeleton />}
    </>
  )
}
