/** Tanggal kalender (YYYY-MM-DD) dari epoch ms pada zona waktu tertentu (bawaan Asia/Jakarta). */
export function localDate(ms: number, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(ms);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}

/** Bulan kalender YYYY-MM. */
export function localMonth(ms: number, timeZone: string): string {
  return localDate(ms, timeZone).slice(0, 7);
}

/** Tambah n hari ke tanggal YYYY-MM-DD (aritmetika kalender, bebas zona waktu). */
export function addDays(isoDate: string, n: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

export const DAY_MS = 86_400_000;
