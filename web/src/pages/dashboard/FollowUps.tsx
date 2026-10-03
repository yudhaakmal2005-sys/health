import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { endpoints } from '../../api/endpoints'
import type { Cadre, FollowUp, FollowUpFilter } from '../../api/types'
import { ApiError } from '../../api/client'
import { Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { useToast } from '../../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorState, Field, Input, PageHeader, Segmented, Select, SkeletonRows, cx } from '../../components/ui'
import { priorityInfo, statusInfo, typeLabel } from '../../lib/domain'
import { dateShort, daysUntil, isoDate, parseIsoDate } from '../../lib/format'
import { KEYS, useCadres, useFollowups, useRw } from '../../lib/hooks'
import { invalidate, setQueryData } from '../../lib/query'

const STATUS_OPTS: { value: FollowUpFilter; label: string }[] = [
  { value: 'OPEN', label: 'Terbuka' },
  { value: 'SCHEDULED', label: 'Terjadwal' },
  { value: 'DONE', label: 'Selesai' },
  { value: 'ALL', label: 'Semua' },
]

function DueLabel({ f }: { f: FollowUp }) {
  if (!f.dueAt) return <span className="text-sm whitespace-nowrap text-muted">Belum dijadwalkan</span>
  const closed = f.status === 'DONE' || f.status === 'CANCELLED'
  const d = daysUntil(f.dueAt)
  return (
    <span className="flex flex-col">
      <span className="text-sm font-medium text-fg tabular">{dateShort(f.dueAt)}</span>
      {!closed && (
        <span className={cx('text-xs font-semibold', d < 0 ? 'text-red-fg' : d <= 2 ? 'text-orange-fg' : 'text-muted')}>
          {d < 0 ? `Lewat ${-d} hari` : d === 0 ? 'Hari ini' : d === 1 ? 'Besok' : `${d} hari lagi`}
        </span>
      )}
    </span>
  )
}

function CadreSelect({ f, cadres, onAssign, busy }: { f: FollowUp; cadres: Cadre[] | undefined; onAssign: (f: FollowUp, id: string | null) => void; busy: boolean }) {
  const active = (cadres ?? []).filter((c) => c.active)
  const same = active.filter((c) => c.rw === f.rw)
  const other = active.filter((c) => c.rw !== f.rw)
  const assignedInactive = f.assignedCadreId && !active.some((c) => c.sehatiId === f.assignedCadreId)
  const closed = f.status === 'DONE' || f.status === 'CANCELLED'
  return (
    <Select
      aria-label={`Kader untuk ${f.sehatiId}`}
      value={f.assignedCadreId ?? ''}
      disabled={busy || !cadres || closed}
      onChange={(e) => onAssign(f, e.target.value || null)}
      className="w-full lg:min-w-[13.5rem]"
    >
      <option value="">— Belum ditugaskan —</option>
      {assignedInactive && <option value={f.assignedCadreId!} disabled>{f.assignedCadreName ?? f.assignedCadreId} (nonaktif)</option>}
      {same.length > 0 && (
        <optgroup label={`Kader RW ${f.rw}`}>
          {same.map((c) => <option key={c.sehatiId} value={c.sehatiId}>{c.fullName}</option>)}
        </optgroup>
      )}
      {other.length > 0 && (
        <optgroup label="Kader RW lain">
          {other.map((c) => <option key={c.sehatiId} value={c.sehatiId}>{c.fullName} · RW {c.rw}</option>)}
        </optgroup>
      )}
    </Select>
  )
}

function ScheduleDialog({ f, onClose, onSave, saving }: { f: FollowUp | null; onClose: () => void; onSave: (f: FollowUp, dueAt: number) => void; saving: boolean }) {
  const [date, setDate] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const today = isoDate(new Date())
  const [lastId, setLastId] = useState<string | null>(null)
  if (f && f.id !== lastId) {
    setLastId(f.id)
    setDate(f.dueAt ? isoDate(new Date(f.dueAt)) : today)
    setErr(null)
  }
  const submit = () => {
    if (!f) return
    if (!date) return setErr('Pilih tanggal.')
    if (date < today) return setErr('Tanggal tidak boleh sebelum hari ini.')
    const d = parseIsoDate(date)
    d.setHours(9, 0, 0, 0)
    onSave(f, d.getTime())
  }
  return (
    <Dialog
      open={!!f}
      onClose={onClose}
      title="Atur jadwal tindak lanjut"
      description={f ? `${f.sehatiId} · RW ${f.rw} · ${typeLabel(f.type)}` : undefined}
      icon="calendar"
      size="sm"
      footer={<><Button variant="secondary" onClick={onClose}>Batal</Button><Button onClick={submit} loading={saving}>Simpan jadwal</Button></>}
    >
      <form onSubmit={(e) => { e.preventDefault(); submit() }}>
        <Field label="Tanggal tindak lanjut" htmlFor="due" error={err} hint="Jadwal ikut tersinkron ke HP kader yang ditugaskan.">
          <Input id="due" type="date" min={today} value={date} onChange={(e) => { setDate(e.target.value); setErr(null) }} invalid={!!err} />
        </Field>
      </form>
    </Dialog>
  )
}

export default function FollowUpsPage() {
  const [params, setParams] = useSearchParams()
  const status = (STATUS_OPTS.some((o) => o.value === params.get('status')) ? params.get('status') : 'OPEN') as FollowUpFilter
  const rw = params.get('rw') ?? ''
  const [search, setSearch] = useState('')
  const [scheduling, setScheduling] = useState<FollowUp | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const toast = useToast()

  const q = useFollowups(status, rw)
  const cadres = useCadres()
  const rws = useRw()
  const key = KEYS.followups(status, rw)

  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v)
    else next.delete(k)
    setParams(next, { replace: true })
  }

  const rows = useMemo(() => {
    const s = search.trim().toUpperCase().replace(/\s/g, '')
    return (q.data ?? [])
      .filter((f) => !s || f.sehatiId.toUpperCase().includes(s))
      .sort((a, b) => b.priority - a.priority || (a.dueAt ?? Infinity) - (b.dueAt ?? Infinity))
  }, [q.data, search])

  async function assign(f: FollowUp, cadreId: string | null) {
    const c = cadres.data?.find((x) => x.sehatiId === cadreId)
    setBusy(f.id)
    const undo = setQueryData<FollowUp[]>(key, (xs) =>
      (xs ?? []).map((x) => (x.id === f.id ? { ...x, assignedCadreId: cadreId, assignedCadreName: c?.fullName ?? null } : x)))
    try {
      await endpoints.assignFollowup(f.id, cadreId)
      toast.success(cadreId ? 'Kader ditugaskan' : 'Penugasan dilepas', cadreId ? `${f.sehatiId} → ${c?.fullName ?? cadreId}` : f.sehatiId)
      invalidate('admin/followups')
    } catch (e) {
      undo()
      toast.error('Penugasan gagal', e instanceof ApiError ? e.message : undefined)
    } finally {
      setBusy(null)
    }
  }

  async function schedule(f: FollowUp, dueAt: number) {
    setBusy(f.id)
    const undo = setQueryData<FollowUp[]>(key, (xs) => (xs ?? []).map((x) => (x.id === f.id ? { ...x, dueAt } : x)))
    try {
      await endpoints.scheduleFollowup(f.id, dueAt)
      toast.success('Jadwal disimpan', `${f.sehatiId} · ${dateShort(dueAt)}`)
      setScheduling(null)
      invalidate('admin/followups')
      invalidate(KEYS.overview)
    } catch (e) {
      undo()
      toast.error('Jadwal gagal disimpan', e instanceof ApiError ? e.message : undefined)
    } finally {
      setBusy(null)
    }
  }

  const rwOptions = rws.data?.map((r) => r.rw) ?? Array.from(new Set((q.data ?? []).map((f) => f.rw))).sort()

  return (
    <>
      <PageHeader
        eyebrow="Pemantauan"
        title="Registri tindak lanjut"
        description="Tugaskan kader dan atur jadwal. Daftar ini sengaja tanpa nama warga dan tanpa nilai pemeriksaan; detail ada di HP kader."
        actions={<Button variant="secondary" size="sm" icon="refresh" onClick={q.reload} loading={q.loading && !q.initial}>Muat ulang</Button>}
      />

      <Card>
        <div className="flex flex-col gap-3 border-b border-line p-4 sm:p-5 xl:flex-row xl:items-center">
          <Segmented label="Filter status" value={status} onChange={(v) => setParam('status', v === 'OPEN' ? '' : v)} options={STATUS_OPTS} />
          <div className="flex flex-1 flex-col gap-3 sm:flex-row xl:justify-end">
            <Select aria-label="Filter RW" value={rw} onChange={(e) => setParam('rw', e.target.value)} className="sm:w-40">
              <option value="">Semua RW</option>
              {rwOptions.map((r) => <option key={r} value={r}>RW {r}</option>)}
            </Select>
            <div className="relative sm:w-64">
              <Icon name="search" size={17} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-muted" />
              <Input aria-label="Cari SEHATI ID" placeholder="Cari SEHATI ID" value={search} onChange={(e) => setSearch(e.target.value)} className="pl-10" />
            </div>
          </div>
        </div>

        {q.error && !q.data ? (
          <ErrorState error={q.error} onRetry={q.reload} />
        ) : q.initial ? (
          <SkeletonRows rows={7} />
        ) : rows.length === 0 ? (
          <EmptyState
            icon={search ? 'search' : 'checkCircle'}
            title={search ? 'SEHATI ID tidak ditemukan' : status === 'OPEN' ? 'Tidak ada tindak lanjut terbuka' : 'Belum ada data'}
            action={(search || rw || status !== 'OPEN') ? <Button variant="secondary" onClick={() => { setSearch(''); setParams(new URLSearchParams(), { replace: true }) }}>Hapus filter</Button> : undefined}
          >
            {search ? 'Periksa kembali penulisan ID atau ubah filter status dan RW.' : 'Semua warga yang perlu perhatian sudah ditangani. Terima kasih kepada para kader!'}
          </EmptyState>
        ) : (
          <>
            {/* Tabel desktop */}
            <div className="hidden overflow-x-auto lg:block">
              <table className="w-full text-sm">
                <caption className="sr-only">Daftar tindak lanjut</caption>
                <thead>
                  <tr className="border-b border-line text-left text-xs font-semibold text-muted">
                    <th scope="col" className="px-5 py-3">Prioritas</th>
                    <th scope="col" className="px-3 py-3">Warga</th>
                    <th scope="col" className="px-3 py-3">Tindak lanjut</th>
                    <th scope="col" className="px-3 py-3">Status</th>
                    <th scope="col" className="px-3 py-3">Jadwal</th>
                    <th scope="col" className="px-3 py-3">Kader</th>
                    <th scope="col" className="px-5 py-3 text-right"><span className="sr-only">Aksi</span></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((f) => {
                    const p = priorityInfo(f.priority)
                    const s = statusInfo(f.status)
                    return (
                      <tr key={f.id} className={cx('border-b border-line last:border-0 transition-colors hover:bg-surface-2/60', busy === f.id && 'opacity-70')}>
                        <td className="px-5 py-3"><Badge tone={p.tone}>{p.label}</Badge></td>
                        <td className="px-3 py-3">
                          <span className="font-semibold tracking-wide whitespace-nowrap text-fg">{f.sehatiId}</span>
                          <span className="block text-xs text-muted">RW {f.rw}</span>
                        </td>
                        <td className="max-w-[18rem] px-3 py-3">
                          <span className="font-medium text-fg">{typeLabel(f.type)}</span>
                          <span className="block truncate text-xs text-muted" title={f.reason}>{f.reason}</span>
                        </td>
                        <td className="px-3 py-3"><Badge tone={s.tone}>{s.label}</Badge></td>
                        <td className="px-3 py-3 whitespace-nowrap"><DueLabel f={f} /></td>
                        <td className="px-3 py-2"><CadreSelect f={f} cadres={cadres.data} onAssign={assign} busy={busy === f.id} /></td>
                        <td className="px-5 py-3 text-right">
                          <Button variant="ghost" size="sm" icon="calendar" onClick={() => setScheduling(f)} disabled={f.status === 'DONE' || f.status === 'CANCELLED'}>
                            Jadwal
                          </Button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            {/* Kartu seluler */}
            <ul className="divide-y divide-line lg:hidden">
              {rows.map((f) => {
                const p = priorityInfo(f.priority)
                const s = statusInfo(f.status)
                return (
                  <li key={f.id} className="space-y-3 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-semibold tracking-wide text-fg">{f.sehatiId} <span className="font-normal text-muted">· RW {f.rw}</span></p>
                        <p className="mt-0.5 text-sm text-fg-2">{typeLabel(f.type)}</p>
                        <p className="text-xs text-muted">{f.reason}</p>
                      </div>
                      <Badge tone={p.tone}>{p.label}</Badge>
                    </div>
                    <div className="flex flex-wrap items-center gap-3">
                      <Badge tone={s.tone}>{s.label}</Badge>
                      <DueLabel f={f} />
                    </div>
                    <div className="flex gap-2">
                      <div className="flex-1"><CadreSelect f={f} cadres={cadres.data} onAssign={assign} busy={busy === f.id} /></div>
                      <Button variant="secondary" icon="calendar" onClick={() => setScheduling(f)} disabled={f.status === 'DONE' || f.status === 'CANCELLED'} className="h-11">
                        Jadwal
                      </Button>
                    </div>
                  </li>
                )
              })}
            </ul>
            <p className="border-t border-line px-5 py-3 text-xs text-muted">{rows.length} tindak lanjut ditampilkan · urut prioritas lalu jadwal terdekat</p>
          </>
        )}
      </Card>

      <ScheduleDialog f={scheduling} onClose={() => setScheduling(null)} onSave={schedule} saving={!!scheduling && busy === scheduling.id} />
    </>
  )
}
