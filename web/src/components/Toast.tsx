import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { Icon } from './Icon'
import { cx } from './ui'

type ToastTone = 'success' | 'error' | 'info'
interface ToastItem { id: number; tone: ToastTone; title: string; message?: string }

interface ToastApi {
  success: (title: string, message?: string) => void
  error: (title: string, message?: string) => void
  info: (title: string, message?: string) => void
}

const Ctx = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const dismiss = useCallback((id: number) => setItems((xs) => xs.filter((x) => x.id !== id)), [])
  const push = useCallback((tone: ToastTone, title: string, message?: string) => {
    const id = Date.now() + Math.random()
    setItems((xs) => [...xs.slice(-3), { id, tone, title, message }])
    window.setTimeout(() => dismiss(id), tone === 'error' ? 7000 : 4200)
  }, [dismiss])

  const api = useMemo<ToastApi>(() => ({
    success: (t, m) => push('success', t, m),
    error: (t, m) => push('error', t, m),
    info: (t, m) => push('info', t, m),
  }), [push])

  return (
    <Ctx.Provider value={api}>
      {children}
      <div className="no-print pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-center gap-2 p-4 sm:items-end sm:p-6" aria-live="polite" aria-atomic="false">
        {items.map((t) => (
          <div
            key={t.id}
            role={t.tone === 'error' ? 'alert' : 'status'}
            className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl border border-line bg-surface p-3.5 pr-2 shadow-pop animate-pop-in"
          >
            <span className={cx(
              'mt-px inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full',
              t.tone === 'success' && 'bg-green-bg text-green-fg',
              t.tone === 'error' && 'bg-red-bg text-red-fg',
              t.tone === 'info' && 'bg-primary-soft text-accent',
            )}>
              <Icon name={t.tone === 'success' ? 'check' : t.tone === 'error' ? 'alert' : 'info'} size={15} />
            </span>
            <div className="min-w-0 flex-1 pt-0.5">
              <p className="text-sm font-semibold text-fg">{t.title}</p>
              {t.message && <p className="mt-0.5 text-[0.8125rem] leading-snug text-muted">{t.message}</p>}
            </div>
            <button type="button" onClick={() => dismiss(t.id)} className="rounded-lg p-1.5 text-muted hover:bg-surface-2 hover:text-fg" aria-label="Tutup notifikasi">
              <Icon name="x" size={15} />
            </button>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  )
}

export function useToast(): ToastApi {
  const v = useContext(Ctx)
  if (!v) throw new Error('useToast di luar ToastProvider')
  return v
}
