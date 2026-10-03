/** Tipe entitas sinkronisasi dan aturan RBAC push (docs/API.md §4). */

/** Data kesehatan milik warga yang boleh dikirim warga sendiri. */
export const WARGA_HEALTH_TYPES = [
  'profile', 'assessment', 'measurement', 'measurement_detail', 'activity', 'sleep', 'food', 'habit', 'smoking',
  'education', 'challenge', 'medication', 'medlog',
] as const;

/** Tipe milik warga yang hanya boleh dikirim warga itu sendiri (kader tidak). */
export const WARGA_ONLY_TYPES = new Set<string>(['medication', 'medlog']);

/** Tipe kerja kader (Posyandu / tindak lanjut). */
export const KADER_WORK_TYPES = ['household', 'visit', 'followup', 'referral', 'homevisit'] as const;

/** Tipe tanpa subjek warga. */
export const SUBJECTLESS_TYPES = new Set<string>(['cadre', 'logistics']);

export const ALL_TYPES = new Set<string>([
  'user', 'credential', ...WARGA_HEALTH_TYPES, ...KADER_WORK_TYPES, 'cadre', 'logistics',
]);

export const WARGA_PUSH = new Set<string>(['user', 'credential', ...WARGA_HEALTH_TYPES]);
export const KADER_PUSH = new Set<string>([
  'user', 'credential', ...WARGA_HEALTH_TYPES.filter((t) => !WARGA_ONLY_TYPES.has(t)), ...KADER_WORK_TYPES,
]);
export const ADMIN_PUSH = new Set<string>(['cadre', 'logistics', 'followup']);

/** Tipe yang dapat ditarik ADMIN (tanpa data kesehatan individu). */
export const ADMIN_PULL = ['cadre', 'logistics', 'followup'] as const;

/** Field followup yang boleh diubah ADMIN (penugasan/penjadwalan). */
export const ADMIN_FOLLOWUP_FIELDS = ['assignedCadreId', 'dueAt', 'status', 'priority', 'type', 'closedAt', 'updatedAt'] as const;

export const FOLLOWUP_STATUSES = ['OPEN', 'SCHEDULED', 'IN_PROGRESS', 'DONE', 'CANCELLED'] as const;
export const FOLLOWUP_CLOSED = ['DONE', 'CANCELLED'] as const;

export type AckStatus = 'OK' | 'DUPLICATE' | 'CONFLICT' | 'REJECTED';

export interface Ack {
  id: string;
  status: AckStatus;
  serverId?: string | null;
  message?: string;
}

/**
 * Label alasan tindak lanjut tanpa nilai pemeriksaan (untuk dashboard ADMIN).
 * Kode berasal dari FollowUpRules aplikasi.
 */
export const FOLLOWUP_REASON_LABELS: Record<string, string> = {
  bp_urgent: 'Tekanan darah sangat tinggi',
  bp_repeated: 'Tekanan darah tinggi berulang',
  bp_elevated: 'Tekanan darah perlu diukur ulang',
  bp_low: 'Tekanan darah rendah, ukur ulang',
  glucose_urgent: 'Gula darah di luar rentang aman',
  glucose_high: 'Gula darah tinggi, perlu ditinjau tenaga kesehatan',
  glucose_watch: 'Gula darah perlu diukur ulang',
  chol_high: 'Kolesterol total tinggi',
};

/** Alasan tanpa angka hasil pemeriksaan: pakai label kode bila dikenal, selain itu hapus angka & satuan. */
export function sanitizeReason(reasonCode: unknown, reason: unknown): string {
  if (typeof reasonCode === 'string' && FOLLOWUP_REASON_LABELS[reasonCode]) return FOLLOWUP_REASON_LABELS[reasonCode];
  const text = typeof reason === 'string' ? reason : '';
  return text
    .replace(/\d+([.,]\d+)?(\s*\/\s*\d+([.,]\d+)?)?\s*(mg\/dl|mmhg|bpm|kg|cm|%)?/gi, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
}
