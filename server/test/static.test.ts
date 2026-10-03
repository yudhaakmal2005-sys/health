import { after, afterEach, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { closePool, setup, type TestCtx } from './helpers.js';

let ctx: TestCtx;
let dir: string;
beforeEach(async () => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sehati-web-'));
  fs.mkdirSync(path.join(dir, 'assets'));
  fs.mkdirSync(path.join(dir, 'download'));
  fs.writeFileSync(path.join(dir, 'index.html'), '<!doctype html><title>SEHATI</title>');
  fs.writeFileSync(path.join(dir, 'assets', 'app.js'), 'console.log(1)');
  fs.writeFileSync(path.join(dir, 'download', 'sehati.apk'), 'APK');
  ctx = await setup();
  ctx.deps.config.webDir = dir;
});
afterEach(async () => { await ctx.close(); fs.rmSync(dir, { recursive: true, force: true }); });
after(closePool);

describe('WEB_DIR: dashboard disajikan server', () => {
  test('index, aset, SPA fallback, unduhan APK', async () => {
    const home = await ctx.app.inject({ method: 'GET', url: '/' });
    assert.equal(home.statusCode, 200);
    assert.match(home.body, /SEHATI/);
    assert.match(String(home.headers['content-security-policy']), /default-src 'self'/);
    const js = await ctx.app.inject({ method: 'GET', url: '/assets/app.js' });
    assert.equal(js.statusCode, 200);
    assert.match(String(js.headers['content-type']), /javascript/);
    assert.match(String(js.headers['cache-control']), /immutable/);
    const spa = await ctx.app.inject({ method: 'GET', url: '/admin/kader' });
    assert.equal(spa.statusCode, 200);
    assert.match(spa.body, /SEHATI/);
    const apk = await ctx.app.inject({ method: 'GET', url: '/download/sehati.apk' });
    assert.equal(apk.statusCode, 200);
    assert.match(String(apk.headers['content-disposition']), /attachment/);
  });

  test('API tetap 404 JSON, aset hilang 404, path traversal ditolak', async () => {
    const api = await ctx.app.inject({ method: 'GET', url: '/api/v1/tidak-ada' });
    assert.equal(api.statusCode, 404);
    assert.equal(api.json().error.code, 'NOT_FOUND');
    assert.equal((await ctx.app.inject({ method: 'GET', url: '/assets/hilang.js' })).statusCode, 404);
    const trav = await ctx.app.inject({ method: 'GET', url: '/..%2f..%2fetc%2fpasswd' });
    assert.notEqual(trav.body, fs.existsSync('/etc/passwd') ? fs.readFileSync('/etc/passwd', 'utf8') : '__');
    const health = await ctx.app.inject({ method: 'GET', url: '/api/v1/health' });
    assert.equal(health.statusCode, 200);
  });
});
