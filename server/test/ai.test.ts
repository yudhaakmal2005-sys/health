import { after, afterEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { FakeAi, call, closePool, parseSse, registerWarga, setup, type TestCtx } from './helpers.js';
import { isEmergency } from '../src/ai/emergency.js';
import { EMERGENCY_MESSAGE, SYSTEM_PROMPT } from '../src/ai/prompt.js';

let ctx: TestCtx;
afterEach(async () => { await ctx.close(); });
after(closePool);

const ask = (content: string, extra: Record<string, unknown> = {}) => ({ messages: [{ role: 'user', content }], ...extra });

describe('Tanya SEHATI /ai/chat', () => {
  test('AI nonaktif → 503 AI_DISABLED', async () => {
    ctx = await setup({ ai: null });
    const w = await registerWarga(ctx.app, '02');
    const r = await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Apa tanda serangan jantung?'));
    assert.equal(r.statusCode, 503);
    assert.equal(r.json().error.code, 'AI_DISABLED');
    const h = (await call(ctx.app, 'GET', '/health')).json();
    assert.equal(h.ai, false);
  });

  test('normal: meta → delta… → done; konteks diteruskan setelah prompt sistem stabil', async () => {
    const fake = new FakeAi();
    ctx = await setup({ ai: fake });
    const w = await registerWarga(ctx.app, '02');
    const r = await call(ctx.app, 'POST', '/ai/chat', w.token, {
      messages: [
        { role: 'user', content: 'Apa tanda serangan jantung?' },
        { role: 'assistant', content: 'Nyeri dada, sesak.' },
        { role: 'user', content: 'Kalau pada perempuan?' },
      ],
      context: { ageBand: '40-49', sex: 'FEMALE', factors: ['bp', 'smoking'], steps: 4200 },
    });
    assert.equal(r.statusCode, 200);
    assert.match(r.headers['content-type'] as string, /^text\/event-stream/);
    assert.ok(r.headers['x-content-type-options']); // header keamanan tetap ada pada SSE
    const ev = parseSse(r.body);
    assert.deepEqual(ev.map((e) => e.event), ['meta', 'delta', 'delta', 'done']);
    assert.deepEqual(ev[0]!.data, { emergency: false });
    assert.equal(ev.filter((e) => e.event === 'delta').map((e) => e.data.text).join(''), fake.chunks.join(''));
    assert.deepEqual(ev[3]!.data, { stopReason: 'end_turn' });
    assert.equal(fake.calls.length, 1);
    const c = fake.calls[0]!;
    assert.equal(c.system, SYSTEM_PROMPT);
    assert.equal(c.messages.length, 3);
    assert.match(c.context!, /40-49/);
    assert.match(c.context!, /perempuan/);
    assert.match(c.context!, /tekanan darah, merokok/);
    assert.ok(!c.context!.includes(w.id));
    const u = await ctx.pool.query('SELECT messages, emergencies FROM ai_usage WHERE account_id = $1', [w.id]);
    assert.deepEqual(u.rows[0], { messages: 1, emergencies: 0 });
  });

  test('darurat: model tidak dipanggil, pesan baku 119/112, done emergency', async () => {
    const fake = new FakeAi();
    ctx = await setup({ ai: fake });
    const w = await registerWarga(ctx.app, '02');
    const r = await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Tolong, saya nyeri dada sekarang dan keringat dingin'));
    const ev = parseSse(r.body);
    assert.deepEqual(ev.map((e) => e.event), ['meta', 'delta', 'done']);
    assert.deepEqual(ev[0]!.data, { emergency: true });
    assert.equal(ev[1]!.data.text, EMERGENCY_MESSAGE);
    assert.match(EMERGENCY_MESSAGE, /119/);
    assert.match(EMERGENCY_MESSAGE, /112/);
    assert.deepEqual(ev[2]!.data, { stopReason: 'emergency' });
    assert.equal(fake.calls.length, 0);
    const u = await ctx.pool.query('SELECT messages, emergencies FROM ai_usage WHERE account_id = $1', [w.id]);
    assert.deepEqual(u.rows[0], { messages: 1, emergencies: 1 });
  });

  test('pertanyaan edukasi umum bukan darurat', async () => {
    const fake = new FakeAi();
    ctx = await setup({ ai: fake });
    const w = await registerWarga(ctx.app, '02');
    const ev = parseSse((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Apa tanda serangan jantung?'))).body);
    assert.deepEqual(ev[0]!.data, { emergency: false });
    assert.equal(fake.calls.length, 1);
  });

  test('batas harian 40 → 429 AI_DAILY_LIMIT; hari berikutnya pulih', async () => {
    const fake = new FakeAi();
    ctx = await setup({ ai: fake, rateLimits: { aiPerDay: 3, aiPerMinute: 1000 } });
    const w = await registerWarga(ctx.app, '02');
    for (let i = 0; i < 3; i++) assert.equal((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Tips kurangi garam?'))).statusCode, 200);
    const r = await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Tips kurangi garam?'));
    assert.equal(r.statusCode, 429);
    assert.equal(r.json().error.code, 'AI_DAILY_LIMIT');
    assert.equal(fake.calls.length, 3);
    // pesan darurat tetap dikirim walau batas tercapai
    const em = parseSse((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('bapak saya pingsan sekarang'))).body);
    assert.equal(em[0]!.data.emergency, true);
    ctx.clock.advance(86_400_000);
    assert.equal((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Tips kurangi garam?'))).statusCode, 200);
  });

  test('batas default 40/hari dan 10/menit per akun', async () => {
    ctx = await setup({ ai: new FakeAi() });
    assert.equal(ctx.deps.config.rateLimits.aiPerDay, 40);
    const w = await registerWarga(ctx.app, '02');
    for (let i = 0; i < 10; i++) assert.equal((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Halo'))).statusCode, 200);
    const r = await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Halo'));
    assert.equal(r.statusCode, 429);
    assert.equal(r.json().error.code, 'AI_RATE_LIMIT');
    ctx.clock.advance(60_001);
    assert.equal((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Halo'))).statusCode, 200);
  });

  test('validasi: pesan terakhir harus user, maks 20 pesan, maks 1000 karakter, konteks tanpa field lain', async () => {
    ctx = await setup({ ai: new FakeAi() });
    const w = await registerWarga(ctx.app, '02');
    const bad = [
      { messages: [{ role: 'user', content: 'a' }, { role: 'assistant', content: 'b' }] },
      { messages: Array.from({ length: 21 }, () => ({ role: 'user', content: 'a' })) },
      { messages: [{ role: 'user', content: 'x'.repeat(1001) }] },
      { messages: [] },
      ask('hai', { context: { name: 'Tariska' } }),
    ];
    for (const b of bad) assert.equal((await call(ctx.app, 'POST', '/ai/chat', w.token, b)).statusCode, 400);
    assert.equal((await call(ctx.app, 'POST', '/ai/chat', null, ask('hai'))).statusCode, 401);
  });

  test('galat di tengah aliran → event error AI_UNAVAILABLE', async () => {
    const fake = new FakeAi();
    fake.failAfter = 1;
    ctx = await setup({ ai: fake });
    const w = await registerWarga(ctx.app, '02');
    const ev = parseSse((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('Tips olahraga?'))).body);
    assert.deepEqual(ev.map((e) => e.event), ['meta', 'delta', 'error']);
    assert.equal(ev[2]!.data.code, 'AI_UNAVAILABLE');
  });

  test('refusal tanpa teks → pesan sopan + done refusal', async () => {
    const fake = new FakeAi();
    fake.chunks = [];
    fake.stopReason = 'refusal';
    ctx = await setup({ ai: fake });
    const w = await registerWarga(ctx.app, '02');
    const ev = parseSse((await call(ctx.app, 'POST', '/ai/chat', w.token, ask('xyz'))).body);
    assert.deepEqual(ev.map((e) => e.event), ['meta', 'delta', 'done']);
    assert.deepEqual(ev[2]!.data, { stopReason: 'refusal' });
  });
});

describe('deteksi darurat', () => {
  const cases: [string, boolean][] = [
    ['Apa tanda serangan jantung?', false],
    ['Kalau pada perempuan?', false],
    ['Apakah nyeri dada tanda serangan jantung?', false],
    ['Apakah keringat dingin termasuk gejala serangan jantung?', false],
    ['Kalau dada terasa tertekan, apa yang harus dilakukan?', false],
    ['Bagaimana jika saya tiba-tiba nyeri dada?', false],
    ['Apa saja gejala stroke seperti bicara pelo?', false],
    ['Berapa tekanan darah normal?', false],
    ['Saya nyeri dada sekarang', true],
    ['saya nyeri dada, apakah ini serangan jantung?', true],
    ['Dada saya terasa tertekan dan keringat dingin', true],
    ['dadaku sakit banget', true],
    ['Bapak saya pingsan', true],
    ['ibuku tiba-tiba bicara pelo dan wajah mencong', true],
    ['mendadak lumpuh sebelah', true],
    ['Tolong, suami saya sesak napas berat', true],
    ['sesak nafas parah dari tadi', true],
  ];
  for (const [text, expected] of cases) {
    test(`${expected ? 'DARURAT' : 'bukan darurat'}: "${text}"`, () => assert.equal(isEmergency(text), expected));
  }
});
