import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { OpenAiCompatClient } from '../src/ai/openai-compat.js';
import type { ChatEvent } from '../src/ai/client.js';
import { loadConfig } from '../src/config.js';

/** Server tiruan lokal berformat OpenAI chat/completions (SSE) — tidak ada panggilan ke API nyata. */
let server: http.Server;
let baseURL = '';
let lastBody: Record<string, unknown> = {};
let lastAuth = '';

before(async () => {
  server = http.createServer((req, res) => {
    let raw = '';
    req.on('data', (c) => (raw += c));
    req.on('end', () => {
      lastBody = JSON.parse(raw) as Record<string, unknown>;
      lastAuth = String(req.headers.authorization ?? '');
      if (req.url !== '/v1/chat/completions') { res.writeHead(404).end(); return; }
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      for (const t of ['Halo, ', 'jaga ', 'tensi.']) res.write(`data: ${JSON.stringify({ choices: [{ delta: { content: t }, finish_reason: null }] })}\n\n`);
      res.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }] })}\n\n`);
      res.end('data: [DONE]\n\n');
    });
  });
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
  baseURL = `http://127.0.0.1:${(server.address() as AddressInfo).port}/v1/`;
});
after(() => new Promise<void>((r) => server.close(() => r())));

describe('OpenAiCompatClient (server tiruan)', () => {
  test('stream teks, prompt sistem + konteks, dan header otorisasi', async () => {
    const client = new OpenAiCompatClient({ apiKey: 'k-test', baseURL, model: 'model-x', maxTokens: 321 });
    const out: ChatEvent[] = [];
    for await (const e of client.streamChat({ system: 'SYS', context: 'CTX', messages: [{ role: 'user', content: 'hai' }], signal: new AbortController().signal })) out.push(e);
    assert.equal(out.filter((e) => e.type === 'text').map((e) => (e as { text: string }).text).join(''), 'Halo, jaga tensi.');
    assert.deepEqual(out.at(-1), { type: 'done', stopReason: 'end_turn' });
    assert.equal(lastAuth, 'Bearer k-test');
    assert.equal(lastBody.model, 'model-x');
    assert.equal(lastBody.max_tokens, 321);
    assert.equal(lastBody.stream, true);
    const msgs = lastBody.messages as { role: string; content: string }[];
    assert.deepEqual(msgs[0], { role: 'system', content: 'SYS\n\nCTX' });
    assert.deepEqual(msgs[1], { role: 'user', content: 'hai' });
  });

  test('galat HTTP dari penyedia dilempar', async () => {
    const client = new OpenAiCompatClient({ apiKey: 'k', baseURL: baseURL + 'salah', model: 'm', maxTokens: 10 });
    await assert.rejects(async () => { for await (const _ of client.streamChat({ system: 's', context: null, messages: [], signal: new AbortController().signal })) { /* */ } }, /HTTP 404/);
  });

  test('konfigurasi AI_PROVIDER=openai', () => {
    const c = loadConfig({ DATABASE_URL: 'postgres://x', AI_PROVIDER: 'openai', AI_API_KEY: 'abc', AI_BASE_URL: 'https://api.deepseek.com/v1', AI_MODEL: 'deepseek-chat' });
    assert.equal(c.aiProvider, 'openai');
    assert.equal(c.aiApiKey, 'abc');
    assert.equal(c.aiBaseUrl, 'https://api.deepseek.com/v1');
    assert.equal(c.aiModel, 'deepseek-chat');
    assert.throws(() => loadConfig({ DATABASE_URL: 'postgres://x', AI_PROVIDER: 'lain' }), /AI_PROVIDER/);
  });
});
