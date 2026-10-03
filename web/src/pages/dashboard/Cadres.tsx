import { useState } from 'react'
import { endpoints } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { Cadre } from '../../api/types'
import { ConfirmDialog, Dialog } from '../../components/Dialog'
import { useToast } from '../../components/Toast'
import { Badge, Button, Callout, Card, EmptyState, ErrorState, Field, IconButton, Input, PageHeader, Select, SkeletonRows, Switch, cx } from '../../components/ui'
import { dateTime, relative } from '../../lib/format'
import { KEYS, useCadres, useRw } from '../../lib/hooks'
import { invalidate, setQueryData } from '../../lib/query'

const ALPHABET = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
function tempPassword(len = 10) {
  const buf = new Uint32Array(len)
  crypto.getRandomValues(buf)
  return Array.from(buf, (n) => ALPHABET[n % ALPHABET.length]).join('')
}

async function copy(text: string, ok: () => void) {
  try {
    await navigator.clipboard.writeText(text)
    ok()
  } catch {
    /* clipboard tidak tersedia */
  }
}

function AddCadreDialog({ open, onClose, rwOptions }: { open: boolean; onClose: () => void; rwOptions: string[] }) {
  const toast = useToast()
  const [name, setName] = useState('')
  const [rw, setRw] = useState('')
  const [password, setPassword] = useState(tempPassword)
  const [errors, setErrors] = useState<{ name?: string; rw?: string; pw?: string; form?: string }>({})
  const [saving, setSaving] = useState(false)
  const [created, setCreated] = useState<{ sehatiId: string; password: string; name: string } | null>(null)

  const reset = () => {
    setName(''); setRw(''); setPassword(tempPassword()); setErrors({}); setCreated(null)
  }
  const close = () => { onClose(); window.setTimeout(reset, 200) }

  async function submit() {
    const e: typeof errors = {}
    if (name.trim().length < 3) e.name = 'Tulis nama lengkap kader (minimal 3 huruf).'
    if (!/^\d{2}$/.test(rw)) e.rw = 'Pilih RW tugas.'
    if (password.length < 8) e.pw = 'Kata sandi sementara minimal 8 karakter.'
    setErrors(e)
    if (Object.keys(e).length) return
    setSaving(true)
    try {
      const res = await endpoints.createCadre({ fullName: name.trim(), rw, password })
      setCreated({ sehatiId: res.sehatiId, password, name: name.trim() })
      toast.success('Kader ditambahkan', `${name.trim()} · ${res.sehatiId}`)
      invalidate(KEYS.cadres)
    } catch (err) {
      setErrors({ form: err instanceof ApiError ? err.message : 'Kader belum dapat ditambahkan.' })
    } finally {
      setSaving(false)
    }
  }

  if (created) {
    return (
      <Dialog open={open} onClose={close} title="Akun kader siap" icon="checkCircle" size="sm"
        description="Sampaikan SEHATI ID dan kata sandi sementara langsung kepada kader — jangan lewat grup percakapan."
        footer={<Button onClick={close}>Selesai</Button>}>
        <div className="space-y-3">
          {([['SEHATI ID', created.sehatiId], ['Kata sandi sementara', created.password]] as const).map(([label, value]) => (
            <div key={label} className="flex items-center justify-between gap-3 rounded-xl border border-line bg-surface-2 px-4 py-3">
              <div className="min-w-0">
                <p className="text-xs font-semibold text-muted">{label}</p>
                <p className="mt-0.5 font-mono text-lg font-bold tracking-wider text-fg">{value}</p>
              </div>
              <IconButton icon="copy" label={`Salin ${label}`} onClick={() => void copy(value, () => toast.info(`${label} disalin`))} />
            </div>
          ))}
          <Callout tone="yellow" icon="key">Kata sandi ini hanya ditampilkan sekali. {created.name} dapat masuk di aplikasi Android SEHATI dengan akun ini.</Callout>
        </div>
      </Dialog>
    )
  }

  return (
    <Dialog open={open} onClose={close} title="Tambah kader" icon="userPlus" description="Akun kader dipakai untuk masuk di aplikasi Android SEHATI."
      footer={<><Button variant="secondary" onClick={close}>Batal</Button><Button onClick={() => void submit()} loading={saving} icon="userPlus">Buat akun</Button></>}>
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void submit() }} noValidate>
        {errors.form && <Callout tone="red" icon="alert">{errors.form}</Callout>}
        <Field label="Nama lengkap" htmlFor="c-name" required error={errors.name}>
          <Input id="c-name" value={name} onChange={(e) => setName(e.target.value)} invalid={!!errors.name} autoComplete="off" placeholder="mis. Sri Wahyuni" />
        </Field>
        <Field label="RW tugas" htmlFor="c-rw" required error={errors.rw} hint="Kader melihat data warga di RW ini saja.">
          <Select id="c-rw" value={rw} onChange={(e) => setRw(e.target.value)} invalid={!!errors.rw}>
            <option value="">Pilih RW</option>
            {rwOptions.map((r) => <option key={r} value={r}>RW {r}</option>)}
          </Select>
        </Field>
        <Field label="Kata sandi sementara" htmlFor="c-pw" required error={errors.pw} hint="Dibuat acak. Anda boleh menggantinya (minimal 8 karakter).">
          <div className="flex gap-2">
            <Input id="c-pw" value={password} onChange={(e) => setPassword(e.target.value)} invalid={!!errors.pw} className="font-mono tracking-wider" autoComplete="new-password" spellCheck={false} />
            <IconButton icon="refresh" label="Buat kata sandi baru" onClick={() => setPassword(tempPassword())} className="h-11 w-11 shrink-0 border border-line-strong" />
          </div>
        </Field>
      </form>
    </Dialog>
  )
}

export default function CadresPage() {
  const q = useCadres()
  const rws = useRw()
  const toast = useToast()
  const [adding, setAdding] = useState(false)
  const [confirm, setConfirm] = useState<Cadre | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  const rwOptions = rws.data?.map((r) => r.rw) ?? Array.from({ length: 8 }, (_, i) => String(i + 1).padStart(2, '0'))
  const list = [...(q.data ?? [])].sort((a, b) => Number(b.active) - Number(a.active) || a.rw.localeCompare(b.rw) || a.fullName.localeCompare(b.fullName))
  const activeCount = list.filter((c) => c.active).length

  async function setActive(c: Cadre, active: boolean) {
    setBusy(c.sehatiId)
    const undo = setQueryData<Cadre[]>(KEYS.cadres, (xs) => (xs ?? []).map((x) => (x.sehatiId === c.sehatiId ? { ...x, active } : x)))
    try {
      await endpoints.setCadreActive(c.sehatiId, active)
      toast.success(active ? 'Kader diaktifkan' : 'Kader dinonaktifkan', `${c.fullName} · ${c.sehatiId}`)
      invalidate(KEYS.cadres)
    } catch (e) {
      undo()
      toast.error('Perubahan gagal disimpan', e instanceof ApiError ? e.message : undefined)
    } finally {
      setBusy(null)
      setConfirm(null)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Pengelolaan"
        title="Kader Posyandu"
        description="Kelola akun kader per RW. Kader nonaktif tidak dapat masuk atau menyinkronkan data, tetapi riwayatnya tetap tersimpan."
        actions={<Button icon="userPlus" onClick={() => setAdding(true)}>Tambah kader</Button>}
      />

      <Card>
        {q.error && !q.data ? <ErrorState error={q.error} onRetry={q.reload} /> : q.initial ? <SkeletonRows rows={6} /> : list.length === 0 ? (
          <EmptyState icon="users" title="Belum ada kader" action={<Button icon="userPlus" onClick={() => setAdding(true)}>Tambah kader pertama</Button>}>
            Tambahkan kader untuk setiap RW agar pendaftaran dan pengukuran Posyandu dapat dimulai.
          </EmptyState>
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2 border-b border-line px-5 py-3.5 text-sm text-muted">
              <Badge tone="green">{activeCount} aktif</Badge>
              {list.length - activeCount > 0 && <Badge tone="neutral">{list.length - activeCount} nonaktif</Badge>}
              <span className="ml-auto text-xs">Terakhir aktif = sinkronisasi terakhir dari HP kader</span>
            </div>
            <ul className="divide-y divide-line">
              {list.map((c) => (
                <li key={c.sehatiId} className={cx('flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3.5 transition-opacity', !c.active && 'opacity-70')}>
                  <span className={cx('inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold', c.active ? 'bg-primary-soft text-accent' : 'bg-surface-2 text-muted')} aria-hidden="true">
                    {c.fullName.replace(/\(.*\)/, '').trim().split(/\s+/).slice(0, 2).map((w) => w[0]).join('')}
                  </span>
                  <div className="min-w-0 flex-1 basis-40">
                    <p className="truncate font-semibold text-fg">{c.fullName}</p>
                    <p className="text-xs text-muted"><span className="font-medium tracking-wide">{c.sehatiId}</span> · RW {c.rw}</p>
                  </div>
                  <div className="w-36 text-sm" title={c.lastSeenAt ? dateTime(c.lastSeenAt) : undefined}>
                    <p className="text-xs text-muted">Terakhir aktif</p>
                    <p className={cx('font-medium', c.lastSeenAt && Date.now() - c.lastSeenAt > 14 * 864e5 ? 'text-orange-fg' : 'text-fg-2')}>{relative(c.lastSeenAt)}</p>
                  </div>
                  <div className="flex w-36 items-center justify-end gap-3">
                    <span className={cx('text-sm font-semibold', c.active ? 'text-green-fg' : 'text-muted')}>{c.active ? 'Aktif' : 'Nonaktif'}</span>
                    <Switch
                      checked={c.active}
                      label={`${c.active ? 'Nonaktifkan' : 'Aktifkan'} ${c.fullName}`}
                      disabled={busy === c.sehatiId}
                      onChange={(v) => (v ? void setActive(c, true) : setConfirm(c))}
                    />
                  </div>
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>


      <AddCadreDialog open={adding} onClose={() => setAdding(false)} rwOptions={rwOptions} />
      <ConfirmDialog
        open={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={() => confirm && void setActive(confirm, false)}
        loading={!!confirm && busy === confirm.sehatiId}
        title="Nonaktifkan kader?"
        confirmLabel="Nonaktifkan"
        danger
      >
        <strong className="font-semibold text-fg">{confirm?.fullName}</strong> tidak akan bisa masuk atau mengirim data dari aplikasi. Tindak lanjut yang
        ditugaskan kepadanya sebaiknya dialihkan ke kader lain. Anda dapat mengaktifkannya kembali kapan saja.
      </ConfirmDialog>
    </>
  )
}
