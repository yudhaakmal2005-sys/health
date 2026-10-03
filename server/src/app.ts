import Fastify, { type FastifyError, type FastifyInstance } from 'fastify';
import { serveWeb } from './core/static.js';
import helmet from '@fastify/helmet';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import type { Deps } from './core/types.js';
import { ApiError, errorBody } from './core/errors.js';
import { publicRoutes } from './core/public-routes.js';
import { authRoutes } from './auth/routes.js';
import { syncRoutes } from './sync/routes.js';
import { adminRoutes } from './admin/routes.js';
import { aiRoutes } from './ai/routes.js';

export const API_PREFIX = '/api/v1';
const BODY_LIMIT = 2 * 1024 * 1024;

/**
 * Logger: hanya metode, URL tanpa query, status, durasi. Header Authorization, body, kata sandi,
 * token dan isi pesan AI tidak pernah dicatat.
 */
function loggerOptions(deps: Deps) {
  if (deps.config.env === 'test') return false;
  return {
    level: deps.config.logLevel,
    redact: {
      paths: ['req.headers.authorization', 'req.headers.cookie', 'req.body', 'body', '*.password', '*.token', '*.payload', '*.messages'],
      censor: '[disembunyikan]',
    },
    serializers: {
      req: (r: { method: string; url: string; id: string }) => ({ id: r.id, method: r.method, url: r.url.split('?')[0] }),
      res: (r: { statusCode: number }) => ({ statusCode: r.statusCode }),
    },
  };
}

export async function buildApp(deps: Deps): Promise<FastifyInstance> {
  const app = Fastify({
    logger: loggerOptions(deps),
    bodyLimit: BODY_LIMIT,
    trustProxy: deps.config.trustProxy,
  });

  await app.register(helmet, { contentSecurityPolicy: { directives: { defaultSrc: ["'none'"], frameAncestors: ["'none'"] } } });
  await app.register(cors, {
    origin: deps.config.corsOrigins.length > 0 ? deps.config.corsOrigins : false,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Authorization', 'Content-Type'],
    maxAge: 600,
  });
  await app.register(rateLimit, {
    global: true,
    max: deps.config.rateLimits.globalPerMinute,
    timeWindow: 60_000,
    errorResponseBuilder: () => new ApiError(429, 'RATE_LIMITED', 'Terlalu banyak permintaan. Coba lagi beberapa saat lagi.'),
  });

  app.setErrorHandler((err: FastifyError | ApiError, req, reply) => {
    if (err instanceof ApiError) return reply.code(err.statusCode).send(errorBody(err.code, err.message));
    const status = err.statusCode ?? 500;
    if (status === 413) return reply.code(413).send(errorBody('PAYLOAD_TOO_LARGE', 'Ukuran data melebihi batas 2 MB.'));
    if (status === 429) return reply.code(429).send(errorBody('RATE_LIMITED', 'Terlalu banyak permintaan. Coba lagi beberapa saat lagi.'));
    if (status >= 400 && status < 500) {
      const code = err.code === 'FST_ERR_CTP_INVALID_MEDIA_TYPE' ? 'UNSUPPORTED_MEDIA_TYPE' : err.code?.startsWith('FST_ERR_CTP') ? 'INVALID_JSON' : 'BAD_REQUEST';
      return reply.code(status).send(errorBody(code, 'Permintaan tidak valid.'));
    }
    req.log.error({ err }, 'unhandled error');
    return reply.code(500).send(errorBody('INTERNAL', 'Terjadi kesalahan pada server. Coba lagi nanti.'));
  });

  app.setNotFoundHandler((req, reply) => {
    if (deps.config.webDir && serveWeb(deps.config.webDir, req, reply)) return reply;
    return reply.code(404).send(errorBody('NOT_FOUND', 'Alamat API tidak ditemukan.'));
  });

  await app.register(async (api) => {
    await api.register(async (s) => publicRoutes(s, deps));
    await api.register(async (s) => authRoutes(s, deps));
    await api.register(async (s) => syncRoutes(s, deps));
    await api.register(async (s) => aiRoutes(s, deps));
    await api.register(async (s) => adminRoutes(s, deps));
  }, { prefix: API_PREFIX });

  return app;
}
