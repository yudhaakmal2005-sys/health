import { useMemo, useState } from 'react'
import { endpoints } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { ClinicalThresholds } from '../../api/types'
import { Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { useToast } from '../../components/Toast'
import { Badge, Button, Callout, Card, CardHeader, ErrorState, Input, PageHeader, Skeleton, cx } from '../../components/ui'
import { KEYS, useThresholds } from '../../lib/hooks'
import { invalidate } from '../../lib/query'
import {
  ALL_FIELDS, DEFAULT_THRESHOLDS, THRESHOLD_GROUPS, parseDraft, sameThresholds, toDraft, validateThresholds,
  type DraftValues, type FieldMeta, type ThresholdKey,
} from '../../lib/thresholds'

function pairs(fields: FieldMeta[]): [FieldMeta, FieldMeta][] {
  const out: [FieldMeta, FieldMeta][] = []
  for (let i = 0; i + 1 < fields.length; i += 2) out.push([fields[i]!, fields[i + 1]!])
  return out
}
const categoryOf = (label: string) => label.split(' — ')[0] ?? label
const ruleOf = (label: string) => (label.split(' — ')[1] ?? '').replace(/^sistolik /, '')

function fmt(v: number) {
  return String(v).replace('.', ',')
}

function Editor({ server, version }: { server: ClinicalThresholds | null; version: number }) {
  const toast = useToast()
  const base = server ?? DEFAULT_THRESHOLDS
  const [draft, setDraft] = useState<DraftValues>(() => toDraft(base))
  const [baseKey, setBaseKey] = useState(() => JSON.stringify(server))
  const [touched, setTouched] = useState(false)
  const [confirm, setConfirm] = useState<'save' | 'reset' | null>(null)
  const [ack, setAck] = useState(false)
  const [saving, setSaving] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  // Bila data server berubah (mis. setelah simpan), sinkronkan isian.
  const k = JSON.stringify(server)
  if (k !== baseKey) {
    setBaseKey(k)
    setDraft(toDraft(base))
    setTouched(false)
  }

  const parsed = useMemo(() => parseDraft(draft), [draft])
  const issue = parsed.value ? validateThresholds(parsed.value) : null
  const issueFields = new Set<ThresholdKey>(issue?.fields ?? [])
  const dirty = parsed.value ? !sameThresholds(parsed.value, base) : true
  const changed = ALL_FIELDS.filter((f) => parsed.value ? parsed.value[f.key] !== base[f.key] : draft[f.key] !== fmt(base[f.key]))
  const isDefaultDraft = parsed.value ? sameThresholds(parsed.value, DEFAULT_THRESHOLDS) : false
  const canSave = touched && dirty && !!parsed.value && !issue

  function renderField(f: FieldMeta, shortLabel?: string) {
    const fieldErr = parsed.fieldErrors[f.key]
    const bad = !!fieldErr || issueFields.has(f.key)
    const isChanged = changed.some((c) => c.key === f.key)
    return (
      <div className="flex flex-col gap-1.5">
        <label htmlFor={`t-${f.key}`} className={cx('flex items-center justify-between gap-2 text-[0.8125rem] font-semibold text-fg-2', shortLabel && 'sm:sr-only')}>
          <span>{shortLabel ? <><span className="sm:hidden">{shortLabel}</span><span className="max-sm:sr-only">{f.label}</span></> : f.label}</span>
          {isChanged && <span className="rounded bg-orange-bg px-1.5 text-[0.65rem] font-bold text-orange-fg">diubah</span>}
        </label>
        <Input
          id={`t-${f.key}`}
          inputMode={f.int ? 'numeric' : 'decimal'}
          value={draft[f.key]}
          invalid={bad}
          suffix={f.unit}
          onChange={(e) => setDraft((d) => ({ ...d, [f.key]: e.target.value }))}
          className="tabular font-semibold"
          aria-describedby={`t-${f.key}-hint`}
        />
        <p id={`t-${f.key}-hint`} className={cx('text-xs', fieldErr ? 'font-medium text-red-fg' : 'text-muted')}>
          {fieldErr ?? <>Bawaan: {fmt(DEFAULT_THRESHOLDS[f.key])}{f.hint ? ` · ${f.hint}` : ''}{isChanged && shortLabel ? ' · diubah' : ''}</>}
        </p>
      </div>
    )
  }

  async function put(value: ClinicalThresholds | null) {
    setSaving(true)
    setServerError(null)
    try {
      await endpoints.putThresholds(value)
      toast.success(value ? 'Ambang klinis disimpan' : 'Ambang dikembalikan ke bawaan', 'Perangkat menerima perubahan saat sinkronisasi berikutnya.')
      setConfirm(null)
      setAck(false)
      invalidate(KEYS.thresholds)
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : 'Perubahan belum dapat disimpan.')
      setConfirm(null)
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="mb-5 flex flex-wrap items-center gap-3">
        {server ? <Badge tone="orange">Memakai ambang khusus</Badge> : <Badge tone="green">Memakai bawaan aplikasi</Badge>}
        <span className="text-sm text-muted">Versi konfigurasi {version} · aturan sehati-rules-1.0{server ? '+custom' : ''}</span>
      </div>

      {serverError && <Callout tone="red" icon="alert" title="Server menolak perubahan" className="mb-5">{serverError}</Callout>}

      <form
        onSubmit={(e) => { e.preventDefault(); if (canSave) setConfirm('save') }}
        onChange={() => setTouched(true)}
        noValidate
        className="space-y-5"
      >
        {THRESHOLD_GROUPS.filter((g) => g.paired).map((g) => (
          <Card key={g.title} className="pb-5 animate-fade-up">
            <CardHeader title={g.title} description={g.description} />
            <div className="px-5 pt-4 sm:px-6">
              <div className="hidden grid-cols-[minmax(10rem,1fr)_1.2fr_1.2fr] gap-4 border-b border-line pb-2 text-xs font-bold text-muted sm:grid">
                <span>Kategori</span><span>Sistolik</span><span>Diastolik</span>
              </div>
              {pairs(g.fields).map(([sys, dia]) => (
                <div key={sys.key} className="grid gap-x-4 gap-y-2 border-b border-line py-3 last:border-0 sm:grid-cols-[minmax(10rem,1fr)_1.2fr_1.2fr] sm:items-start">
                  <p className="pt-2.5 text-sm font-semibold text-fg">{categoryOf(sys.label)}<span className="block text-xs font-normal text-muted">{ruleOf(sys.label)}</span></p>
                  {renderField(sys, 'Sistolik')}
                  {renderField(dia, 'Diastolik')}
                </div>
              ))}
            </div>
          </Card>
        ))}
        <div className="gap-5 xl:columns-2 [&>*]:mb-5 [&>*]:break-inside-avoid">
          {THRESHOLD_GROUPS.filter((g) => !g.paired).map((g, gi) => (
            <Card key={g.title} className="pb-5 animate-fade-up" style={{ animationDelay: `${gi * 50}ms` }}>
              <CardHeader title={g.title} description={g.description} />
              <div className="grid gap-4 px-5 pt-5 sm:grid-cols-2 sm:px-6">
                {g.fields.map((f) => <div key={f.key}>{renderField(f)}</div>)}
              </div>
            </Card>
          ))}
        </div>

        {/* Bilah aksi */}
        <div className={cx(touched && dirty && 'sticky bottom-4 z-10')}>
          <div className="flex flex-col gap-3 rounded-2xl border border-line bg-surface/95 p-4 shadow-pop backdrop-blur sm:flex-row sm:items-center">
            <div className="min-w-0 flex-1 text-sm" aria-live="polite">
              {issue ? (
                <p className="flex items-start gap-2 font-medium text-red-fg"><Icon name="alert" size={17} className="mt-px shrink-0" />{issue.message}</p>
              ) : Object.keys(parsed.fieldErrors).length ? (
                <p className="flex items-start gap-2 font-medium text-red-fg"><Icon name="alert" size={17} className="mt-px shrink-0" />Periksa kolom yang ditandai merah.</p>
              ) : dirty && touched ? (
                <p className="flex items-center gap-2 text-fg-2"><Icon name="pencil" size={16} className="text-orange-fg" />{changed.length} nilai diubah, belum disimpan.</p>
              ) : (
                <p className="flex items-center gap-2 text-muted"><Icon name="checkCircle" size={16} className="text-wellness" />Tidak ada perubahan.</p>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="ghost" onClick={() => setConfirm('reset')} disabled={!server && isDefaultDraft} icon="refresh">Kembalikan bawaan</Button>
              {touched && dirty && <Button variant="secondary" onClick={() => { setDraft(toDraft(base)); setTouched(false) }}>Batalkan</Button>}
              <Button type="submit" disabled={!canSave} icon="check">Simpan ambang</Button>
            </div>
          </div>
        </div>
      </form>

      <Dialog
        open={confirm !== null}
        onClose={() => { setConfirm(null); setAck(false) }}
        title={confirm === 'reset' ? 'Kembalikan ke bawaan aplikasi?' : 'Simpan ambang klinis baru?'}
        icon="alert"
        tone="red"
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => { setConfirm(null); setAck(false) }}>Batal</Button>
            <Button
              variant={confirm === 'reset' ? 'primary' : 'danger'}
              disabled={confirm === 'save' && !ack}
              loading={saving}
              onClick={() => void put(confirm === 'reset' ? null : parsed.value)}
            >
              {confirm === 'reset' ? 'Kembalikan bawaan' : 'Ya, terapkan untuk semua'}
            </Button>
          </>
        }
      >
        {confirm === 'reset' ? (
          <p className="text-sm leading-relaxed text-fg-2">
            Server akan menyimpan <code className="rounded bg-surface-2 px-1">null</code> sehingga semua perangkat kembali memakai ambang bawaan aplikasi
            (sehati-rules-1.0). Profil warga dihitung ulang saat perangkat menerima perubahan. Tindakan ini dicatat di audit.
          </p>
        ) : (
          <div className="space-y-4 text-sm leading-relaxed text-fg-2">
            <p>Perubahan ini akan dipakai oleh <strong className="font-semibold text-fg">semua aplikasi warga dan kader</strong> setelah sinkronisasi, dan semua profil dihitung ulang.</p>
            <ul className="max-h-48 space-y-1 overflow-y-auto rounded-xl border border-line bg-surface-2 p-3">
              {changed.map((f) => (
                <li key={f.key} className="flex items-center justify-between gap-3 text-[0.8125rem]">
                  <span className="text-fg-2">{f.label}</span>
                  <span className="tabular shrink-0 font-semibold text-fg">{fmt(base[f.key])} → {parsed.value ? fmt(parsed.value[f.key]) : '?'} {f.unit}</span>
                </li>
              ))}
            </ul>
            <label className="flex items-start gap-3 rounded-xl border border-red/30 bg-red-bg p-3 text-red-fg">
              <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} className="mt-1 h-4 w-4 shrink-0 accent-[var(--risk-red)]" />
              <span className="font-medium">Saya memastikan perubahan ini mengikuti pedoman resmi yang berlaku (Kemenkes/Dinas Kesehatan) dan sudah disetujui penanggung jawab program PTM Puskesmas.</span>
            </label>
          </div>
        )}
      </Dialog>
    </>
  )
}

export default function ThresholdsPage() {
  const q = useThresholds()
  return (
    <>
      <PageHeader
        eyebrow="Pengelolaan"
        title="Ambang klinis"
        description="Batas yang dipakai aplikasi untuk mengelompokkan hasil skrining dan membuat rekomendasi tindak lanjut."
      />
      <Callout tone="red" icon="alert" title="Perubahan berlaku untuk semua warga dan kader" className="mb-6">
        Ambang ini memengaruhi kategori hasil, profil SEHATI, dan rekomendasi tindak lanjut di setiap perangkat. Ubah hanya bila mengikuti
        <strong className="font-semibold"> pedoman resmi</strong> yang berlaku dan atas persetujuan penanggung jawab program. Bila ragu, gunakan nilai bawaan.
        Setiap perubahan tercatat di audit log. Hasil SEHATI tetap merupakan data skrining, bukan diagnosis.
      </Callout>
      {q.data ? (
        <Editor server={q.data.thresholds} version={q.data.version} />
      ) : q.error ? (
        <Card><ErrorState error={q.error} onRetry={q.reload} /></Card>
      ) : (
        <div className="grid gap-5 xl:grid-cols-2" role="status" aria-label="Memuat ambang">
          <Skeleton className="h-64 rounded-2xl xl:col-span-2" />
          <Skeleton className="h-52 rounded-2xl" />
          <Skeleton className="h-52 rounded-2xl" />
        </div>
      )}
    </>
  )
}
