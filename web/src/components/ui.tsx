import {
  forwardRef, useEffect, useId, useRef, useState,
  type ButtonHTMLAttributes, type HTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes,
} from 'react'
import type { Tone } from '../lib/domain'
import type { ApiError } from '../api/client'
import { Icon, type IconName } from './Icon'
import { SUPPRESSED_LABEL } from '../lib/format'

export function cx(...c: (string | false | null | undefined)[]) {
  return c.filter(Boolean).join(' ')
}

/* ---------------- Tombol ---------------- */

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'soft'
type Size = 'sm' | 'md' | 'lg'

export function buttonClass(variant: Variant = 'primary', size: Size = 'md', extra = '') {
  return cx(
    'inline-flex items-center justify-center gap-2 rounded-xl font-semibold whitespace-nowrap select-none',
    'transition-[background-color,border-color,color,box-shadow,transform] duration-150 active:translate-y-px',
    'disabled:opacity-55 disabled:pointer-events-none',
    size === 'sm' && 'h-8 px-3 text-[0.8125rem]',
    size === 'md' && 'h-10 px-4 text-sm',
    size === 'lg' && 'h-12 px-6 text-[0.95rem]',
    variant === 'primary' && 'bg-primary text-on-primary shadow-soft hover:bg-primary-strong',
    variant === 'secondary' && 'border border-line-strong bg-surface text-fg hover:bg-surface-2 hover:border-muted/50',
    variant === 'ghost' && 'text-fg-2 hover:bg-surface-2 hover:text-fg',
    variant === 'soft' && 'bg-primary-soft text-accent hover:brightness-95 dark:hover:brightness-125',
    variant === 'danger' && 'bg-red text-white shadow-soft hover:brightness-95 dark:text-[#1a0606]',
    extra,
  )
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  icon?: IconName
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', icon, loading, className, children, disabled, type = 'button', ...rest },
  ref,
) {
  return (
    <button ref={ref} type={type} className={buttonClass(variant, size, className)} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading ? <Spinner size={size === 'sm' ? 14 : 16} /> : icon && <Icon name={icon} size={size === 'sm' ? 15 : 17} />}
      {children}
    </button>
  )
})

export function IconButton({ icon, label, className, size = 'md', ...rest }: { icon: IconName; label: string; size?: 'sm' | 'md' } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={cx(
        'inline-flex items-center justify-center rounded-lg text-fg-2 transition-colors hover:bg-surface-2 hover:text-fg disabled:opacity-50',
        size === 'sm' ? 'h-8 w-8' : 'h-10 w-10',
        className,
      )}
      {...rest}
    >
      <Icon name={icon} size={size === 'sm' ? 16 : 19} />
    </button>
  )
}

export function Spinner({ size = 16, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" className={cx('animate-spin', className)} aria-hidden="true">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}

/* ---------------- Kartu & tata letak ---------------- */

export function Card({ className, children, as: As = 'section', ...rest }: { as?: 'section' | 'div' | 'article' } & HTMLAttributes<HTMLElement>) {
  return (
    <As className={cx('rounded-2xl border border-line bg-surface shadow-soft', className)} {...rest}>
      {children}
    </As>
  )
}

export function CardHeader({ title, description, action, icon }: { title: ReactNode; description?: ReactNode; action?: ReactNode; icon?: IconName }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 px-5 pt-5 sm:px-6">
      <div className="flex min-w-0 items-start gap-3">
        {icon && (
          <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-accent">
            <Icon name={icon} size={18} />
          </span>
        )}
        <div className="min-w-0">
          <h2 className="text-[0.975rem] font-bold text-fg">{title}</h2>
          {description && <p className="mt-0.5 text-[0.8125rem] leading-relaxed text-muted">{description}</p>}
        </div>
      </div>
      {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
    </div>
  )
}

export function PageHeader({ title, description, actions, eyebrow }: { title: string; description?: ReactNode; actions?: ReactNode; eyebrow?: string }) {
  return (
    <header className="mb-6 flex flex-col gap-4 animate-fade-up sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow && <p className="mb-1 text-xs font-semibold tracking-[0.12em] text-accent uppercase">{eyebrow}</p>}
        <h1 className="text-[1.6rem] leading-tight font-extrabold tracking-tight text-fg">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-sm leading-relaxed text-muted">{description}</p>}
      </div>
      {actions && <div className="no-print flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

/* ---------------- Lencana ---------------- */

const TONE_CLASSES: Record<Tone, string> = {
  green: 'bg-green-bg text-green-fg',
  yellow: 'bg-yellow-bg text-yellow-fg',
  orange: 'bg-orange-bg text-orange-fg',
  red: 'bg-red-bg text-red-fg',
  primary: 'bg-primary-soft text-accent',
  neutral: 'bg-surface-2 text-fg-2',
}
const TONE_DOT: Record<Tone, string> = {
  green: 'bg-green', yellow: 'bg-yellow', orange: 'bg-orange', red: 'bg-red', primary: 'bg-primary', neutral: 'bg-muted',
}

export function Badge({ tone = 'neutral', children, dot = true, className }: { tone?: Tone; children: ReactNode; dot?: boolean; className?: string }) {
  return (
    <span className={cx('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap', TONE_CLASSES[tone], className)}>
      {dot && <span className={cx('h-1.5 w-1.5 rounded-full', TONE_DOT[tone])} aria-hidden="true" />}
      {children}
    </span>
  )
}

export function toneDot(tone: Tone) {
  return TONE_DOT[tone]
}

/** Nilai agregat yang disembunyikan. */
export function Suppressed({ className }: { className?: string }) {
  return (
    <span className={cx('hatch inline-flex items-center rounded-md px-1.5 py-0.5 text-[0.8em] font-semibold text-fg-2', className)} title="Disembunyikan: jumlah 1–4 tidak ditampilkan untuk melindungi privasi">
      {SUPPRESSED_LABEL}
      <span className="sr-only"> (disembunyikan untuk privasi)</span>
    </span>
  )
}

/* ---------------- Formulir ---------------- */

const fieldBase =
  'w-full rounded-xl border bg-surface text-fg text-sm placeholder:text-muted/80 transition-[border-color,box-shadow] ' +
  'focus:outline-none focus:ring-4 focus:ring-primary/15 focus:border-primary disabled:opacity-60 disabled:bg-surface-2'

export function Field({ label, hint, error, children, htmlFor, required, className }: {
  label: ReactNode; hint?: ReactNode; error?: string | null; children: ReactNode; htmlFor: string; required?: boolean; className?: string
}) {
  return (
    <div className={cx('flex flex-col gap-1.5', className)}>
      <label htmlFor={htmlFor} className="text-[0.8125rem] font-semibold text-fg-2">
        {label}
        {required && <span className="text-red" aria-hidden="true"> *</span>}
      </label>
      {children}
      {error ? (
        <p id={`${htmlFor}-err`} className="flex items-start gap-1 text-xs font-medium text-red-fg" role="alert">
          <Icon name="alert" size={14} className="mt-px shrink-0" /> {error}
        </p>
      ) : hint ? (
        <p id={`${htmlFor}-hint`} className="text-xs text-muted">{hint}</p>
      ) : null}
    </div>
  )
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean; suffix?: ReactNode }>(
  function Input({ className, invalid, suffix, id, ...rest }, ref) {
    const input = (
      <input
        ref={ref}
        id={id}
        aria-invalid={invalid || undefined}
        aria-describedby={id ? (invalid ? `${id}-err` : `${id}-hint`) : undefined}
        className={cx(fieldBase, 'h-11 px-3.5', invalid ? 'border-red focus:border-red focus:ring-red/15' : 'border-line-strong', suffix ? 'pr-16' : '', className)}
        {...rest}
      />
    )
    if (!suffix) return input
    return (
      <div className="relative">
        {input}
        <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-xs font-medium text-muted">{suffix}</span>
      </div>
    )
  },
)

export function Select({ className, invalid, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <div className={cx('relative', className)}>
      <select
        aria-invalid={invalid || undefined}
        className={cx(fieldBase, 'h-11 appearance-none pr-9 pl-3.5', invalid ? 'border-red' : 'border-line-strong')}
        {...rest}
      >
        {children}
      </select>
      <Icon name="chevronDown" size={16} className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-muted" />
    </div>
  )
}

export function Textarea({ className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cx(fieldBase, 'min-h-[84px] border-line-strong px-3.5 py-2.5', className)} {...rest} />
}

/* ---------------- Status ---------------- */

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('skeleton', className)} aria-hidden="true" />
}

export function SkeletonRows({ rows = 6 }: { rows?: number }) {
  return (
    <div className="space-y-3 p-5" role="status" aria-label="Memuat data">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-4">
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-20" />
        </div>
      ))}
    </div>
  )
}

export function EmptyState({ icon = 'clipboard', title, children, action }: { icon?: IconName; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center animate-fade-in">
      <span className="mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-soft text-accent">
        <Icon name={icon} size={26} />
      </span>
      <h3 className="text-base font-bold text-fg">{title}</h3>
      {children && <p className="mt-1.5 max-w-sm text-sm leading-relaxed text-muted">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry, compact }: { error: ApiError; onRetry?: () => void; compact?: boolean }) {
  return (
    <div role="alert" className={cx('flex flex-col items-center text-center animate-fade-in', compact ? 'px-4 py-8' : 'px-6 py-14')}>
      <span className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-red-bg text-red-fg">
        <Icon name={error.status === 0 ? 'wifiOff' : 'alert'} size={24} />
      </span>
      <h3 className="text-base font-bold text-fg">{error.status === 0 ? 'Tidak dapat terhubung' : 'Data belum dapat dimuat'}</h3>
      <p className="mt-1.5 max-w-sm text-sm leading-relaxed text-muted">{error.message}</p>
      {onRetry && (
        <Button variant="secondary" icon="refresh" className="mt-5" onClick={onRetry}>
          Coba lagi
        </Button>
      )}
    </div>
  )
}

export function Callout({ tone = 'primary', icon, title, children, className }: { tone?: Tone; icon?: IconName; title?: ReactNode; children: ReactNode; className?: string }) {
  const border: Record<Tone, string> = {
    primary: 'border-primary/25', green: 'border-green/30', yellow: 'border-yellow/35', orange: 'border-orange/35', red: 'border-red/35', neutral: 'border-line',
  }
  return (
    <div className={cx('flex gap-3 rounded-2xl border p-4', TONE_CLASSES[tone], border[tone], className)}>
      {icon && <Icon name={icon} size={20} className="mt-0.5 shrink-0" />}
      <div className="min-w-0 text-sm leading-relaxed">
        {title && <p className="font-bold">{title}</p>}
        <div className={cx(title ? 'mt-0.5' : '', 'opacity-95')}>{children}</div>
      </div>
    </div>
  )
}

/* ---------------- Angka menghitung naik ---------------- */

function prefersReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function CountUp({ value, duration = 900, format = (n: number) => new Intl.NumberFormat('id-ID').format(n) }: {
  value: number; duration?: number; format?: (n: number) => string
}) {
  const [shown, setShown] = useState(() => (prefersReducedMotion() ? value : 0))
  const from = useRef(0)
  useEffect(() => {
    if (prefersReducedMotion()) {
      setShown(value)
      return
    }
    const start = performance.now()
    const a = from.current
    let raf = 0
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / duration)
      const eased = 1 - Math.pow(1 - p, 3)
      setShown(Math.round(a + (value - a) * eased))
      if (p < 1) raf = requestAnimationFrame(tick)
      else from.current = value
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [value, duration])
  return (
    <>
      <span aria-hidden="true">{format(shown)}</span>
      <span className="sr-only">{format(value)}</span>
    </>
  )
}

/* ---------------- Segmented control ---------------- */

export function Segmented<T extends string>({ value, onChange, options, label, size = 'md' }: {
  value: T; onChange: (v: T) => void; options: { value: T; label: ReactNode }[]; label: string; size?: 'sm' | 'md'
}) {
  const name = useId()
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex max-w-full overflow-x-auto rounded-xl border border-line bg-surface-2 p-1">
      {options.map((o) => {
        const active = o.value === value
        return (
          <label
            key={o.value}
            className={cx(
              'relative cursor-pointer rounded-lg font-semibold whitespace-nowrap transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-ring',
              size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3 py-1.5 text-[0.8125rem]',
              active ? 'bg-surface text-fg shadow-soft' : 'text-muted hover:text-fg',
            )}
          >
            <input type="radio" name={name} value={o.value} checked={active} onChange={() => onChange(o.value)} className="sr-only" />
            {o.label}
          </label>
        )
      })}
    </div>
  )
}

export function Switch({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cx(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors disabled:opacity-50',
        checked ? 'bg-wellness' : 'bg-line-strong',
      )}
    >
      <span className={cx('inline-block h-5 w-5 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-[22px]' : 'translate-x-0.5')} />
    </button>
  )
}
