import type { AppConfig } from '../config.js';
import type { Pool } from '../db/pool.js';
import type { AiClient } from '../ai/client.js';

export type Role = 'WARGA' | 'KADER' | 'ADMIN';
export const ROLES: readonly Role[] = ['WARGA', 'KADER', 'ADMIN'];

/** Pengguna terautentikasi; role & RW selalu dari basis data, bukan dari klien. */
export interface Actor {
  id: string;
  role: Role;
  fullName: string;
  rw: string;
  village: string;
  sessionHash: string;
}

export interface Deps {
  pool: Pool;
  config: AppConfig;
  /** null bila ANTHROPIC_API_KEY tidak diatur (AI nonaktif). */
  ai: AiClient | null;
  now: () => number;
}

declare module 'fastify' {
  interface FastifyRequest {
    actor?: Actor;
  }
}
