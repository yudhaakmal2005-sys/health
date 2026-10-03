import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { ApiError, IS_MOCK } from '../api/client'
import type { User } from '../api/types'
import { Icon } from '../components/Icon'
import { Logo, LogoMark } from '../components/Logo'
import { MockBanner } from '../components/PublicShell'
import { ThemeToggle } from '../components/ThemeToggle'
import { Button, Callout, Field, Input, buttonClass } from '../components/ui'
import { site } from '../site.config'

const ID_PATTERN = /^[A-Z]{2}-\d{6}$/

function normalizeId(v: string) {
  const s = v.toUpperCase().replace(/[^A-Z0-9-]/g, '')
  // "AD000001" → "AD-000001"
  const m = /^([A-Z]{2})(\d{1,6})$/.exec(s)
  return m ? `${m[1]}-${m[2]}` : s.slice(0, 9)
}

function NotForWeb({ user, onBack }: { user: User; onBack: () => void }) {
  const kader = user.role === 'KADER'
  return (
    <div className="animate-pop-in">
      <span className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-wellness-soft text-green-fg">
        <Icon name="phoneDevice" size={24} />
      </span>
      <h1 className="mt-5 text-2xl font-extrabold tracking-tight text-fg">Halo, {user.fullName.split(' ')[0]}!</h1>
      <p className="mt-2 text-sm leading-relaxed text-fg-2">
        {kader
          ? <>Terima kasih sudah melayani warga{user.rw ? <> RW {user.rw}</> : null}. Pekerjaan kader — pendaftaran, pengukuran Posyandu,
            tindak lanjut, dan kunjungan rumah — dilakukan di <strong className="font-semibold text-fg">aplikasi Android SEHATI</strong>, yang tetap berjalan tanpa internet.</>
          : <>Data kesehatan Anda tersedia di <strong className="font-semibold text-fg">aplikasi Android SEHATI</strong>. Dashboard web ini khusus untuk Admin Puskesmas.</>}
      </p>
      <p className="mt-3 text-sm leading-relaxed text-muted">
        Gunakan SEHATI ID dan kata sandi yang sama di aplikasi. Anda belum masuk ke dashboard web, dan sesi ini telah ditutup.
      </p>
      <div className="mt-6 flex flex-col gap-2 sm:flex-row">
        <a href={site.apk.url} download className={buttonClass('primary', 'md')}>
          <Icon name="download" size={17} /> Unduh aplikasi Android
        </a>
        <Button variant="secondary" onClick={onBack}>Masuk dengan akun lain</Button>
      </div>
    </div>
  )
}

export function LoginPage() {
  const { session, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [sehatiId, setSehatiId] = useState(IS_MOCK ? 'AD-000001' : '')
  const [password, setPassword] = useState(IS_MOCK ? 'demo1234' : '')
  const [show, setShow] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [fieldError, setFieldError] = useState<{ id?: string; pw?: string }>({})
  const [nonAdmin, setNonAdmin] = useState<User | null>(null)
  const expired = params.get('sesi') === 'berakhir'

  if (session?.user.role === 'ADMIN') return <Navigate to="/dasbor" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    const fe: typeof fieldError = {}
    if (!ID_PATTERN.test(sehatiId)) fe.id = 'Format SEHATI ID: dua huruf dan enam angka, misalnya AD-000001.'
    if (!password) fe.pw = 'Masukkan kata sandi.'
    setFieldError(fe)
    setError(null)
    if (fe.id || fe.pw) return
    setPending(true)
    try {
      const res = await login(sehatiId, password)
      if (!res.admitted) {
        setNonAdmin(res.user)
        setPassword('')
        return
      }
      const from = (location.state as { from?: string } | null)?.from
      navigate(from?.startsWith('/dasbor') ? from : '/dasbor', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) setError({ code: err.code, message: err.message || 'SEHATI ID atau kata sandi salah.' })
        else if (err.status === 429) setError({ code: 'ACCOUNT_LOCKED', message: err.message })
        else setError({ code: err.code, message: err.message })
      } else {
        setError({ code: 'UNKNOWN', message: 'Terjadi kesalahan yang tidak terduga. Coba lagi.' })
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <MockBanner />
      <div className="grid flex-1 lg:grid-cols-[1fr_1.05fr]">
        {/* Panel merek */}
        <aside className="relative hidden overflow-hidden bg-gradient-to-br from-[#0284C7] via-[#0369A1] to-[#0C4A6E] p-12 text-white lg:flex lg:flex-col lg:justify-between">
          <svg className="pointer-events-none absolute -right-40 -bottom-40 h-[560px] w-[560px] opacity-[0.12]" viewBox="0 0 64 64" aria-hidden="true">
            <circle cx="32" cy="32" r="30" fill="none" stroke="#fff" strokeWidth="1" />
            <circle cx="32" cy="32" r="23" fill="none" stroke="#fff" strokeWidth="0.6" />
            <circle cx="32" cy="32" r="16" fill="none" stroke="#fff" strokeWidth="0.4" />
          </svg>
          <Link to="/" className="relative inline-flex items-center gap-3 self-start rounded-lg">
            <LogoMark size={44} animated />
            <span className="text-xl font-extrabold tracking-[0.08em]">SEHATI</span>
          </Link>
          <div className="relative max-w-md">
            <h2 className="text-3xl leading-tight font-extrabold tracking-tight">Dashboard Puskesmas untuk pencegahan jantung koroner di desa.</h2>
            <p className="mt-4 text-sm leading-relaxed text-sky-100">
              Pantau cakupan skrining, sebaran faktor risiko per RW, dan registri tindak lanjut — dengan angka agregat yang menjaga privasi warga.
            </p>
            <ul className="mt-8 space-y-3 text-sm text-sky-50">
              {['Tanpa data kesehatan perorangan', 'Sel kecil (< 5) disembunyikan', 'Setiap perubahan tercatat di audit'].map((t) => (
                <li key={t} className="flex items-center gap-2.5">
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-white/15"><Icon name="check" size={14} /></span>
                  {t}
                </li>
              ))}
            </ul>
          </div>
          <p className="relative text-xs text-sky-200">{site.village} · {site.kkn.team}</p>
        </aside>

        {/* Formulir */}
        <main id="konten" className="flex flex-col px-4 py-6 sm:px-10">
          <div className="flex items-center justify-between">
            <Link to="/" className="rounded-lg lg:invisible" aria-label="Kembali ke beranda"><Logo size={32} subtitle={false} /></Link>
            <ThemeToggle />
          </div>
          <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
            {nonAdmin ? (
              <NotForWeb user={nonAdmin} onBack={() => { setNonAdmin(null); setSehatiId('') }} />
            ) : (
              <div className="animate-fade-up">
                <p className="text-xs font-bold tracking-[0.14em] text-accent uppercase">Khusus staf</p>
                <h1 className="mt-2 text-[1.75rem] font-extrabold tracking-tight text-fg">Masuk ke dashboard</h1>
                <p className="mt-1.5 text-sm text-muted">Gunakan SEHATI ID dan kata sandi akun Admin Puskesmas Anda.</p>

                {expired && !error && (
                  <Callout tone="primary" icon="clock" className="mt-6">Sesi Anda telah berakhir. Silakan masuk kembali.</Callout>
                )}
                {error && (
                  <Callout tone={error.code === 'ACCOUNT_LOCKED' ? 'orange' : 'red'} icon={error.code === 'ACCOUNT_LOCKED' ? 'lock' : 'alert'} className="mt-6"
                    title={error.code === 'ACCOUNT_LOCKED' ? 'Akun dikunci sementara' : error.code === 'NETWORK' ? 'Tidak dapat terhubung' : 'Gagal masuk'}>
                    {error.message}
                    {error.code === 'ACCOUNT_LOCKED' && <> Coba lagi setelah 15 menit.</>}
                  </Callout>
                )}

                <form onSubmit={submit} noValidate className="mt-6 space-y-4">
                  <Field label="SEHATI ID" htmlFor="sehatiId" error={fieldError.id} hint="Contoh: AD-000001">
                    <Input
                      id="sehatiId"
                      name="username"
                      autoComplete="username"
                      inputMode="text"
                      autoCapitalize="characters"
                      spellCheck={false}
                      placeholder="AD-000001"
                      value={sehatiId}
                      invalid={!!fieldError.id}
                      onChange={(e) => setSehatiId(normalizeId(e.target.value))}
                      className="font-semibold tracking-wider"
                      autoFocus={!IS_MOCK}
                    />
                  </Field>
                  <Field label="Kata sandi" htmlFor="password" error={fieldError.pw}>
                    <div className="relative">
                      <Input
                        id="password"
                        name="password"
                        type={show ? 'text' : 'password'}
                        autoComplete="current-password"
                        value={password}
                        invalid={!!fieldError.pw}
                        onChange={(e) => setPassword(e.target.value)}
                        className="pr-12"
                      />
                      <button
                        type="button"
                        onClick={() => setShow((v) => !v)}
                        className="absolute inset-y-0 right-1 my-1 inline-flex w-10 items-center justify-center rounded-lg text-muted hover:text-fg"
                        aria-label={show ? 'Sembunyikan kata sandi' : 'Tampilkan kata sandi'}
                        aria-pressed={show}
                      >
                        <Icon name={show ? 'eyeOff' : 'eye'} size={18} />
                      </button>
                    </div>
                  </Field>
                  <Button type="submit" size="lg" className="w-full" loading={pending}>
                    {pending ? 'Memeriksa…' : 'Masuk'}
                  </Button>
                </form>

                {IS_MOCK && (
                  <p className="mt-5 rounded-xl border border-dashed border-line-strong p-3 text-xs leading-relaxed text-muted">
                    <strong className="text-fg-2">Mode demo:</strong> AD-000001 dengan kata sandi apa pun masuk sebagai Admin.
                    KD-000001 menunjukkan pesan untuk kader; kata sandi <code className="font-semibold">kunci</code> mensimulasikan akun terkunci.
                  </p>
                )}

                <div className="mt-8 rounded-2xl bg-surface-2 p-4 text-sm leading-relaxed text-fg-2">
                  <p className="flex items-center gap-2 font-semibold text-fg"><Icon name="phoneDevice" size={16} /> Kader atau warga?</p>
                  <p className="mt-1 text-[0.8125rem] text-muted">
                    Pencatatan dan data pribadi ada di aplikasi Android SEHATI.{' '}
                    <a href={site.apk.url} download className="font-semibold text-accent hover:underline">Unduh aplikasi</a>
                  </p>
                </div>
                <p className="mt-6 text-center text-xs text-muted">
                  Lupa kata sandi? Hubungi pengelola SEHATI di Puskesmas. · <Link to="/privasi" className="hover:text-accent">Privasi</Link>
                </p>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  )
}
