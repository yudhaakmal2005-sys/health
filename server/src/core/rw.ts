/** RW ditulis dua digit ("02"). */
export function normalizeRw(raw: string): string {
  const t = raw.trim();
  return /^\d{1,3}$/.test(t) ? t.padStart(2, '0') : t;
}

export const RW_PATTERN = /^\d{1,3}$/;

/**
 * RW tugas kader dapat berupa satu RW ("02"), daftar ("01,03") atau rentang ("04-06")
 * — aplikasi memakai format rentang pada entitas cadre.
 */
export function expandRwSpec(spec: string): string[] {
  const out = new Set<string>();
  for (const part of spec.split(',').map((s) => s.trim()).filter(Boolean)) {
    const m = /^(\d{1,3})\s*-\s*(\d{1,3})$/.exec(part);
    if (m) {
      const a = Number(m[1]);
      const b = Number(m[2]);
      if (b >= a && b - a <= 50) for (let i = a; i <= b; i++) out.add(String(i).padStart(2, '0'));
    } else if (RW_PATTERN.test(part)) {
      out.add(normalizeRw(part));
    }
  }
  return [...out];
}

export function isValidRwSpec(spec: string): boolean {
  return expandRwSpec(spec).length > 0 && /^[\d,\s-]+$/.test(spec);
}
