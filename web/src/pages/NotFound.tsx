import { Link } from 'react-router'
import { PublicShell } from '../components/PublicShell'
import { LogoMark } from '../components/Logo'
import { buttonClass } from '../components/ui'

export function NotFoundPage() {
  return (
    <PublicShell>
      <div className="mx-auto flex max-w-lg flex-col items-center px-4 py-24 text-center animate-fade-up">
        <LogoMark size={64} />
        <p className="mt-6 text-sm font-bold tracking-[0.14em] text-accent uppercase">404</p>
        <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-fg">Halaman tidak ditemukan</h1>
        <p className="mt-3 text-sm leading-relaxed text-muted">Alamat yang Anda buka tidak tersedia. Periksa kembali tautannya atau kembali ke beranda.</p>
        <Link to="/" className={buttonClass('primary', 'md', 'mt-8')}>Kembali ke beranda</Link>
      </div>
    </PublicShell>
  )
}
