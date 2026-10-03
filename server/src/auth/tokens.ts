import { createHash, randomBytes } from 'node:crypto';

/** Token opaque: 32 byte acak, base64url. Server hanya menyimpan SHA-256 (hex). */
export function newToken(): string {
  return randomBytes(32).toString('base64url');
}

export function hashToken(token: string): string {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}
