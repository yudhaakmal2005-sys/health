import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { AnthropicAiClient, type ChatEvent } from '../src/ai/client.js';

/** Server tiruan lokal yang meniru SSE Messages API — tidak ada panggilan ke API nyata. */
let server: http.Server;
let baseURL = '';
let lastBody: Record<string, unknown> = {};
let lastHeaders: http.IncomingHttpHeaders = {};

function sse(res: http.ServerResponse, events: [string, unknown][]) {
  res.writeHead(200, { 'content-type': 'text/event-stream' });
  for (const [e, d] of events) res.write(`event: ${e}\ndata: ${JSON.stringify(d)}\n\n`);
  res.end();
}

before(async () => {
  server = http.createServer((req, res) => {
    let raw = '';
    req.on('data', (c) => (raw += c));
    req.on('end', () => {
      lastBody = JSON.parse(raw) as Record<string, unknown>;
      lastHeaders = req.headers;
      const msg = { id: 'msg_1', type: 'message', role: 'assistant', model: 'claude-opus-5-5', content: [], stop_reason: null, stop_sequence: null,
        usage: { input_tokens: 10, output_tokens: 0 } };
      sse(res, [
        ['message_start', { type: 'message_start', message: msg }],
        ['content_block_start', { type: 'content_block_start', index: 0, content_block: { type: 'thinking', thinking: '', signature: '' } }],
        ['content_block_stop', { type: 'content_block_stop', index: 0 }],
        ['content_block_start', { type: 'content_block_start', index: 1, content_block: { type: 'text', text: '' } }],
        ['content_block_delta', { type: 'content_block_delta', index: 1, delta: { type: 'text_delta', text: 'Halo, ' } }],
        ['content_block_delta', { type: 'content_block_delta', index: 1, delta: { type: 'text_delta', text: 'jaga tensi.' } }],
        ['content_block_stop', { type: 'content_block_stop', index: 1 }],
        ['message_delta', { type: 'message_delta', delta: { stop_reason: 'end_turn', stop_sequence: null }, usage: { output_tokens: 5 } }],
        ['message_stop', { type: 'message_stop' }],
      ]);
    });
  });
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
  baseURL = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});
after(() => new Promise<void>((r) => server.close(() => r())));

describe('AnthropicAiClient (server tiruan)', () => {
  test('mengalirkan text delta + stop reason; bentuk permintaan sesuai konfigurasi', async () => {
    const client = new AnthropicAiClient({ apiKey: 'sk-test', model: 'claude-opus-5-5', effort: 'low', maxTokens: 1234, baseURL });
    const events: ChatEvent[] = [];
    for await (const ev of client.streamChat({
      system: 'SISTEM', context: 'KONTEKS', messages: [{ role: 'user', content: 'Halo' }], signal: new AbortController().signal,
    })) events.push(ev);
    assert.deepEqual(events, [{ type: 'text', text: 'Halo, ' }, { type: 'text', text: 'jaga tensi.' }, { type: 'done', stopReason: 'end_turn' }]);
    assert.equal(lastBody.model, 'claude-opus-5-5');
    assert.equal(lastBody.max_tokens, 1234);
    assert.equal(lastBody.stream, true);
    assert.deepEqual(lastBody.thinking, { type: 'adaptive' });
    assert.deepEqual(lastBody.output_config, { effort: 'low' });
    assert.equal(lastBody.fallbacks, 'default');
    assert.match(String(lastHeaders['anthropic-beta']), /server-side-fallback-2026-07-01/);
    assert.deepEqual(lastBody.system, [
      { type: 'text', text: 'SISTEM', cache_control: { type: 'ephemeral' } },
      { type: 'text', text: 'KONTEKS' },
    ]);
    assert.deepEqual(lastBody.messages, [{ role: 'user', content: 'Halo' }]);
    assert.equal(lastHeaders['x-api-key'], 'sk-test');
  });
});

describe('modelCapabilities', () => {
  test('parameter disesuaikan dengan model', async () => {
    const { modelCapabilities } = await import('../src/ai/client.js');
    assert.deepEqual(modelCapabilities('claude-opus-5-5'), { adaptive: true, defaultFallbacks: true });
    assert.deepEqual(modelCapabilities('claude-sonnet-5-5'), { adaptive: true, defaultFallbacks: true });
    assert.deepEqual(modelCapabilities('claude-opus-4-8'), { adaptive: true, defaultFallbacks: false });
    assert.deepEqual(modelCapabilities('claude-haiku-4-5'), { adaptive: false, defaultFallbacks: false });
    assert.deepEqual(modelCapabilities('claude-sonnet-4-5'), { adaptive: false, defaultFallbacks: false });
  });
});
