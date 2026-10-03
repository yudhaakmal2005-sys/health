import { Link } from 'react-router'
import { PublicShell } from '../components/PublicShell'
import { Icon, type IconName } from '../components/Icon'
import { LogoMark } from '../components/Logo'
import { buttonClass, cx } from '../components/ui'
import { site } from '../site.config'

const AUDIENCES: { icon: IconName; who: string; title: string; points: string[]; tone: string }[] = [
  {
    icon: 'heart',
    who: 'Warga',
    title: 'Kenali faktor risiko jantung sejak dini',
    tone: 'from-rose-500/12',
    points: [
      'Asesmen singkat 9 faktor risiko jantung koroner — bukan skor kemungkinan, bukan diagnosis',
      'Lima pilar jantung sehat: aktif, bebas rokok, garam terkendali, tidur cukup, kontrol tensi',
      'Tantangan 7 hari, langkah harian, dan Tanya SEHATI untuk pertanyaan sehari-hari',
      'Kartu QR SEHATI untuk mempercepat pendaftaran di Posyandu',
    ],
  },
  {
    icon: 'stethoscope',
    who: 'Kader Posyandu',
    title: 'Skrining dewasa yang rapi, walau tanpa sinyal',
    tone: 'from-emerald-500/12',
    points: [
      'Alur 5 langkah Posyandu ILP: pendaftaran, pengukuran, pencatatan, penyuluhan, validasi',
      'Pindai QR warga, ukur tensi, gula darah, kolesterol, dan lingkar perut',
      'Daftar tindak lanjut otomatis: ukur ulang, kunjungan rumah, rujukan Puskesmas',
      'Data tersimpan terenkripsi di HP dan dikirim saat ada internet',
    ],
  },
  {
    icon: 'building',
    who: 'Puskesmas',
    title: 'Gambaran desa tanpa membuka data pribadi',
    tone: 'from-indigo-500/12',
    points: [
      'Ringkasan agregat per RW dengan penyembunyian sel kecil (< 5)',
      'Registri tindak lanjut: tugaskan kader dan atur jadwal kunjungan',
      'Jadwal Posyandu per RW langsung tampil di HP warga dan kader',
      'Ambang klinis terpusat yang tervalidasi dan tercatat di audit',
    ],
  },
]

const PILLARS: { icon: IconName; label: string }[] = [
  { icon: 'activity', label: 'Aktif bergerak' },
  { icon: 'cigarette', label: 'Bebas rokok' },
  { icon: 'filter', label: 'Garam terkendali' },
  { icon: 'moon', label: 'Tidur cukup' },
  { icon: 'heartPulse', label: 'Kontrol tensi' },
]

const STEPS: { icon: IconName; title: string; body: string }[] = [
  {
    icon: 'phoneDevice',
    title: 'Daftar dan kenali risiko',
    body: 'Warga mendaftar sendiri atau dibantu kader, lalu mengisi asesmen gaya hidup. SEHATI menunjukkan faktor yang bisa diperbaiki beserta langkah kecilnya.',
  },
  {
    icon: 'stethoscope',
    title: 'Ukur di Posyandu',
    body: 'Kader memindai QR warga, mencatat tensi, gula darah, kolesterol, IMT, dan lingkar perut — tetap berjalan meski tanpa sinyal.',
  },
  {
    icon: 'clipboard',
    title: 'Tindak lanjut terarah',
    body: 'Hasil yang perlu perhatian menjadi daftar tindak lanjut. Saat online, data tersinkron agar Puskesmas dapat menugaskan kader dan menjadwalkan kunjungan.',
  },
]

function PhoneMock() {
  return (
    <div className="relative mx-auto w-full max-w-[300px]" aria-hidden="true">
      <div className="absolute -inset-10 -z-10 rounded-full bg-gradient-to-br from-rose-400/25 via-orange-300/10 to-emerald-300/20 blur-3xl" />
      <div className="rounded-[2.4rem] border border-line-strong bg-surface p-2.5 shadow-pop">
        <div className="overflow-hidden rounded-[1.9rem] bg-bg">
          <div className="flex items-center justify-between bg-gradient-to-br from-[#E11D48] to-[#BE123C] px-5 pt-6 pb-10 text-white">
            <div>
              <p className="text-[0.7rem] opacity-80">Selamat pagi,</p>
              <p className="text-base font-bold">Warga Contoh</p>
            </div>
            <LogoMark size={34} />
          </div>
          <div className="-mt-7 space-y-3 px-3.5 pb-5">
            <div className="rounded-2xl border border-line bg-surface p-3.5 shadow-card">
              <p className="text-[0.65rem] font-semibold tracking-wide text-muted uppercase">Profil SEHATI</p>
              <div className="mt-1.5 flex items-center gap-2">
                <span className="inline-flex items-center gap-1 rounded-full bg-yellow-bg px-2 py-0.5 text-[0.7rem] font-bold text-yellow-fg">
                  <span className="h-1.5 w-1.5 rounded-full bg-yellow" /> Waspada Faktor Risiko
                </span>
              </div>
              <p className="mt-2 text-[0.7rem] leading-snug text-muted">3 faktor bisa diperbaiki. Hasil pemantauan, bukan diagnosis.</p>
            </div>
            <div className="rounded-2xl border border-line bg-surface p-3.5">
              <p className="text-[0.7rem] font-bold text-fg">Lima pilar minggu ini</p>
              {[['Aktif', 72, 'bg-wellness'], ['Bebas rokok', 100, 'bg-wellness'], ['Garam', 45, 'bg-yellow'], ['Tidur', 80, 'bg-wellness'], ['Tensi', 60, 'bg-primary']].map(([l, v, c]) => (
                <div key={l as string} className="mt-2 flex items-center gap-2">
                  <span className="w-16 text-[0.65rem] text-fg-2">{l}</span>
                  <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-2">
                    <span className={cx('block h-full rounded-full', c as string)} style={{ width: `${v}%` }} />
                  </span>
                </div>
              ))}
            </div>
            <div className="flex items-center gap-3 rounded-2xl border border-line bg-surface p-3">
              <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-primary-soft text-accent"><Icon name="calendar" size={17} /></span>
              <div className="min-w-0">
                <p className="text-[0.7rem] font-bold text-fg">Posyandu RW 02</p>
                <p className="text-[0.65rem] text-muted">Sabtu, 08.00–11.00 · Balai RW</p>
              </div>
            </div>
            <div className="flex items-center justify-center gap-1.5 rounded-xl bg-wellness-soft py-1.5 text-[0.65rem] font-semibold text-green-fg">
              <Icon name="wifiOff" size={12} /> Tersimpan di perangkat — sinkron saat online
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export function LandingPage() {
  return (
    <PublicShell>
      {/* ---------- Hero ---------- */}
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60rem_30rem_at_85%_-10%,color-mix(in_srgb,var(--primary)_16%,transparent),transparent),radial-gradient(40rem_24rem_at_-10%_10%,color-mix(in_srgb,var(--wellness)_10%,transparent),transparent)]" />
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 pt-10 pb-16 sm:px-6 md:pt-16 lg:grid-cols-[1.15fr_1fr] lg:gap-8 lg:pb-24">
          <div className="animate-fade-up">
            <p className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary-soft px-3 py-1 text-xs font-semibold text-accent">
              <Icon name="heartPulse" size={14} /> Posyandu ILP · Skrining dewasa · {site.village}
            </p>
            <h1 className="mt-5 text-[2.1rem] leading-[1.12] font-extrabold tracking-tight text-fg sm:text-5xl lg:text-[3.35rem]">
              Jaga jantung warga, <span className="bg-gradient-to-r from-[#E11D48] to-[#F97316] bg-clip-text text-transparent">dimulai dari Posyandu.</span>
            </h1>
            <p className="mt-5 max-w-xl text-base leading-relaxed text-fg-2 sm:text-lg">
              SEHATI membantu warga, kader, dan Puskesmas mencegah penyakit jantung koroner lewat skrining rutin,
              edukasi gaya hidup, dan tindak lanjut yang terarah — tetap berjalan tanpa internet.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
              <a href={site.apk.url} download className={buttonClass('primary', 'lg', 'shadow-card')}>
                <Icon name="download" size={19} /> Unduh aplikasi Android
              </a>
              <Link to="/masuk" className={buttonClass('secondary', 'lg')}>
                Masuk staf Puskesmas <Icon name="arrowRight" size={17} />
              </Link>
            </div>
            <p className="mt-3 text-xs text-muted">Versi {site.apk.version} · {site.apk.minAndroid} · Gratis</p>
            <ul className="mt-8 flex flex-wrap gap-x-6 gap-y-3 text-sm text-fg-2">
              {([['wifiOff', 'Bekerja tanpa internet'], ['lock', 'Data terenkripsi di HP'], ['shield', 'Bukan alat diagnosis']] as const).map(([i, t]) => (
                <li key={t} className="flex items-center gap-2">
                  <span className="inline-flex h-7 w-7 items-center justify-center rounded-lg bg-surface text-accent shadow-soft ring-1 ring-line"><Icon name={i} size={15} /></span>
                  {t}
                </li>
              ))}
            </ul>
          </div>
          <div className="animate-fade-up [animation-delay:120ms]">
            <PhoneMock />
          </div>
        </div>
      </section>

      {/* ---------- Darurat ---------- */}
      <section aria-labelledby="darurat" className="mx-auto max-w-6xl px-4 sm:px-6">
        <div className="flex flex-col gap-4 rounded-2xl border border-red/30 bg-red-bg p-5 sm:flex-row sm:items-center sm:p-6">
          <span className="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-red text-white dark:text-[#1a0606]">
            <Icon name="alert" size={24} />
          </span>
          <div className="min-w-0 flex-1">
            <h2 id="darurat" className="text-base font-bold text-red-fg">Nyeri dada? Jangan menunggu.</h2>
            <p className="mt-1 text-sm leading-relaxed text-fg-2">
              Nyeri atau rasa tertekan di dada lebih dari beberapa menit, terutama disertai sesak napas, keringat dingin, mual, atau nyeri menjalar
              ke lengan, rahang, atau punggung, bisa menjadi tanda serangan jantung. Segera cari pertolongan — jangan menyetir sendiri.
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            {site.emergencyNumbers.map((n) => (
              <a key={n} href={`tel:${n}`} className="inline-flex h-12 items-center gap-2 rounded-xl bg-red px-5 text-base font-extrabold text-white shadow-soft hover:brightness-95 dark:text-[#1a0606]">
                <Icon name="phone" size={18} /> {n}
              </a>
            ))}
          </div>
        </div>
      </section>

      {/* ---------- Untuk siapa ---------- */}
      <section id="fitur" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-20 sm:px-6">
        <div className="max-w-2xl">
          <p className="text-xs font-bold tracking-[0.14em] text-accent uppercase">Satu alur data</p>
          <h2 className="mt-2 text-3xl font-extrabold tracking-tight text-fg">Warga, kader, dan Puskesmas saling terhubung</h2>
          <p className="mt-3 text-base leading-relaxed text-muted">
            Data mengalir dari warga ke kader Posyandu lalu ke Puskesmas — masing-masing hanya melihat yang dibutuhkan perannya.
          </p>
        </div>
        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {AUDIENCES.map((a, i) => (
            <article key={a.who} className={cx('group relative overflow-hidden rounded-2xl border border-line bg-surface p-6 shadow-soft transition-shadow hover:shadow-card animate-fade-up')} style={{ animationDelay: `${i * 80}ms` }}>
              <div className={cx('pointer-events-none absolute inset-x-0 top-0 h-28 bg-gradient-to-b to-transparent', a.tone)} />
              <div className="relative">
                <span className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-white shadow-soft"><Icon name={a.icon} size={21} /></span>
                <p className="mt-5 text-xs font-bold tracking-[0.12em] text-accent uppercase">{a.who}</p>
                <h3 className="mt-1 text-lg leading-snug font-bold text-fg">{a.title}</h3>
                <ul className="mt-4 space-y-2.5">
                  {a.points.map((p) => (
                    <li key={p} className="flex gap-2.5 text-sm leading-relaxed text-fg-2">
                      <Icon name="check" size={16} className="mt-0.5 shrink-0 text-wellness" /> {p}
                    </li>
                  ))}
                </ul>
              </div>
            </article>
          ))}
        </div>

        <div className="mt-10 rounded-2xl border border-line bg-surface p-5 sm:p-6">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="max-w-xs">
              <h3 className="text-base font-bold text-fg">Lima pilar jantung sehat</h3>
              <p className="mt-1 text-sm text-muted">Kebiasaan kecil yang dipantau dan dirayakan setiap hari.</p>
            </div>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-5 lg:flex-1">
              {PILLARS.map((p) => (
                <li key={p.label} className="flex items-center gap-2.5 rounded-xl bg-surface-2 px-3 py-3 text-sm font-semibold text-fg-2">
                  <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-wellness-soft text-green-fg"><Icon name={p.icon} size={16} /></span>
                  {p.label}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      {/* ---------- Cara kerja ---------- */}
      <section id="cara-kerja" className="scroll-mt-20 border-y border-line bg-surface">
        <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <div className="max-w-2xl">
            <p className="text-xs font-bold tracking-[0.14em] text-accent uppercase">Cara kerja</p>
            <h2 className="mt-2 text-3xl font-extrabold tracking-tight text-fg">Tiga langkah, dari rumah ke Puskesmas</h2>
          </div>
          <ol className="mt-10 grid gap-5 md:grid-cols-3">
            {STEPS.map((s, i) => (
              <li key={s.title} className="relative rounded-2xl border border-line bg-bg p-6">
                <div className="flex items-center gap-3">
                  <span className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-primary text-sm font-extrabold text-white">{i + 1}</span>
                  <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-primary-soft text-accent"><Icon name={s.icon} size={18} /></span>
                </div>
                <h3 className="mt-5 text-lg font-bold text-fg">{s.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-fg-2">{s.body}</p>
                {i < STEPS.length - 1 && (
                  <Icon name="chevronRight" size={22} className="absolute top-1/2 -right-4 hidden -translate-y-1/2 text-line-strong md:block" />
                )}
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* ---------- Offline & privasi ---------- */}
      <section id="privasi" className="mx-auto grid max-w-6xl scroll-mt-20 gap-5 px-4 py-20 sm:px-6 lg:grid-cols-2">
        <div className="rounded-2xl border border-line bg-surface p-6 sm:p-8">
          <span className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-primary-soft text-accent"><Icon name="wifiOff" size={21} /></span>
          <h2 className="mt-5 text-2xl font-extrabold tracking-tight text-fg">Dirancang untuk sinyal yang naik-turun</h2>
          <p className="mt-3 text-sm leading-relaxed text-fg-2">
            Semua fitur inti berjalan di HP tanpa internet. Setiap data diberi status yang jelas —
            <em className="not-italic font-semibold"> tersimpan di perangkat</em>, <em className="not-italic font-semibold">sedang disinkronkan</em>, atau
            <em className="not-italic font-semibold"> tersinkron</em> — dan dikirim ulang otomatis tanpa duplikasi saat koneksi kembali.
          </p>
          <ul className="mt-5 grid gap-2 text-sm text-fg-2 sm:grid-cols-2">
            {['Basis data terenkripsi (SQLCipher)', 'Kunci di Android Keystore', 'Sinkron idempoten & aman diulang', 'Jadwal Posyandu tersimpan offline'].map((t) => (
              <li key={t} className="flex items-center gap-2"><Icon name="check" size={16} className="text-wellness" /> {t}</li>
            ))}
          </ul>
        </div>
        <div className="rounded-2xl border border-line bg-surface p-6 sm:p-8">
          <span className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-wellness-soft text-green-fg"><Icon name="shield" size={21} /></span>
          <h2 className="mt-5 text-2xl font-extrabold tracking-tight text-fg">Privasi warga diutamakan</h2>
          <ul className="mt-4 space-y-3 text-sm leading-relaxed text-fg-2">
            <li className="flex gap-2.5"><Icon name="check" size={16} className="mt-0.5 shrink-0 text-wellness" />Data dikirim ke server hanya dengan persetujuan warga.</li>
            <li className="flex gap-2.5"><Icon name="check" size={16} className="mt-0.5 shrink-0 text-wellness" />Puskesmas melihat angka agregat; jumlah 1–4 disembunyikan.</li>
            <li className="flex gap-2.5"><Icon name="check" size={16} className="mt-0.5 shrink-0 text-wellness" />Pertanyaan ke Tanya SEHATI tidak disimpan di server.</li>
            <li className="flex gap-2.5"><Icon name="check" size={16} className="mt-0.5 shrink-0 text-wellness" />Warga dapat menghapus seluruh datanya kapan saja.</li>
          </ul>
          <Link to="/privasi" className={buttonClass('soft', 'md', 'mt-6')}>
            Baca kebijakan privasi <Icon name="arrowRight" size={16} />
          </Link>
        </div>
      </section>

      {/* ---------- Unduh ---------- */}
      <section id="unduh" className="mx-auto max-w-6xl scroll-mt-20 px-4 pb-20 sm:px-6">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#E11D48] via-[#BE123C] to-[#9F1239] px-6 py-12 text-white sm:px-12">
          <svg className="pointer-events-none absolute -top-16 -right-16 h-72 w-72 opacity-15" viewBox="0 0 64 64" aria-hidden="true">
            <circle cx="32" cy="32" r="30" fill="none" stroke="#fff" strokeWidth="2" />
            <circle cx="32" cy="32" r="22" fill="none" stroke="#fff" strokeWidth="1.2" />
          </svg>
          <div className="relative grid items-center gap-8 lg:grid-cols-[1.4fr_1fr]">
            <div>
              <h2 className="text-3xl font-extrabold tracking-tight">Pasang SEHATI di HP Android</h2>
              <p className="mt-3 max-w-xl text-sm leading-relaxed text-rose-100">
                Unduh berkas APK, buka, lalu izinkan pemasangan dari sumber ini bila diminta. Kader menerima akun dari Puskesmas;
                warga dapat mendaftar sendiri atau dibantu kader saat Posyandu.
              </p>
              <ol className="mt-5 grid gap-2 text-sm text-rose-50 sm:grid-cols-3">
                {['Unduh APK', 'Izinkan pemasangan', 'Buka & daftar'].map((t, i) => (
                  <li key={t} className="flex items-center gap-2">
                    <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-white/15 text-xs font-bold">{i + 1}</span>{t}
                  </li>
                ))}
              </ol>
            </div>
            <div className="flex flex-col items-start gap-3 lg:items-end">
              <a href={site.apk.url} download className="inline-flex h-13 items-center gap-2.5 rounded-xl bg-white px-6 py-3.5 text-base font-bold text-[#BE123C] shadow-pop transition-transform hover:-translate-y-0.5">
                <Icon name="download" size={20} /> Unduh sehati.apk
              </a>
              <p className="text-xs text-rose-100">Versi {site.apk.version} · {site.apk.minAndroid}</p>
            </div>
          </div>
        </div>
      </section>
    </PublicShell>
  )
}
