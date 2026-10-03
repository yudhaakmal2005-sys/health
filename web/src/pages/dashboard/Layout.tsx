import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { Icon, type IconName } from '../../components/Icon'
import { Logo } from '../../components/Logo'
import { MockBanner } from '../../components/PublicShell'
import { ThemeToggle } from '../../components/ThemeToggle'
import { IconButton, cx } from '../../components/ui'
import { useOverview } from '../../lib/hooks'
import { site } from '../../site.config'

interface NavItem { to: string; label: string; icon: IconName; end?: boolean; badge?: 'overdue' }

const NAV: { title: string; items: NavItem[] }[] = [
  {
    title: 'Pemantauan',
    items: [
      { to: '/dasbor', label: 'Ringkasan', icon: 'grid', end: true },
      { to: '/dasbor/peta-rw', label: 'Peta RW', icon: 'map' },
      { to: '/dasbor/tindak-lanjut', label: 'Tindak lanjut', icon: 'clipboard', badge: 'overdue' },
    ],
  },
  {
    title: 'Pengelolaan',
    items: [
      { to: '/dasbor/kader', label: 'Kader', icon: 'users' },
      { to: '/dasbor/jadwal', label: 'Jadwal Posyandu', icon: 'calendar' },
      { to: '/dasbor/ambang', label: 'Ambang klinis', icon: 'sliders' },
    ],
  },
  {
    title: 'Laporan & audit',
    items: [
      { to: '/dasbor/laporan', label: 'Laporan', icon: 'file' },
      { to: '/dasbor/audit', label: 'Audit log', icon: 'history' },
      { to: '/dasbor/ai', label: 'Pemakaian AI', icon: 'sparkles' },
    ],
  },
]

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { data } = useOverview()
  const overdue = data?.followUpOverdue
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 shrink-0 items-center px-5">
        <NavLink to="/" className="rounded-lg" aria-label="SEHATI — beranda publik"><Logo size={34} /></NavLink>
      </div>
      <nav aria-label="Menu dashboard" className="flex-1 overflow-y-auto px-3 pb-4">
        {NAV.map((g) => (
          <div key={g.title} className="mt-5 first:mt-2">
            <p className="px-3 pb-1.5 text-[0.68rem] font-bold tracking-[0.12em] text-muted uppercase">{g.title}</p>
            <ul className="space-y-0.5">
              {g.items.map((it) => (
                <li key={it.to}>
                  <NavLink
                    to={it.to}
                    end={it.end}
                    onClick={onNavigate}
                    className={({ isActive }) => cx(
                      'group flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-semibold transition-colors',
                      isActive ? 'bg-primary-soft text-accent' : 'text-fg-2 hover:bg-surface-2 hover:text-fg',
                    )}
                  >
                    {({ isActive }) => (
                      <>
                        <Icon name={it.icon} size={18} className={isActive ? 'text-accent' : 'text-muted group-hover:text-fg-2'} />
                        <span className="flex-1">{it.label}</span>
                        {it.badge === 'overdue' && overdue !== undefined && overdue !== 0 && (
                          <span className="rounded-full bg-red-bg px-1.5 py-px text-[0.68rem] font-bold text-red-fg tabular" title="Tindak lanjut melewati jadwal">
                            {overdue === null ? '< 5' : overdue}
                            <span className="sr-only"> melewati jadwal</span>
                          </span>
                        )}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>
      <div className="m-3 rounded-2xl border border-line bg-surface-2 p-3.5 text-xs leading-relaxed text-muted">
        <p className="flex items-center gap-1.5 font-semibold text-fg-2"><Icon name="shield" size={14} /> Privasi terjaga</p>
        <p className="mt-1">Hanya angka agregat. Jumlah 1–4 ditampilkan sebagai <span className="hatch rounded px-1 font-semibold text-fg-2">&lt; 5</span>.</p>
      </div>
    </div>
  )
}

export default function DashboardLayout() {
  const { session, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const drawerRef = useRef<HTMLDivElement>(null)
  const menuBtn = useRef<HTMLButtonElement>(null)

  useEffect(() => setOpen(false), [location.pathname])
  useEffect(() => {
    if (!open) return
    const prev = document.activeElement as HTMLElement | null
    drawerRef.current?.querySelector<HTMLElement>('a,button')?.focus()
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
      ;(prev ?? menuBtn.current)?.focus()
    }
  }, [open])

  const user = session?.user
  const initials = (user?.fullName ?? 'A').split(/\s+/).filter((w) => /^[A-Za-z]/.test(w)).slice(0, 2).map((w) => w[0]).join('').toUpperCase()

  return (
    <div className="min-h-dvh">
      <MockBanner />
      <a href="#konten" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[70] focus:rounded-lg focus:bg-surface focus:px-3 focus:py-2 focus:text-sm focus:font-semibold focus:shadow-pop">
        Lewati ke konten
      </a>
      {/* Sidebar desktop */}

      {/* Laci seluler */}
      {open && (
        <div className="no-print fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu dashboard">
          <div className="absolute inset-0 bg-slate-950/45 backdrop-blur-[2px] animate-fade-in" onClick={() => setOpen(false)} />
          <div ref={drawerRef} className="absolute inset-y-0 left-0 w-[min(18rem,85vw)] bg-surface shadow-pop animate-slide-in">
            <IconButton icon="x" label="Tutup menu" className="absolute top-3 right-3 z-10" onClick={() => setOpen(false)} />
            <Sidebar onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex">
      <aside className="no-print z-30 hidden w-64 shrink-0 border-r border-line bg-surface lg:block">
        <div className="sticky top-0 h-dvh">
          <Sidebar />
        </div>
      </aside>
      <div className="min-w-0 flex-1">
        <header className="no-print sticky top-0 z-20 border-b border-line bg-bg/85 backdrop-blur-md">
          <div className="flex h-16 items-center gap-3 px-4 sm:px-6 lg:px-8">
            <button
              ref={menuBtn}
              type="button"
              onClick={() => setOpen(true)}
              aria-label="Buka menu"
              aria-expanded={open}
              className="-ml-1 inline-flex h-10 w-10 items-center justify-center rounded-lg text-fg-2 hover:bg-surface-2 lg:hidden"
            >
              <Icon name="menu" size={20} />
            </button>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-fg">Dashboard Puskesmas</p>
              <p className="truncate text-xs text-muted">{user?.village ?? site.village}</p>
            </div>
            <ThemeToggle className="max-sm:hidden" />
            <div className="flex items-center gap-2.5 rounded-full border border-line bg-surface py-1 pr-1 pl-1">
              <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-white" aria-hidden="true">{initials}</span>
              <span className="hidden min-w-0 pr-1 leading-tight md:block">
                <span className="block max-w-[11rem] truncate text-[0.8125rem] font-semibold text-fg">{user?.fullName}</span>
                <span className="block text-[0.7rem] text-muted">{user?.sehatiId} · Admin</span>
              </span>
              <IconButton icon="logout" label="Keluar" size="sm" onClick={() => void logout()} />
            </div>
          </div>
        </header>
        <main id="konten" className="mx-auto max-w-[1280px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <div key={location.pathname} className="animate-fade-in">
            <Outlet />
          </div>
          <div className="mt-10 flex justify-center sm:hidden">
            <ThemeToggle />
          </div>
        </main>
      </div>
      </div>
    </div>
  )
}
