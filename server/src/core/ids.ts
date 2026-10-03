/** Format SEHATI ID: prefix 2 huruf + 6 digit. HM = warga, KD = kader, AD = admin. */
export const ID_PATTERN = /^(HM|KD|AD)-\d{6}$/;
export const WARGA_ID = /^HM-\d{6}$/;

export function normalizeSehatiId(raw: string): string {
  return raw.trim().toUpperCase();
}

export function formatId(prefix: string, n: number): string {
  return `${prefix}-${String(n).padStart(6, '0')}`;
}

export function idNumber(id: string): number | null {
  const m = /^(?:HM|KD|AD)-(\d{6})$/.exec(id);
  return m ? Number(m[1]) : null;
}

export function isStaffId(id: string): boolean {
  return id.startsWith('KD-') || id.startsWith('AD-');
}
