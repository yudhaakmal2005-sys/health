import { useState } from 'react'
import { endpoints } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { Count, Overview, RwStat } from '../../api/types'
import { Icon } from '../../components/Icon'
import { LogoMark } from '../../components/Logo'
import { useToast } from '../../components/Toast'
import { Button, Card, ErrorState, PageHeader, Skeleton, Suppressed } from '../../components/ui'
import { RISK_LEVELS } from '../../lib/domain'
import { count, dateLong, dateTime, isoDate, monthLong, pct } from '../../lib/format'
import { useOverview, useRw } from '../../lib/hooks'
import { useAuth } from '../../auth/AuthContext'
import { site } from '../../site.config'

function C({ v }: { v: Count | undefined }) {
  return v === null ? <Suppressed /> : <>{count(v)}</>
}

function Th({ children, right }: { children: React.ReactNode; right?: boolean }) {
  return <th scope="col" className={`border-b border-line-strong px-3 py-2 text-xs font-bold text-fg-2 ${right ? 'text-right' : 'text-left'}`}>{children}</th>
}

function Report({ o, rw }: { o: Overview; rw: RwStat[] }) {
  const { session } = useAuth()
  const share = (v: Count) => (typeof v === 'number' && typeof o.registered === 'number' && o.registered > 0 ? pct(v / o.registered) : '—')
  const lastSix = o.byMonth.slice(-6)
  return (
    <article className="mx-auto max-w-[820px] rounded-2xl border border-line bg-surface p-6 shadow-soft sm:p-10 print:max-w-none print:rounded-none print:border-0 print:p-0 print:shadow-none">
      <header className="flex items-start justify-between gap-6 border-b-2 border-primary pb-5">
        <div>
          <p className="text-xs font-bold tracking-[0.14em] text-accent uppercase">Laporan ringkas · data agregat</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-fg">Pemantauan Pencegahan Jantung Koroner</h2>
          <p className="mt-1 text-sm text-fg-2">{site.village} · {site.district}</p>
        </div>
        <LogoMark size={52} />
      </header>

      <dl className="mt-5 grid gap-x-8 gap-y-1 text-sm sm:grid-cols-2">
        <div className="flex justify-between gap-4"><dt className="text-muted">Data per</dt><dd className="font-semibold text-fg">{dateTime(o.updatedAt)}</dd></div>
        <div className="flex justify-between gap-4"><dt className="text-muted">Dicetak</dt><dd className="font-semibold text-fg">{dateLong(isoDate(new Date()))}</dd></div>
        <div className="flex justify-between gap-4"><dt className="text-muted">Disusun oleh</dt><dd className="font-semibold text-fg">{session?.user.fullName}</dd></div>
        <div className="flex justify-between gap-4"><dt className="text-muted">Sumber</dt><dd className="font-semibold text-fg">SEHATI (sinkronisasi server)</dd></div>
      </dl>

      <section className="mt-8 print-avoid">
        <h3 className="text-sm font-bold text-fg">1. Indikator utama</h3>
        <table className="mt-2 w-full text-sm">
          <thead><tr><Th>Indikator</Th><Th right>Jumlah</Th><Th right>% terdaftar</Th></tr></thead>
          <tbody className="[&_td]:border-b [&_td]:border-line [&_td]:px-3 [&_td]:py-2">
            <tr><td>Warga terdaftar</td><td className="text-right tabular"><C v={o.registered} /></td><td className="text-right text-muted">—</td></tr>
            <tr><td>Terskrining 30 hari terakhir</td><td className="text-right tabular"><C v={o.screened30d} /></td><td className="text-right tabular">{share(o.screened30d)}</td></tr>
            <tr><td>Perokok aktif</td><td className="text-right tabular"><C v={o.smokers} /></td><td className="text-right tabular">{share(o.smokers)}</td></tr>
            <tr><td>Tindak lanjut terbuka</td><td className="text-right tabular"><C v={o.followUpOpen} /></td><td className="text-right text-muted">—</td></tr>
            <tr><td>Tindak lanjut melewati jadwal</td><td className="text-right tabular"><C v={o.followUpOverdue} /></td><td className="text-right text-muted">—</td></tr>
          </tbody>
        </table>
      </section>

      <div className="mt-8 grid gap-8 sm:grid-cols-2">
        <section className="print-avoid">
          <h3 className="text-sm font-bold text-fg">2. Sebaran profil SEHATI</h3>
          <table className="mt-2 w-full text-sm">
            <thead><tr><Th>Kategori pemantauan</Th><Th right>Warga</Th></tr></thead>
            <tbody className="[&_td]:border-b [&_td]:border-line [&_td]:px-3 [&_td]:py-2">
              {RISK_LEVELS.map((l) => <tr key={l.key}><td>{l.label}</td><td className="text-right tabular"><C v={o.levels[l.key] ?? 0} /></td></tr>)}
            </tbody>
          </table>
        </section>
        <section className="print-avoid">
          <h3 className="text-sm font-bold text-fg">3. Tekanan darah terakhir</h3>
          <table className="mt-2 w-full text-sm">
            <thead><tr><Th>Kategori</Th><Th right>Warga</Th></tr></thead>
            <tbody className="[&_td]:border-b [&_td]:border-line [&_td]:px-3 [&_td]:py-2">
              <tr><td>Normal</td><td className="text-right tabular"><C v={o.bp.normal} /></td></tr>
              <tr><td>Normal-tinggi</td><td className="text-right tabular"><C v={o.bp.elevated} /></td></tr>
              <tr><td>Tinggi (perlu konfirmasi)</td><td className="text-right tabular"><C v={o.bp.high} /></td></tr>
            </tbody>
          </table>
        </section>
      </div>

      <section className="mt-8 print-avoid">
        <h3 className="text-sm font-bold text-fg">4. Rekap per RW</h3>
        <div className="overflow-x-auto">
          <table className="mt-2 w-full text-sm">
            <thead><tr><Th>RW</Th><Th right>Terdaftar</Th><Th right>Terskrining</Th><Th right>Tindak lanjut terbuka</Th><Th right>% TD tinggi</Th></tr></thead>
            <tbody className="[&_td]:border-b [&_td]:border-line [&_td]:px-3 [&_td]:py-2">
              {rw.map((r) => (
                <tr key={r.rw}>
                  <td className="font-semibold">RW {r.rw}</td>
                  <td className="text-right tabular"><C v={r.registered} /></td>
                  <td className="text-right tabular"><C v={r.screened} /></td>
                  <td className="text-right tabular"><C v={r.followUpOpen} /></td>
                  <td className="text-right tabular">{r.elevatedBpPct === null ? <Suppressed /> : pct(r.elevatedBpPct)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mt-8 print-avoid">
        <h3 className="text-sm font-bold text-fg">5. Skrining enam bulan terakhir</h3>
        <table className="mt-2 w-full text-sm">
          <thead><tr>{lastSix.map((m) => <Th key={m.month} right>{monthLong(m.month).replace(/ \d{4}$/, '')}</Th>)}</tr></thead>
          <tbody><tr className="[&_td]:border-b [&_td]:border-line [&_td]:px-3 [&_td]:py-2">{lastSix.map((m) => <td key={m.month} className="text-right tabular"><C v={m.screened} /></td>)}</tr></tbody>
        </table>
      </section>

      <section className="mt-8 rounded-xl bg-surface-2 p-4 text-xs leading-relaxed text-fg-2 print-avoid">
        <p className="font-bold text-fg">Catatan</p>
        <ul className="mt-1 list-disc space-y-1 pl-4">
          <li>Semua angka adalah agregat. Jumlah 1–4 ditampilkan sebagai “&lt; 5” untuk melindungi privasi warga.</li>
          <li>Profil SEHATI adalah kategori pemantauan berbasis data yang dimasukkan, bukan diagnosis medis.</li>
          <li>Tekanan darah “tinggi” dari satu kali pengukuran adalah data skrining dan perlu dikonfirmasi tenaga kesehatan.</li>
        </ul>
      </section>

      <footer className="mt-12 grid grid-cols-2 gap-10 text-sm text-fg-2 print-avoid">
        {['Mengetahui,\nKepala Puskesmas', 'Penanggung jawab\nProgram PTM'].map((t) => (
          <div key={t} className="text-center">
            <p className="whitespace-pre-line">{t}</p>
            <div className="mx-auto mt-16 w-48 border-b border-fg-2" />
            <p className="mt-1 text-xs text-muted">Nama &amp; NIP</p>
          </div>
        ))}
      </footer>
    </article>
  )
}

export default function ReportsPage() {
  const o = useOverview()
  const rw = useRw()
  const toast = useToast()
  const [downloading, setDownloading] = useState(false)

  async function downloadCsv() {
    setDownloading(true)
    try {
      const blob = await endpoints.summaryCsv()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `sehati-ringkasan-${isoDate(new Date())}.csv`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
      toast.success('CSV diunduh', 'Berisi agregat per RW tanpa data perorangan.')
    } catch (e) {
      toast.error('CSV gagal diunduh', e instanceof ApiError ? e.message : undefined)
    } finally {
      setDownloading(false)
    }
  }

  const err = o.error ?? rw.error
  return (
    <>
      <div className="no-print">
      <PageHeader
        eyebrow="Laporan & audit"
        title="Laporan"
        description="Ringkasan siap cetak untuk rapat lintas sektor atau laporan bulanan. Unduh CSV untuk diolah di spreadsheet."
        actions={
          <>
            <Button variant="secondary" icon="download" onClick={() => void downloadCsv()} loading={downloading}>Unduh CSV</Button>
            <Button icon="printer" onClick={() => window.print()} disabled={!o.data || !rw.data}>Cetak / simpan PDF</Button>
          </>
        }
      />
      </div>
      <p className="no-print mb-5 flex items-center gap-2 text-xs text-muted">
        <Icon name="info" size={14} /> Tampilan di bawah sudah diatur untuk kertas A4. Pilih “Simpan sebagai PDF” pada dialog cetak untuk berkas digital.
      </p>
      {o.data && rw.data ? (
        <Report o={o.data} rw={rw.data} />
      ) : err ? (
        <Card><ErrorState error={err} onRetry={() => { o.reload(); rw.reload() }} /></Card>
      ) : (
        <Skeleton className="mx-auto h-[900px] max-w-[820px] rounded-2xl" />
      )}
    </>
  )
}
