import { randomUUID } from 'node:crypto';
import type { FastifyInstance, LightMyRequestResponse } from 'fastify';
import { buildApp } from '../src/app.js';
import { defaultRateLimits, type AppConfig, type RateLimits } from '../src/config.js';
import { createPool, type Pool } from '../src/db/pool.js';
import { runMigrations } from '../src/db/migrate.js';
import { ensureAdmin } from '../src/core/bootstrap.js';
import type { AiClient, ChatEvent, ChatRequest } from '../src/ai/client.js';
import type { Deps } from '../src/core/types.js';

export const ADMIN_ID = 'AD-000001';
export const ADMIN_PW = 'admin-test-password';
export const PW = 'rahasia123';

let pool: Pool | null = null;

export async function getPool(): Promise<Pool> {
  if (pool) return pool;
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error('DATABASE_URL tidak diatur — jalankan lewat `npm test` (scripts/test-db.sh).');
  pool = createPool(url);
  await runMigrations(pool);
  return pool;
}

export async function resetDb(p: Pool): Promise<void> {
  await p.query(`TRUNCATE accounts, sessions, id_counters, id_reservations, entities, sync_receipts, audit_log, ai_usage,
                 settings, posyandu_schedule RESTART IDENTITY CASCADE`);
  await p.query('ALTER SEQUENCE entity_seq RESTART WITH 1');
}

export class Clock {
  constructor(public t = Date.UTC(2026, 9, 3, 3, 0, 0)) {}
  now = () => this.t;
  advance(ms: number) {
    this.t += ms;
  }
}

/** AI palsu: mencatat panggilan dan mengalirkan potongan teks yang ditentukan tes. */
export class FakeAi implements AiClient {
  calls: ChatRequest[] = [];
  chunks = ['Tanda yang perlu ', 'diwaspadai: nyeri dada.'];
  stopReason = 'end_turn';
  failAfter: number | null = null;

  async *streamChat(req: ChatRequest): AsyncIterable<ChatEvent> {
    this.calls.push(req);
    let i = 0;
    for (const c of this.chunks) {
      if (this.failAfter !== null && i >= this.failAfter) throw new Error('upstream failed');
      i++;
      yield { type: 'text', text: c };
    }
    yield { type: 'done', stopReason: this.stopReason };
  }
}

export interface TestCtx {
  app: FastifyInstance;
  pool: Pool;
  clock: Clock;
  deps: Deps;
  close: () => Promise<void>;
}

export async function setup(opts: { ai?: AiClient | null; rateLimits?: Partial<RateLimits> } = {}): Promise<TestCtx> {
  const p = await getPool();
  await resetDb(p);
  const clock = new Clock();
  const config: AppConfig = {
    env: 'test', port: 0, host: '127.0.0.1', databaseUrl: process.env.DATABASE_URL!, adminId: ADMIN_ID, adminPassword: ADMIN_PW,
    corsOrigins: ['https://dashboard.example'], trustProxy: false, timeZone: 'Asia/Jakarta', logLevel: 'silent',
    anthropicApiKey: null, aiModel: 'claude-opus-5-5', aiEffort: 'low', aiMaxTokens: 1000,
    rateLimits: {
      ...defaultRateLimits(), globalPerMinute: 100_000, loginPerMinute: 100_000, registerPerHour: 100_000,
      activatePerHour: 100_000, activatePerIdPerHour: 100_000, anonReservePerHour: 100_000, ...opts.rateLimits,
    },
  };
  const deps: Deps = { pool: p, config, ai: opts.ai === undefined ? null : opts.ai, now: clock.now };
  await ensureAdmin(p, config, { info: () => undefined, warn: () => undefined }, clock.t);
  const app = await buildApp(deps);
  await app.ready();
  return { app, pool: p, clock, deps, close: () => app.close() };
}

export async function closePool(): Promise<void> {
  if (pool) await pool.end();
  pool = null;
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export async function call(app: FastifyInstance, method: Method, url: string, token?: string | null, body?: unknown): Promise<LightMyRequestResponse> {
  return app.inject({
    method,
    url: `/api/v1${url}`,
    headers: token ? { authorization: `Bearer ${token}` } : {},
    ...(body !== undefined ? { payload: body as object } : {}),
  });
}

export async function login(app: FastifyInstance, sehatiId: string, password = PW): Promise<string> {
  const r = await call(app, 'POST', '/auth/login', null, { sehatiId, password, deviceId: 'dev-test' });
  if (r.statusCode !== 200) throw new Error(`login ${sehatiId} gagal: ${r.statusCode} ${r.body}`);
  return r.json().token as string;
}

export async function adminToken(app: FastifyInstance): Promise<string> {
  return login(app, ADMIN_ID, ADMIN_PW);
}

/** Buat kader lewat API admin, kembalikan { id, token }. */
export async function createKader(app: FastifyInstance, admin: string, rw: string, fullName = 'Kader Uji'): Promise<{ id: string; token: string }> {
  const r = await call(app, 'POST', '/admin/cadres', admin, { fullName, rw, password: PW });
  if (r.statusCode !== 201) throw new Error(`buat kader gagal: ${r.body}`);
  const id = r.json().sehatiId as string;
  return { id, token: await login(app, id) };
}

let nextWarga = 500;
/** Daftarkan warga mandiri (consent=true) lewat /auth/register. */
export async function registerWarga(app: FastifyInstance, rw: string, id?: string): Promise<{ id: string; token: string }> {
  const sehatiId = id ?? `HM-${String(nextWarga++).padStart(6, '0')}`;
  const r = await call(app, 'POST', '/auth/register', null, {
    sehatiId, fullName: 'Warga Uji', birthDate: '1980-05-01', sex: 'FEMALE', village: 'Desa Uji', rw, rt: '01', phone: null,
    password: PW, consentServerSync: true, deviceId: `dev-${sehatiId}`,
  });
  if (r.statusCode !== 201) throw new Error(`register gagal: ${r.statusCode} ${r.body}`);
  return { id: sehatiId, token: r.json().token as string };
}

export function userPayload(id: string, rw: string, extra: Record<string, unknown> = {}) {
  return {
    sehatiId: id, fullName: 'Warga Uji', birthDate: '1980-05-01', sex: 'FEMALE', village: 'Desa Uji', rw, rt: '01', phone: null,
    role: 'WARGA', qrToken: 'qr-token-abcdefghijkl', goals: '', consentLocal: true, consentServerSync: true, consentHealthConnect: false,
    consentAt: 1, onboardingDone: true, assessmentDone: false, hasAccount: true, registeredBy: null, householdId: null, isDemo: true,
    createdAt: 1, updatedAt: 1, syncStatus: 'LOCAL_ONLY', serverId: null, version: 1, ...extra,
  };
}

export interface ItemSpec {
  type: string;
  entityId: string;
  subjectId?: string | null;
  version?: number;
  operation?: 'UPSERT' | 'DELETE';
  payload: unknown;
  id?: string;
}

export function item(s: ItemSpec) {
  return {
    id: s.id ?? randomUUID(), type: s.type, entityId: s.entityId, subjectId: s.subjectId ?? null,
    operation: s.operation ?? 'UPSERT', version: s.version ?? 1, payload: JSON.stringify(s.payload),
  };
}

export async function push(app: FastifyInstance, token: string, items: (Omit<ReturnType<typeof item>, "subjectId"> & { subjectId?: string | null })[], deviceId = 'dev-test') {
  const r = await call(app, 'POST', '/sync/push', token, { deviceId, items });
  if (r.statusCode !== 200) throw new Error(`push gagal: ${r.statusCode} ${r.body}`);
  return r.json().results as { id: string; status: string; serverId?: string; message?: string }[];
}

export interface PulledItem {
  seq: number; type: string; entityId: string; subjectId: string | null; version: number; deleted: boolean; payload: string | null;
}

/** Tarik semua halaman. */
export async function pullAll(app: FastifyInstance, token: string, limit = 500): Promise<PulledItem[]> {
  const out: PulledItem[] = [];
  let cursor = 0;
  for (let i = 0; i < 100; i++) {
    const r = await call(app, 'GET', `/sync/pull?cursor=${cursor}&limit=${limit}`, token);
    if (r.statusCode !== 200) throw new Error(`pull gagal: ${r.body}`);
    const body = r.json() as { items: PulledItem[]; nextCursor: number; hasMore: boolean };
    out.push(...body.items);
    cursor = body.nextCursor;
    if (!body.hasMore) break;
  }
  return out;
}

export function measurement(id: string, userId: string, measuredAt: number, extra: Record<string, unknown> = {}) {
  return {
    id, userId, measuredAt, source: 'POSYANDU', operatorId: null, facilityId: null, visitId: null, notes: '', verification: 'UNVERIFIED',
    createdAt: measuredAt, updatedAt: measuredAt, syncStatus: 'LOCAL_ONLY', serverId: null, version: 1, ...extra,
  };
}

export function detail(measurementId: string, userId: string, sys: number, dia: number) {
  return {
    measurementId,
    anthropometry: { id: `a-${measurementId}`, measurementId, userId, weightKg: 60, heightCm: 160, waistCm: 80, bmi: 23.4 },
    bloodPressure: { id: `b-${measurementId}`, measurementId, userId, systolic: sys, diastolic: dia, heartRate: 80 },
    glucose: null,
    lipid: null,
  };
}

export function followup(id: string, userId: string, extra: Record<string, unknown> = {}) {
  return {
    id, userId, visitId: null, type: 'REPEAT_MEASUREMENT', reasonCode: 'bp_elevated', reason: 'Tekanan darah 150/95 perlu diukur ulang',
    priority: 2, status: 'OPEN', assignedCadreId: null, dueAt: 1, createdBy: 'KD-000001', closedAt: null, notes: 'catatan kader',
    createdAt: 1, updatedAt: 1, syncStatus: 'LOCAL_ONLY', serverId: null, version: 1, ...extra,
  };
}

/** Uraikan respons SSE menjadi daftar { event, data }. */
export function parseSse(body: string): { event: string; data: Record<string, unknown> }[] {
  return body.split('\n\n').filter((b) => b.trim()).map((block) => {
    const lines = block.split('\n');
    const event = lines.find((l) => l.startsWith('event: '))?.slice(7) ?? '';
    const data = lines.find((l) => l.startsWith('data: '))?.slice(6) ?? '{}';
    return { event, data: JSON.parse(data) as Record<string, unknown> };
  });
}
