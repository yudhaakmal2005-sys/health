import { useMemo, useState } from 'react'
import { endpoints } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { PosyanduInput, PosyanduSchedule } from '../../api/types'
import { ConfirmDialog, Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { useToast } from '../../components/Toast'
import { Badge, Button, Callout, Card, EmptyState, ErrorState, Field, IconButton, Input, PageHeader, Select, Skeleton, Switch, Textarea, cx } from '../../components/ui'
import { dateLong, isoDate, monthLong, parseIsoDate } from '../../lib/format'
import { KEYS, usePosyandu, useRw } from '../../lib/hooks'
import { invalidate, setQueryData } from '../../lib/query'

const DAYS = ['Min', 'Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des']
const EMPTY: PosyanduInput = { rw: '', date: '', startTime: '08:00', endTime: '11:00', location: '', notes: '' }

type Errors = Partial<Record<keyof PosyanduInput | 'form', string>>

function validate(v: PosyanduInput, isNew: boolean): Errors {
  const e: Errors = {}
  if (!/^\d{2}$/.test(v.rw)) e.rw = 'Pilih RW.'
  if (!v.date) e.date = 'Pilih tanggal.'
  else if (isNew && v.date < isoDate(new Date())) e.date = 'Tanggal tidak boleh sebelum hari ini.'
  if (!v.startTime) e.startTime = 'Isi jam mulai.'
  if (!v.endTime) e.endTime = 'Isi jam selesai.'
  else if (v.startTime && v.endTime <= v.startTime) e.endTime = 'Jam selesai harus setelah jam mulai.'
  if (v.location.trim().length < 3) e.location = 'Tulis lokasi, mis. Balai RW 02.'
  if ((v.notes ?? '').length > 200) e.notes = 'Catatan maksimal 200 karakter.'
  return e
}

function ScheduleForm({ initial, open, onClose, rwOptions }: { initial: PosyanduSchedule | null; open: boolean; onClose: () => void; rwOptions: string[] }) {
  const toast = useToast()
  const isNew = !initial
  const [v, setV] = useState<PosyanduInput>(EMPTY)
  const [errors, setErrors] = useState<Errors>({})
  const [saving, setSaving] = useState(false)
  const [loadedFor, setLoadedFor] = useState<string | null | undefined>(undefined)
  const marker = open ? (initial?.id ?? 'new') : undefined
  if (marker !== loadedFor) {
    setLoadedFor(marker)
    if (open) {
      setV(initial ? { rw: initial.rw, date: initial.date, startTime: initial.startTime, endTime: initial.endTime, location: initial.location, notes: initial.notes ?? '' } : EMPTY)
      setErrors({})
    }
  }
  const set = <K extends keyof PosyanduInput>(k: K, val: PosyanduInput[K]) => {
    setV((x) => ({ ...x, [k]: val }))
    setErrors((e) => ({ ...e, [k]: undefined, form: undefined }))
  }

  async function submit() {
    const e = validate(v, isNew)
    setErrors(e)
    if (Object.keys(e).length) return
    const body: PosyanduInput = { ...v, location: v.location.trim(), notes: v.notes?.trim() ? v.notes.trim() : null }
    setSaving(true)
    let undo: (() => void) | undefined
    try {
      if (initial) {
        undo = setQueryData<PosyanduSchedule[]>(KEYS.posyandu, (xs) => (xs ?? []).map((x) => (x.id === initial.id ? { ...x, ...body } : x)))
        await endpoints.updatePosyandu(initial.id, body)
        toast.success('Jadwal diperbarui', `RW ${body.rw} · ${dateLong(body.date)}`)
      } else {
        await endpoints.createPosyandu(body)
        toast.success('Jadwal ditambahkan', `RW ${body.rw} · ${dateLong(body.date)}`)
      }
      invalidate(KEYS.posyandu)
      onClose()
    } catch (err) {
      undo?.()
      setErrors({ form: err instanceof ApiError ? err.message : 'Jadwal belum dapat disimpan.' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={isNew ? 'Tambah jadwal Posyandu' : 'Ubah jadwal Posyandu'}
      description="Jadwal tampil di HP warga dan kader RW tersebut setelah sinkronisasi berikutnya."
      icon="calendar"
      footer={<><Button variant="secondary" onClick={onClose}>Batal</Button><Button onClick={() => void submit()} loading={saving}>{isNew ? 'Simpan jadwal' : 'Simpan perubahan'}</Button></>}
    >
      <form className="grid gap-4 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); void submit() }} noValidate>
        {errors.form && <Callout tone="red" icon="alert" className="sm:col-span-2">{errors.form}</Callout>}
        <Field label="RW" htmlFor="p-rw" required error={errors.rw}>
          <Select id="p-rw" value={v.rw} onChange={(e) => set('rw', e.target.value)} invalid={!!errors.rw}>
            <option value="">Pilih RW</option>
            {rwOptions.map((r) => <option key={r} value={r}>RW {r}</option>)}
          </Select>
        </Field>
        <Field label="Tanggal" htmlFor="p-date" required error={errors.date}>
          <Input id="p-date" type="date" value={v.date} min={isNew ? isoDate(new Date()) : undefined} onChange={(e) => set('date', e.target.value)} invalid={!!errors.date} />
        </Field>
        <Field label="Jam mulai" htmlFor="p-start" required error={errors.startTime}>
          <Input id="p-start" type="time" value={v.startTime} onChange={(e) => set('startTime', e.target.value)} invalid={!!errors.startTime} />
        </Field>
        <Field label="Jam selesai" htmlFor="p-end" required error={errors.endTime}>
          <Input id="p-end" type="time" value={v.endTime} onChange={(e) => set('endTime', e.target.value)} invalid={!!errors.endTime} />
        </Field>
        <Field label="Lokasi" htmlFor="p-loc" required error={errors.location} className="sm:col-span-2">
          <Input id="p-loc" value={v.location} onChange={(e) => set('location', e.target.value)} invalid={!!errors.location} placeholder="mis. Balai RW 02" maxLength={120} />
        </Field>
        <Field label="Catatan untuk warga" htmlFor="p-notes" error={errors.notes} hint={`${(v.notes ?? '').length}/200 · mis. “Bawa QR SEHATI” atau “Puasa 8 jam untuk cek gula puasa”`} className="sm:col-span-2">
          <Textarea id="p-notes" value={v.notes ?? ''} onChange={(e) => set('notes', e.target.value)} maxLength={220} rows={3} />
        </Field>
      </form>
    </Dialog>
  )
}

export default function PosyanduPage() {
  const q = usePosyandu()
  const rws = useRw()
  const toast = useToast()
  const [rw, setRw] = useState('')
  const [showPast, setShowPast] = useState(false)
  const [editing, setEditing] = useState<PosyanduSchedule | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [deleting, setDeleting] = useState<PosyanduSchedule | null>(null)
  const [busy, setBusy] = useState(false)
  const today = isoDate(new Date())
  const rwOptions = rws.data?.map((r) => r.rw) ?? Array.from({ length: 8 }, (_, i) => String(i + 1).padStart(2, '0'))

  const groups = useMemo(() => {
    const list = [...(q.data ?? [])]
      .filter((s) => (!rw || s.rw === rw) && (showPast || s.date >= today))
      .sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime))
    const m = new Map<string, PosyanduSchedule[]>()
    for (const s of list) {
      const k = s.date.slice(0, 7)
      m.set(k, [...(m.get(k) ?? []), s])
    }
    return [...m.entries()]
  }, [q.data, rw, showPast, today])

  const pastCount = (q.data ?? []).filter((s) => s.date < today).length

  async function remove(s: PosyanduSchedule) {
    setBusy(true)
    const undo = setQueryData<PosyanduSchedule[]>(KEYS.posyandu, (xs) => (xs ?? []).filter((x) => x.id !== s.id))
    try {
      await endpoints.deletePosyandu(s.id)
      toast.success('Jadwal dihapus', `RW ${s.rw} · ${dateLong(s.date)}`)
      invalidate(KEYS.posyandu)
    } catch (e) {
      undo()
      toast.error('Jadwal gagal dihapus', e instanceof ApiError ? e.message : undefined)
    } finally {
      setBusy(false)
      setDeleting(null)
    }
  }

  const openNew = () => { setEditing(null); setFormOpen(true) }

  return (
    <>
      <PageHeader
        eyebrow="Pengelolaan"
        title="Jadwal Posyandu"
        description="Atur jadwal Posyandu ILP dewasa per RW. Warga hanya menerima jadwal RW-nya; kader menerima jadwal RW tugasnya."
        actions={<Button icon="plus" onClick={openNew}>Tambah jadwal</Button>}
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Select aria-label="Filter RW" value={rw} onChange={(e) => setRw(e.target.value)} className="w-40">
          <option value="">Semua RW</option>
          {rwOptions.map((r) => <option key={r} value={r}>RW {r}</option>)}
        </Select>
        <label className="flex items-center gap-2.5 text-sm text-fg-2">
          <Switch checked={showPast} onChange={setShowPast} label="Tampilkan jadwal yang sudah lewat" />
          Tampilkan yang sudah lewat{pastCount > 0 && <span className="text-muted">({pastCount})</span>}
        </label>
      </div>

      {q.error && !q.data ? (
        <Card><ErrorState error={q.error} onRetry={q.reload} /></Card>
      ) : q.initial ? (
        <div className="grid gap-3 md:grid-cols-2" role="status" aria-label="Memuat jadwal">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-28 rounded-2xl" />)}</div>
      ) : groups.length === 0 ? (
        <Card>
          <EmptyState icon="calendar" title="Belum ada jadwal mendatang" action={<Button icon="plus" onClick={openNew}>Tambah jadwal</Button>}>
            {rw ? `Belum ada jadwal untuk RW ${rw}.` : 'Buat jadwal agar warga mendapat pengingat Posyandu di aplikasinya.'}
          </EmptyState>
        </Card>
      ) : (
        <div className="space-y-7">
          {groups.map(([month, items]) => (
            <section key={month} aria-labelledby={`m-${month}`}>
              <h2 id={`m-${month}`} className="mb-3 text-sm font-bold text-fg-2">{monthLong(month)}</h2>
              <ul className="grid gap-3 md:grid-cols-2">
                {items.map((s, i) => {
                  const d = parseIsoDate(s.date)
                  const past = s.date < today
                  const isToday = s.date === today
                  return (
                    <li key={s.id} className={cx('flex gap-4 rounded-2xl border border-line bg-surface p-4 shadow-soft animate-fade-up', past && 'opacity-65')} style={{ animationDelay: `${i * 40}ms` }}>
                      <div className={cx('flex w-16 shrink-0 flex-col items-center justify-center rounded-xl py-2', isToday ? 'bg-primary text-white' : 'bg-primary-soft text-accent')}>
                        <span className="text-[0.68rem] font-bold uppercase">{DAYS[d.getDay()]}</span>
                        <span className="text-2xl leading-none font-extrabold tabular">{d.getDate()}</span>
                        <span className="text-[0.68rem] font-semibold">{MONTHS[d.getMonth()]}</span>
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge tone="primary" dot={false}>RW {s.rw}</Badge>
                          {isToday && <Badge tone="green">Hari ini</Badge>}
                          {past && <Badge tone="neutral" dot={false}>Selesai</Badge>}
                        </div>
                        <p className="mt-1.5 flex items-center gap-1.5 text-sm font-semibold text-fg"><Icon name="clock" size={15} className="text-muted" />{s.startTime.replace(':', '.')}–{s.endTime.replace(':', '.')} WIB</p>
                        <p className="mt-0.5 flex items-center gap-1.5 text-sm text-fg-2"><Icon name="pin" size={15} className="shrink-0 text-muted" /><span className="truncate">{s.location}</span></p>
                        {s.notes && <p className="mt-1.5 line-clamp-2 text-xs text-muted">{s.notes}</p>}
                      </div>
                      <div className="flex shrink-0 flex-col gap-1">
                        <IconButton icon="pencil" size="sm" label={`Ubah jadwal RW ${s.rw} ${dateLong(s.date)}`} onClick={() => { setEditing(s); setFormOpen(true) }} />
                        <IconButton icon="trash" size="sm" label={`Hapus jadwal RW ${s.rw} ${dateLong(s.date)}`} onClick={() => setDeleting(s)} className="hover:text-red-fg" />
                      </div>
                    </li>
                  )
                })}
              </ul>
            </section>
          ))}
        </div>
      )}

      <ScheduleForm open={formOpen} initial={editing} onClose={() => setFormOpen(false)} rwOptions={rwOptions} />
      <ConfirmDialog
        open={!!deleting}
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && void remove(deleting)}
        loading={busy}
        danger
        title="Hapus jadwal?"
        confirmLabel="Hapus jadwal"
      >
        Jadwal Posyandu <strong className="font-semibold text-fg">RW {deleting?.rw}</strong> pada {deleting ? dateLong(deleting.date) : ''} akan dihapus dari HP warga dan kader
        setelah sinkronisasi berikutnya. Bila jadwal sudah diumumkan, beri tahu warga lewat kader.
      </ConfirmDialog>
    </>
  )
}
