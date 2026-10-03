import { useTheme, type ThemePref } from '../lib/theme'
import { Icon, type IconName } from './Icon'
import { cx } from './ui'

const OPTS: { value: ThemePref; icon: IconName; label: string }[] = [
  { value: 'light', icon: 'sun', label: 'Tema terang' },
  { value: 'system', icon: 'monitor', label: 'Ikuti sistem' },
  { value: 'dark', icon: 'moon', label: 'Tema gelap' },
]

export function ThemeToggle({ className }: { className?: string }) {
  const { pref, choose } = useTheme()
  return (
    <div role="radiogroup" aria-label="Tema tampilan" className={cx('inline-flex rounded-full border border-line bg-surface-2 p-0.5', className)}>
      {OPTS.map((o) => {
        const active = pref === o.value
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={active}
            aria-label={o.label}
            title={o.label}
            onClick={() => choose(o.value)}
            className={cx(
              'inline-flex h-7 w-7 items-center justify-center rounded-full transition-colors',
              active ? 'bg-surface text-accent shadow-soft' : 'text-muted hover:text-fg',
            )}
          >
            <Icon name={o.icon} size={15} />
          </button>
        )
      })}
    </div>
  )
}
