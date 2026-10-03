import { useMemo, useState } from 'react'
import { Icon } from '../../components/Icon'
import { Badge, Button, Card, EmptyState, ErrorState, Input, PageHeader, Select, SkeletonRows } from '../../components/ui'
import { ROLE_LABEL, auditLabel, auditTone } from '../../lib/domain'
import { dateTime, relative } from '../../lib/format'
import { useAudit } from '../../lib/hooks'

const CATEGORIES: { value: string; label: string; test: (a: string) => boolean }[] = [
  { value: '', label: 'Semua aktivitas', test: () => true },
  { value: 'login', label: 'Masuk & keluar', test: (a) => /LOGIN|LOGOUT|LOCK/.test(a) },
  { value: 'account', label: 'Akun kader', test: (a) => /CADRE|USER_CREATE|REGISTER/.test(a) },
  { value: 'followup', label: 'Tindak lanjut', test: (a) => /FOLLOWUP/.test(a) },
  { value: 'threshold', label: 'Ambang klinis', test: (a) => /THRESHOLD/.test(a) },
  { value: 'posyandu', label: 'Jadwal Posyandu', test: (a) => /POSYANDU/.test(a) },
  { value: 'delete', label: 'Penghapusan data', test: (a) => /DELETE/.test(a) && !/POSYANDU/.test(a) },
]

export default function AuditPage() {
  const [limit, setLimit] = useState(100)
  const [cat, setCat] = useState('')
  const [search, setSearch] = useState('')
  const q = useAudit(limit)

  const rows = useMemo(() => {
    const c = CATEGORIES.find((x) => x.value === cat) ?? CATEGORIES[0]!
    const s = search.trim().toLowerCase()
    return (q.data ?? []).filter((e) => c.test(e.action) && (!s || [e.actorId, e.subjectId, e.detail, e.action].some((v) => v?.toLowerCase().includes(s))))
  }, [q.data, cat, search])

  return (
    <>
      <PageHeader
        eyebrow="Laporan & audit"
        title="Audit log"
        description="Jejak perubahan penting: masuk, akun, penugasan, ambang klinis, jadwal, dan penghapusan data. Log tidak memuat isi data kesehatan."
        actions={<Button variant="secondary" size="sm" icon="refresh" onClick={q.reload} loading={q.loading && !q.initial}>Muat ulang</Button>}
      />
      <Card>
        <div className="flex flex-col gap-3 border-b border-line p-4 sm:flex-row sm:items-center sm:p-5">
          <Select aria-label="Jenis aktivitas" value={cat} onChange={(e) => setCat(e.target.value)} className="sm:w-56">
            {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
          </Select>
          <div className="relative flex-1 sm:max-w-xs">
            <Icon name="search" size={17} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-muted" />
            <Input aria-label="Cari ID atau detail" placeholder="Cari ID pelaku, subjek, atau detail" value={search} onChange={(e) => setSearch(e.target.value)} className="pl-10" />
          </div>
          <Select aria-label="Jumlah entri" value={String(limit)} onChange={(e) => setLimit(Number(e.target.value))} className="sm:ml-auto sm:w-44">
            {[100, 250, 500].map((n) => <option key={n} value={n}>{n} entri terakhir</option>)}
          </Select>
        </div>

        {q.error && !q.data ? <ErrorState error={q.error} onRetry={q.reload} /> : q.initial ? <SkeletonRows rows={8} /> : rows.length === 0 ? (
          <EmptyState icon="history" title="Tidak ada entri">Tidak ada aktivitas yang cocok dengan filter ini.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <caption className="sr-only">Audit log</caption>
              <thead>
                <tr className="border-b border-line text-left text-xs font-semibold text-muted">
                  <th scope="col" className="px-5 py-3">Waktu</th>
                  <th scope="col" className="px-3 py-3">Aktivitas</th>
                  <th scope="col" className="px-3 py-3">Pelaku</th>
                  <th scope="col" className="px-3 py-3">Subjek</th>
                  <th scope="col" className="px-5 py-3">Detail</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e, i) => (
                  <tr key={`${e.at}-${i}`} className="border-b border-line last:border-0 hover:bg-surface-2/60">
                    <td className="px-5 py-2.5 whitespace-nowrap">
                      <span className="block font-medium text-fg tabular">{dateTime(e.at)}</span>
                      {Date.now() - e.at < 30 * 864e5 && <span className="text-xs text-muted">{relative(e.at)}</span>}
                    </td>
                    <td className="px-3 py-2.5"><Badge tone={auditTone(e.action)}>{auditLabel(e.action)}</Badge></td>
                    <td className="px-3 py-2.5 whitespace-nowrap">
                      <span className="font-semibold tracking-wide text-fg">{e.actorId}</span>
                      <span className="block text-xs text-muted">{ROLE_LABEL[e.actorRole] ?? e.actorRole}</span>
                    </td>
                    <td className="px-3 py-2.5 text-fg-2">{e.subjectId ?? <span className="text-muted">—</span>}</td>
                    <td className="px-5 py-2.5 text-fg-2">{e.detail ?? <span className="text-muted">—</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {q.data && <p className="border-t border-line px-5 py-3 text-xs text-muted">{rows.length} dari {q.data.length} entri ditampilkan</p>}
      </Card>
    </>
  )
}
