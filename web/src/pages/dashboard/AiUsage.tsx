import { useState } from 'react'
import type { AiUsageDay } from '../../api/types'
import { BarChart, ChartFrame, CountCell, DataTable } from '../../components/charts'
import { Icon, type IconName } from '../../components/Icon'
import { Button, Callout, Card, CardHeader, CountUp, ErrorState, PageHeader, Segmented, Skeleton, Suppressed, cx } from '../../components/ui'
import { dateLong, dayShort, num, sum } from '../../lib/format'
import { useAiUsage } from '../../lib/hooks'

function Stat({ icon, label, value, suppressed, sub, tone = 'primary' }: { icon: IconName; label: string; value: number; suppressed?: boolean; sub: string; tone?: 'primary' | 'red' | 'neutral' }) {
  return (
    <div className="rounded-2xl border border-line bg-surface p-5 shadow-soft animate-fade-up">
      <div className="flex items-center justify-between">
        <p className="text-[0.8125rem] font-semibold text-muted">{label}</p>
        <span className={cx('inline-flex h-9 w-9 items-center justify-center rounded-xl', tone === 'red' ? 'bg-red-bg text-red-fg' : tone === 'neutral' ? 'bg-surface-2 text-fg-2' : 'bg-primary-soft text-accent')}>
          <Icon name={icon} size={18} />
        </span>
      </div>
      <p className="mt-2 text-[2rem] leading-none font-extrabold tracking-tight text-fg tabular">
        <CountUp value={value} />{suppressed && <span className="ml-1 align-top text-base text-muted" title="Belum termasuk hari yang disembunyikan">+</span>}
      </p>
      <p className="mt-2.5 text-xs text-muted">{sub}</p>
    </div>
  )
}

function Body({ data, days }: { data: AiUsageDay[]; days: number }) {
  const msgs = sum(data.map((d) => d.messages))
  const users = data.map((d) => d.users).filter((v): v is number => v !== null)
  const em = sum(data.map((d) => d.emergencies))
  const avgUsers = users.length ? Math.round(users.reduce((a, b) => a + b, 0) / users.length) : 0
  const bars = data.map((d) => ({ key: d.day, label: dayShort(d.day), fullLabel: dateLong(d.day), value: d.messages }))
  const emBars = data.map((d) => ({ key: d.day, label: dayShort(d.day), fullLabel: dateLong(d.day), value: d.emergencies }))
  const emSuppressedDays = data.filter((d) => d.emergencies === null).length

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-3">
        <Stat icon="message" label={`Pesan (${days} hari)`} value={msgs.total} suppressed={msgs.anySuppressed} sub={`Rata-rata ${num(Math.round(msgs.total / Math.max(1, data.length)))} pesan per hari`} />
        <Stat icon="users" label="Pengguna per hari" value={avgUsers} sub="Rata-rata akun unik yang bertanya" tone="neutral" />
        <Stat icon="alert" label="Pertanyaan darurat" value={em.total} suppressed={em.anySuppressed} tone="red"
          sub={emSuppressedDays ? `${emSuppressedDays} hari berjumlah 1–4 tidak ikut dijumlahkan` : 'Dijawab dengan anjuran 119/112'} />
      </div>

      <Card className="pb-5">
        <CardHeader
          title="Pesan dan pengguna per hari"
          icon="bars"
          description={
            <span className="flex flex-wrap items-center gap-x-4 gap-y-1">
              <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-primary" aria-hidden="true" />Pesan (batang)</span>
              <span className="inline-flex items-center gap-1.5"><span className="h-0.5 w-4 rounded bg-fg-2" aria-hidden="true" />Pengguna (garis)</span>
              <span className="inline-flex items-center gap-1.5"><span className="hatch h-2.5 w-2.5 rounded-sm" aria-hidden="true" />&lt; 5</span>
            </span>
          }
        />
        <div className="px-5 pt-3 sm:px-6">
          <ChartFrame
            label="pemakaian AI"
            chart={<BarChart data={bars} unit="pesan" line={{ label: 'Pengguna', values: data.map((d) => d.users) }} ariaLabel={`Grafik pesan dan pengguna Tanya SEHATI per hari, ${days} hari terakhir`} height={240} />}
            table={<DataTable caption="Pemakaian AI per hari" head={['Tanggal', 'Pesan', 'Pengguna', 'Darurat']} rows={[...data].reverse().map((d) => [dateLong(d.day), <CountCell key="m" value={d.messages} />, <CountCell key="u" value={d.users} />, <CountCell key="e" value={d.emergencies} />])} />}
          />
        </div>
      </Card>

      <div className="grid items-start gap-5 lg:grid-cols-[1.5fr_1fr]">
        <Card className="pb-5">
          <CardHeader title="Pertanyaan bergejala darurat" icon="alert" description="Server mendeteksi kata gejala darurat sebelum memanggil AI; pengguna langsung diarahkan ke 119/112." />
          <div className="px-5 pt-3 sm:px-6">
            <BarChart data={emBars} unit="pertanyaan" height={150} ariaLabel="Grafik pertanyaan bergejala darurat per hari" />
          </div>
        </Card>
        <Callout tone="primary" icon="shield" title="Isi percakapan tidak disimpan">
          Server hanya mencatat hitungan harian. Pertanyaan, jawaban, dan konteks pengguna tidak disimpan maupun ditulis ke log.
          Batas pemakaian: 40 pesan per hari per akun. Hari dengan jumlah 1–4 ditampilkan berarsir (<Suppressed />).
        </Callout>
      </div>
    </div>
  )
}

export default function AiUsagePage() {
  const [days, setDays] = useState(30)
  const q = useAiUsage(days)
  return (
    <>
      <PageHeader
        eyebrow="Laporan & audit"
        title="Pemakaian Tanya SEHATI"
        description="Seberapa sering warga dan kader bertanya ke asisten AI, dan berapa pertanyaan yang menunjukkan gejala darurat."
        actions={
          <>
            <Segmented label="Rentang waktu" size="sm" value={String(days)} onChange={(v) => setDays(Number(v))}
              options={[{ value: '7', label: '7 hari' }, { value: '30', label: '30 hari' }, { value: '90', label: '90 hari' }]} />
            <Button variant="secondary" size="sm" icon="refresh" onClick={q.reload} loading={q.loading && !q.initial}>Muat ulang</Button>
          </>
        }
      />
      {q.data ? <Body data={q.data} days={days} /> : q.error ? <Card><ErrorState error={q.error} onRetry={q.reload} /></Card> : (
        <div className="space-y-5" role="status" aria-label="Memuat pemakaian AI">
          <div className="grid gap-4 sm:grid-cols-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-[132px] rounded-2xl" />)}</div>
          <Skeleton className="h-[330px] rounded-2xl" />
        </div>
      )}
    </>
  )
}
