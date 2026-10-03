import { useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { site } from '../site.config'
import { IS_MOCK } from '../api/client'
import { Logo } from './Logo'
import { ThemeToggle } from './ThemeToggle'
import { Icon } from './Icon'
import { buttonClass, cx } from './ui'

export function MockBanner() {
  if (!IS_MOCK) return null
  return (
    <div className="no-print bg-yellow-bg px-4 py-1.5 text-center text-xs font-semibold text-yellow-fg">
      MODE DEMO — semua data di halaman ini sintetis dan disajikan di peramban, bukan data warga sungguhan.
    </div>
  )
}

export function PublicHeader() {
  const [scrolled, setScrolled] = useState(false)
  const [open, setOpen] = useState(false)
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 8)
    on()
    window.addEventListener('scroll', on, { passive: true })
    return () => window.removeEventListener('scroll', on)
  }, [])
  const links = [
    { to: '/#fitur', label: 'Fitur' },
    { to: '/#cara-kerja', label: 'Cara kerja' },
    { to: '/#privasi', label: 'Privasi' },
    { to: '/#unduh', label: 'Unduh' },
  ]
  return (
    <header className={cx('sticky top-0 z-40 transition-[background-color,box-shadow,border-color] duration-200', scrolled || open ? 'border-b border-line bg-bg/85 backdrop-blur-md' : 'border-b border-transparent')}>
      <a href="#konten" className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-lg focus:bg-surface focus:px-3 focus:py-2 focus:text-sm focus:font-semibold focus:shadow-pop">
        Lewati ke konten
      </a>
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Link to="/" aria-label="SEHATI — beranda" className="rounded-lg">
          <Logo size={34} />
        </Link>
        <nav aria-label="Navigasi utama" className="hidden items-center gap-1 md:flex">
          {links.map((l) => (
            <Link key={l.to} to={l.to} className="rounded-lg px-3 py-2 text-sm font-medium text-fg-2 transition-colors hover:bg-surface-2 hover:text-fg">
              {l.label}
            </Link>
          ))}
        </nav>
        <div className="flex items-center gap-2">
          <ThemeToggle className="max-sm:hidden" />
          <Link to="/masuk" className={buttonClass('secondary', 'sm', 'max-sm:hidden')}>
            <Icon name="lock" size={15} /> Masuk staf
          </Link>
          <button
            type="button"
            className="inline-flex h-10 w-10 items-center justify-center rounded-lg text-fg-2 hover:bg-surface-2 md:hidden"
            aria-expanded={open}
            aria-controls="menu-publik"
            aria-label={open ? 'Tutup menu' : 'Buka menu'}
            onClick={() => setOpen((v) => !v)}
          >
            <Icon name={open ? 'x' : 'menu'} size={20} />
          </button>
        </div>
      </div>
      {open && (
        <div id="menu-publik" className="border-t border-line px-4 pt-2 pb-4 md:hidden animate-fade-in">
          <nav aria-label="Navigasi seluler" className="flex flex-col">
            {links.map((l) => (
              <Link key={l.to} to={l.to} onClick={() => setOpen(false)} className="rounded-lg px-3 py-2.5 text-[0.95rem] font-medium text-fg-2 hover:bg-surface-2">
                {l.label}
              </Link>
            ))}
            <NavLink to="/privasi" onClick={() => setOpen(false)} className="rounded-lg px-3 py-2.5 text-[0.95rem] font-medium text-fg-2 hover:bg-surface-2">
              Kebijakan privasi
            </NavLink>
          </nav>
          <div className="mt-3 flex items-center justify-between gap-3 px-1">
            <ThemeToggle />
            <Link to="/masuk" className={buttonClass('secondary', 'sm')}>
              <Icon name="lock" size={15} /> Masuk staf
            </Link>
          </div>
        </div>
      )}
    </header>
  )
}

export function PublicFooter() {
  return (
    <footer className="border-t border-line bg-surface">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-[1.4fr_1fr_1fr]">
        <div>
          <Logo size={38} />
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-muted">
            {site.tagline} untuk {site.village}. SEHATI adalah alat bantu edukasi dan pemantauan —
            <strong className="font-semibold text-fg-2"> bukan alat diagnosis</strong>. Keputusan medis tetap oleh tenaga kesehatan.
          </p>
        </div>
        <div>
          <h2 className="text-sm font-bold text-fg">Kontak</h2>
          <ul className="mt-3 space-y-2 text-sm text-muted">
            <li className="flex items-start gap-2"><Icon name="pin" size={16} className="mt-0.5 shrink-0" />{site.contact.address}, {site.village}</li>
            <li className="flex items-start gap-2"><Icon name="message" size={16} className="mt-0.5 shrink-0" /><a className="hover:text-accent" href={`mailto:${site.contact.email}`}>{site.contact.email}</a></li>
            <li className="flex items-start gap-2"><Icon name="phone" size={16} className="mt-0.5 shrink-0" /><a className="hover:text-accent" href={`tel:${site.contact.phone.replace(/\D/g, '')}`}>{site.contact.phone}</a></li>
          </ul>
        </div>
        <div>
          <h2 className="text-sm font-bold text-fg">Tautan</h2>
          <ul className="mt-3 space-y-2 text-sm text-muted">
            <li><a className="hover:text-accent" href={site.apk.url} download>Unduh aplikasi Android</a></li>
            <li><Link className="hover:text-accent" to="/privasi">Kebijakan privasi</Link></li>
            <li><Link className="hover:text-accent" to="/masuk">Masuk staf Puskesmas</Link></li>
          </ul>
        </div>
      </div>
      <div className="border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-5 text-xs text-muted sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p>{site.kkn.credit} <span className="font-semibold text-fg-2">{site.kkn.team}</span>, {site.kkn.university} · {site.kkn.year}</p>
          <p>Darurat: hubungi <a className="font-semibold text-red-fg" href="tel:119">119</a> atau <a className="font-semibold text-red-fg" href="tel:112">112</a></p>
        </div>
      </div>
    </footer>
  )
}

export function PublicShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <MockBanner />
      <PublicHeader />
      <main id="konten" className="flex-1">{children}</main>
      <PublicFooter />
    </div>
  )
}
