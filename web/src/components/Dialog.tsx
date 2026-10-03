import { useEffect, useRef, type ReactNode } from 'react'
import { Button, IconButton, cx } from './ui'
import { Icon, type IconName } from './Icon'

/** Dialog modal berbasis <dialog> bawaan: fokus terkunci, Esc menutup, latar diredupkan. */
export function Dialog({ open, onClose, title, description, children, footer, size = 'md', icon, tone = 'primary' }: {
  open: boolean
  onClose: () => void
  title: string
  description?: ReactNode
  children?: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  icon?: IconName
  tone?: 'primary' | 'red'
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose()
      }}
      aria-labelledby="dlg-title"
      className={cx(
        'm-auto w-[calc(100%-2rem)] rounded-2xl border border-line bg-surface p-0 text-fg shadow-pop',
        'backdrop:bg-slate-950/45 backdrop:backdrop-blur-[2px] open:animate-pop-in',
        size === 'sm' && 'max-w-md',
        size === 'md' && 'max-w-lg',
        size === 'lg' && 'max-w-2xl',
      )}
    >
      {open && (
        <div className="flex max-h-[min(88dvh,760px)] flex-col">
          <div className="flex items-start gap-3 border-b border-line px-5 py-4 sm:px-6">
            {icon && (
              <span className={cx('mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl', tone === 'red' ? 'bg-red-bg text-red-fg' : 'bg-primary-soft text-accent')}>
                <Icon name={icon} size={18} />
              </span>
            )}
            <div className="min-w-0 flex-1">
              <h2 id="dlg-title" className="text-base font-bold">{title}</h2>
              {description && <p className="mt-0.5 text-[0.8125rem] leading-relaxed text-muted">{description}</p>}
            </div>
            <IconButton icon="x" label="Tutup" size="sm" onClick={onClose} className="-mt-1 -mr-2" />
          </div>
          {children && <div className="overflow-y-auto px-5 py-5 sm:px-6">{children}</div>}
          {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-line bg-surface-2/60 px-5 py-3.5 sm:px-6">{footer}</div>}
        </div>
      )}
    </dialog>
  )
}

export function ConfirmDialog({ open, onClose, onConfirm, title, children, confirmLabel = 'Ya, lanjutkan', loading, danger }: {
  open: boolean; onClose: () => void; onConfirm: () => void; title: string; children: ReactNode; confirmLabel?: string; loading?: boolean; danger?: boolean
}) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      size="sm"
      icon={danger ? 'alert' : 'info'}
      tone={danger ? 'red' : 'primary'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Batal</Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>{confirmLabel}</Button>
        </>
      }
    >
      <div className="text-sm leading-relaxed text-fg-2">{children}</div>
    </Dialog>
  )
}
