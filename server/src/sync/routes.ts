import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { Deps } from '../core/types.js';
import { Errors } from '../core/errors.js';
import { parse, zDeviceId } from '../core/validate.js';
import { withTx } from '../db/pool.js';
import { actorOf, optionalAuth, requireAuth } from '../auth/guard.js';
import { pull, pushBatch } from './service.js';
import { reserveIds } from './ids.js';

const MAX_PAYLOAD_CHARS = 256 * 1024;

const pushSchema = z.object({
  deviceId: zDeviceId,
  items: z.array(z.object({
    id: z.string().min(1).max(200),
    type: z.string().min(1).max(40),
    entityId: z.string().min(1).max(200),
    subjectId: z.string().max(40).nullable().optional(),
    operation: z.enum(['UPSERT', 'DELETE']),
    version: z.number().int().min(0).max(2_147_483_647),
    payload: z.string().max(MAX_PAYLOAD_CHARS),
  })).max(500),
});

const pullSchema = z.object({
  cursor: z.coerce.number().int().min(0).default(0),
  limit: z.coerce.number().int().min(1).max(500).default(500),
});

const reserveSchema = z.object({
  deviceId: zDeviceId,
  count: z.number().int().min(1).max(1000).default(1),
  prefix: z.enum(['HM', 'KD']).default('HM'),
});

export async function syncRoutes(app: FastifyInstance, deps: Deps): Promise<void> {
  app.post('/sync/push', { preHandler: requireAuth(deps) }, async (req) => {
    const body = parse(pushSchema, req.body);
    const ids = new Set<string>();
    for (const it of body.items) {
      if (ids.has(it.id)) throw Errors.validation('Kunci idempoten (id) ganda dalam satu permintaan.');
      ids.add(it.id);
    }
    const results = await pushBatch(deps, actorOf(req), body.deviceId, body.items, req.log);
    return { results };
  });

  app.get('/sync/pull', { preHandler: requireAuth(deps) }, async (req) => {
    const q = parse(pullSchema, req.query);
    return pull(deps, actorOf(req), q.cursor, q.limit);
  });

  // Tanpa auth: maks 1 ID (pendaftaran mandiri) dan batas per IP yang ketat.
  app.post('/ids/reserve', {
    preHandler: optionalAuth(deps),
    config: {
      rateLimit: {
        max: (req) => (req.headers.authorization ? deps.config.rateLimits.globalPerMinute * 60 : deps.config.rateLimits.anonReservePerHour),
        timeWindow: 3_600_000,
      },
    },
  }, async (req) => {
    const body = parse(reserveSchema, req.body);
    const actor = req.actor;
    let max = 1;
    if (actor?.role === 'KADER') {
      if (body.prefix !== 'HM') throw Errors.forbidden('Kader hanya dapat mencadangkan ID warga (HM).');
      max = 50;
    } else if (actor?.role === 'ADMIN') {
      max = 50;
    } else if (body.prefix !== 'HM') {
      throw Errors.forbidden('Hanya ID warga (HM) yang dapat dicadangkan.');
    }
    const count = Math.min(body.count, max);
    const now = deps.now();
    return withTx(deps.pool, (c) =>
      reserveIds(c, { prefix: body.prefix, count, deviceId: body.deviceId, reservedBy: actor?.id ?? null, now }));
  });
}
