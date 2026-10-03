import { pbkdf2, randomBytes, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';

const pbkdf2Async = promisify(pbkdf2);

/** Sama dengan PasswordHasher aplikasi: PBKDF2-HMAC-SHA256, kunci 256 bit, salt 16 byte, base64 standar. */
export const PASSWORD_ITERATIONS = 120_000;
const KEY_BYTES = 32;

export interface PasswordHash {
  salt: string;
  hash: string;
  iterations: number;
}

async function derive(password: string, salt: Buffer, iterations: number): Promise<string> {
  // Java PBEKeySpec memakai char[] → PBKDF2WithHmacSHA256 mengodekan kata sandi sebagai UTF-8.
  const key = await pbkdf2Async(Buffer.from(password, 'utf8'), salt, iterations, KEY_BYTES, 'sha256');
  return key.toString('base64');
}

export async function hashPassword(password: string, iterations = PASSWORD_ITERATIONS): Promise<PasswordHash> {
  const salt = randomBytes(16);
  return { salt: salt.toString('base64'), hash: await derive(password, salt, iterations), iterations };
}

/** Bandingkan string base64 secara waktu-konstan, seperti MessageDigest.isEqual di aplikasi. */
export async function verifyPassword(password: string, stored: PasswordHash): Promise<boolean> {
  if (!Number.isInteger(stored.iterations) || stored.iterations < 1 || stored.iterations > 10_000_000) return false;
  const candidate = Buffer.from(await derive(password, Buffer.from(stored.salt, 'base64'), stored.iterations));
  const expected = Buffer.from(stored.hash);
  return candidate.length === expected.length && timingSafeEqual(candidate, expected);
}

/** Aturan aplikasi (PasswordHasher.passwordIssue). */
export function passwordIssue(p: string): string | null {
  return p.length < 6 ? 'Kata sandi minimal 6 karakter.' : null;
}

/** Hash tiruan untuk menyamakan waktu respons saat akun tidak ada (mencegah enumerasi lewat timing). */
let dummy: PasswordHash | null = null;
export async function burnPasswordCheck(password: string): Promise<void> {
  dummy ??= await hashPassword('sehati-dummy-password');
  await verifyPassword(password, dummy);
}
