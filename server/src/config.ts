/** Konfigurasi dari environment. Semua nilai divalidasi saat start; tidak ada rahasia di kode. */

export type Effort = 'low' | 'medium' | 'high' | 'xhigh' | 'max';

export interface RateLimits {
  /** Batas global per IP per menit untuk semua rute. */
  globalPerMinute: number;
  loginPerMinute: number;
  registerPerHour: number;
  activatePerHour: number;
  /** Aktivasi per sehatiId (di memori). */
  activatePerIdPerHour: number;
  anonReservePerHour: number;
  aiPerMinute: number;
  aiPerDay: number;
}

export interface AppConfig {
  env: 'development' | 'production' | 'test';
  port: number;
  host: string;
  databaseUrl: string;
  adminId: string;
  adminPassword: string | null;
  corsOrigins: string[];
  trustProxy: string | boolean;
  timeZone: string;
  logLevel: string;
  anthropicApiKey: string | null;
  aiModel: string;
  aiEffort: Effort;
  aiMaxTokens: number;
  rateLimits: RateLimits;
}

export const APP_VERSION = '2.0.0';

const EFFORTS: readonly Effort[] = ['low', 'medium', 'high', 'xhigh', 'max'];

function int(v: string | undefined, def: number, name: string): number {
  if (v === undefined || v.trim() === '') return def;
  const n = Number(v);
  if (!Number.isInteger(n) || n < 0) throw new Error(`${name} harus bilangan bulat >= 0`);
  return n;
}

export function defaultRateLimits(): RateLimits {
  return {
    globalPerMinute: 300,
    loginPerMinute: 20,
    registerPerHour: 10,
    activatePerHour: 5,
    activatePerIdPerHour: 5,
    anonReservePerHour: 10,
    aiPerMinute: 10,
    aiPerDay: 40,
  };
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const nodeEnv = env.NODE_ENV === 'production' ? 'production' : env.NODE_ENV === 'test' ? 'test' : 'development';
  const databaseUrl = env.DATABASE_URL;
  if (!databaseUrl) throw new Error('DATABASE_URL wajib diisi');

  const adminPassword = env.ADMIN_PASSWORD && env.ADMIN_PASSWORD.length > 0 ? env.ADMIN_PASSWORD : null;
  if (nodeEnv === 'production' && adminPassword === null) {
    throw new Error('ADMIN_PASSWORD wajib diisi saat NODE_ENV=production');
  }
  if (nodeEnv === 'production' && adminPassword !== null && adminPassword.length < 10) {
    throw new Error('ADMIN_PASSWORD minimal 10 karakter di production');
  }

  const effort = (env.AI_EFFORT ?? 'low').trim() as Effort;
  if (!EFFORTS.includes(effort)) throw new Error(`AI_EFFORT harus salah satu dari ${EFFORTS.join(', ')}`);

  const adminId = (env.ADMIN_ID ?? 'AD-000001').trim().toUpperCase();
  if (!/^AD-\d{6}$/.test(adminId)) throw new Error('ADMIN_ID harus berformat AD-000001');

  const tp = env.TRUST_PROXY ?? 'loopback,linklocal,uniquelocal';
  const trustProxy = tp === 'true' ? true : tp === 'false' ? false : tp;

  const rl = defaultRateLimits();
  return {
    env: nodeEnv,
    port: int(env.PORT, 8080, 'PORT'),
    host: env.HOST ?? '0.0.0.0',
    databaseUrl,
    adminId,
    adminPassword,
    corsOrigins: (env.CORS_ORIGIN ?? '').split(',').map((s) => s.trim()).filter(Boolean),
    trustProxy,
    timeZone: env.TZ && env.TZ.trim() !== '' ? env.TZ : 'Asia/Jakarta',
    logLevel: env.LOG_LEVEL ?? (nodeEnv === 'production' ? 'info' : 'debug'),
    anthropicApiKey: env.ANTHROPIC_API_KEY && env.ANTHROPIC_API_KEY.trim() !== '' ? env.ANTHROPIC_API_KEY.trim() : null,
    aiModel: env.AI_MODEL && env.AI_MODEL.trim() !== '' ? env.AI_MODEL.trim() : 'claude-opus-5-5',
    aiEffort: effort,
    aiMaxTokens: int(env.AI_MAX_TOKENS, 8000, 'AI_MAX_TOKENS'),
    rateLimits: {
      ...rl,
      globalPerMinute: int(env.RATE_LIMIT_PER_MINUTE, rl.globalPerMinute, 'RATE_LIMIT_PER_MINUTE'),
      aiPerDay: int(env.AI_DAILY_LIMIT, rl.aiPerDay, 'AI_DAILY_LIMIT'),
      aiPerMinute: int(env.AI_MINUTE_LIMIT, rl.aiPerMinute, 'AI_MINUTE_LIMIT'),
    },
  };
}
