import { useId } from 'react'

/** Logo SEHATI: hati putih di dalam lingkaran biru bercincin. */
export function LogoMark({ size = 36, animated = false, className = '' }: { size?: number; animated?: boolean; className?: string }) {
  const gid = useId().replace(/:/g, '')
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="Logo SEHATI" className={className}>
      <defs>
        <linearGradient id={`g${gid}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#0EA5E9" />
          <stop offset="1" stopColor="#BE123C" />
        </linearGradient>
      </defs>
      <g className={animated ? 'ring-spin' : undefined}>
        <circle cx="32" cy="32" r="30" fill="none" stroke="#7DD3FC" strokeOpacity="0.55" strokeWidth="2.5" />
        <path d="M32 2a30 30 0 0 1 26 15" fill="none" stroke="#E11D48" strokeWidth="2.5" strokeLinecap="round" />
      </g>
      <circle cx="32" cy="32" r="24" fill={`url(#g${gid})`} />
      <g className={animated ? 'heartbeat' : undefined}>
        <path
          fill="#fff"
          d="M32 44.5c-.5 0-1-.2-1.4-.5C24.3 38.7 18 33.4 18 27.2c0-4.1 3.2-7.4 7.2-7.4 2.8 0 5.3 1.6 6.8 4 1.5-2.4 4-4 6.8-4 4 0 7.2 3.3 7.2 7.4 0 6.2-6.3 11.5-12.6 16.8-.4.3-.9.5-1.4.5z"
        />
      </g>
    </svg>
  )
}

export function Logo({ size = 36, subtitle = true, className = '' }: { size?: number; subtitle?: boolean; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <LogoMark size={size} />
      <span className="flex flex-col leading-none">
        <span className="text-[1.05rem] font-extrabold tracking-[0.08em] text-fg">SEHATI</span>
        {subtitle && <span className="mt-1 whitespace-nowrap text-[0.68rem] font-medium tracking-wide text-muted">Jantung sehat dari desa</span>}
      </span>
    </span>
  )
}
