import type { FastifyInstance } from 'fastify';
import type { Deps } from './types.js';
import { APP_VERSION } from '../config.js';
import { ApiError } from './errors.js';
import { localDate } from './time.js';
import { actorOf, requireAuth } from '../auth/guard.js';
import { getThresholds } from '../admin/thresholds.js';
import { upcomingFor } from '../admin/posyandu.js';

export const EMERGENCY_NUMBERS = '119 atau 112';

export async function publicRoutes(app: FastifyInstance, deps: Deps): Promise<void> {
  app.get('/health', { config: { rateLimit: false } }, async () => {
    try {
      await deps.pool.query('SELECT 1');
    } catch {
      throw new ApiError(503, 'DB_UNAVAILABLE', 'Basis data tidak dapat dihubungi.');
    }
    return { status: 'ok', version: APP_VERSION, time: deps.now(), ai: deps.ai !== null };
  });

  app.get('/config', { preHandler: requireAuth(deps) }, async (req) => {
    const actor = actorOf(req);
    const t = await getThresholds(deps.pool);
    return {
      thresholds: t.thresholds,
      thresholdsVersion: t.version,
      emergencyNumbers: EMERGENCY_NUMBERS,
      posyandu: await upcomingFor(deps.pool, actor, localDate(deps.now(), deps.config.timeZone)),
    };
  });
}
