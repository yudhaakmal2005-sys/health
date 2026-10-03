import { after, afterEach, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { call, closePool, setup, type TestCtx } from './helpers.js';
import { loadConfig } from '../src/config.js';

let ctx: TestCtx;
beforeEach(async () => { ctx = await setup(); });
afterEach(async () => { await ctx.close(); });
after(closePool);

describe('platform', () => {
  test('GET /api/v1/health', async () => {
    const r = await call(ctx.app, 'GET', '/health');
    assert.equal(r.statusCode, 200);
    assert.deepEqual(r.json(), { status: 'ok', version: '2.0.0', time: ctx.clock.t, ai: false });
    assert.equal(r.headers['x-content-type-options'], 'nosniff');
    assert.ok(r.headers['strict-transport-security']);
  });

  test('rute di luar /api/v1 → 404 format galat kontrak', async () => {
    const r = await ctx.app.inject({ method: 'GET', url: '/health' });
    assert.equal(r.statusCode, 404);
    assert.equal(r.json().error.code, 'NOT_FOUND');
  });

  test('JSON rusak → 400; body > 2 MB → 413', async () => {
    const bad = await ctx.app.inject({ method: 'POST', url: '/api/v1/auth/login', headers: { 'content-type': 'application/json' }, payload: '{nope' });
    assert.equal(bad.statusCode, 400);
    assert.ok(bad.json().error.code);
    const big = await ctx.app.inject({
      method: 'POST', url: '/api/v1/auth/login', headers: { 'content-type': 'application/json' },
      payload: JSON.stringify({ x: 'a'.repeat(2 * 1024 * 1024 + 10) }),
    });
    assert.equal(big.statusCode, 413);
    assert.equal(big.json().error.code, 'PAYLOAD_TOO_LARGE');
  });

  test('CORS hanya untuk origin dashboard', async () => {
    const ok = await ctx.app.inject({ method: 'OPTIONS', url: '/api/v1/health', headers: { origin: 'https://dashboard.example', 'access-control-request-method': 'GET' } });
    assert.equal(ok.headers['access-control-allow-origin'], 'https://dashboard.example');
    const no = await ctx.app.inject({ method: 'GET', url: '/api/v1/health', headers: { origin: 'https://evil.example' } });
    assert.equal(no.headers['access-control-allow-origin'], undefined);
  });

  test('config: production wajib ADMIN_PASSWORD', () => {
    assert.throws(() => loadConfig({ NODE_ENV: 'production', DATABASE_URL: 'postgres://x' }), /ADMIN_PASSWORD/);
    const c = loadConfig({ NODE_ENV: 'production', DATABASE_URL: 'postgres://x', ADMIN_PASSWORD: 'cukup-panjang-123' });
    assert.equal(c.aiModel, 'claude-opus-5-5');
    assert.equal(c.aiEffort, 'low');
    assert.equal(c.adminId, 'AD-000001');
    assert.throws(() => loadConfig({ DATABASE_URL: 'postgres://x', AI_EFFORT: 'turbo' }), /AI_EFFORT/);
  });
});
