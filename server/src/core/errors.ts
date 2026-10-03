import { ZodError } from 'zod';

/** Galat API dengan format kontrak: { error: { code, message } }. Pesan untuk pengguna, Bahasa Indonesia. */
export class ApiError extends Error {
  constructor(
    readonly statusCode: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export const Errors = {
  validation: (message = 'Data yang dikirim tidak valid.') => new ApiError(400, 'VALIDATION_ERROR', message),
  unauthorized: () => new ApiError(401, 'UNAUTHORIZED', 'Sesi tidak valid atau sudah berakhir. Silakan masuk kembali.'),
  forbidden: (message = 'Anda tidak memiliki akses untuk tindakan ini.') => new ApiError(403, 'FORBIDDEN', message),
  notFound: (message = 'Data tidak ditemukan.') => new ApiError(404, 'NOT_FOUND', message),
  conflict: (code: string, message: string) => new ApiError(409, code, message),
  rateLimited: (message = 'Terlalu banyak permintaan. Coba lagi beberapa saat lagi.') =>
    new ApiError(429, 'RATE_LIMITED', message),
};

export function errorBody(code: string, message: string) {
  return { error: { code, message } };
}

/** Pesan validasi singkat dari ZodError tanpa mengutip nilai masukan (bisa berisi data pribadi). */
export function zodMessage(err: ZodError): string {
  const issue = err.issues[0];
  if (!issue) return 'Data yang dikirim tidak valid.';
  const path = issue.path.length > 0 ? issue.path.join('.') : 'body';
  return `Data tidak valid pada "${path}".`;
}
