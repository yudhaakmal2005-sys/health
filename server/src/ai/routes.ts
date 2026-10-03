import type { FastifyInstance, FastifyReply } from 'fastify';
import { z } from 'zod';
import type { Deps } from '../core/types.js';
import { ApiError } from '../core/errors.js';
import { parse } from '../core/validate.js';
import { localDate } from '../core/time.js';
import { SlidingWindowLimiter } from '../core/limiter.js';
import { actorOf, requireAuth } from '../auth/guard.js';
import { isEmergency } from './emergency.js';
import { EMERGENCY_MESSAGE, REFUSAL_MESSAGE, SYSTEM_PROMPT, contextText } from './prompt.js';

const chatSchema = z.object({
  messages: z.array(z.object({
    role: z.enum(['user', 'assistant']),
    content: z.string().trim().min(1).max(1000),
  })).min(1).max(20).refine((m) => m[m.length - 1]?.role === 'user', { message: 'Pesan terakhir harus dari user' }),
  context: z.object({
    ageBand: z.string().regex(/^\d{1,3}(-\d{1,3}|\+)?$/).optional(),
    sex: z.enum(['MALE', 'FEMALE']).optional(),
    factors: z.array(z.string().regex(/^[a-z_]{1,30}$/)).max(12).optional(),
    steps: z.number().int().min(0).max(200_000).optional(),
  }).strict().nullable().optional(),
});

/** Penulis SSE di atas respons mentah (reply.hijack). Header keamanan/CORS dari hook tetap disalin. */
function openSse(reply: FastifyReply) {
  reply.hijack();
  const headers: Record<string, string | number | string[]> = {};
  for (const [k, v] of Object.entries(reply.getHeaders())) if (v !== undefined) headers[k] = v as string | number | string[];
  reply.raw.writeHead(200, {
    ...headers,
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-cache, no-transform',
    connection: 'keep-alive',
    'x-accel-buffering': 'no',
  });
  return {
    send(event: string, data: unknown) {
      if (!reply.raw.writableEnded) reply.raw.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    },
    end() {
      if (!reply.raw.writableEnded) reply.raw.end();
    },
  };
}

export async function aiRoutes(app: FastifyInstance, deps: Deps): Promise<void> {
  const perMinute = new SlidingWindowLimiter(deps.config.rateLimits.aiPerMinute, 60_000);
  const daily = deps.config.rateLimits.aiPerDay;

  app.post('/ai/chat', { preHandler: requireAuth(deps) }, async (req, reply) => {
    const actor = actorOf(req);
    const ai = deps.ai;
    if (!ai) throw new ApiError(503, 'AI_DISABLED', 'Tanya SEHATI (AI) belum diaktifkan di server ini.');
    const body = parse(chatSchema, req.body);
    const now = deps.now();
    if (!perMinute.tryHit(actor.id, now)) {
      throw new ApiError(429, 'AI_RATE_LIMIT', 'Terlalu cepat. Tunggu sebentar sebelum bertanya lagi.');
    }

    const last = body.messages[body.messages.length - 1]!.content;
    const emergency = isEmergency(last);

    // Hitung pemakaian secara atomik; hanya hitungan, tanpa isi pesan.
    const day = localDate(now, deps.config.timeZone);
    const used = await deps.pool.query(
      `INSERT INTO ai_usage (account_id, day, messages, emergencies) VALUES ($1, $2, 1, $3)
       ON CONFLICT (account_id, day) DO UPDATE SET messages = ai_usage.messages + 1, emergencies = ai_usage.emergencies + EXCLUDED.emergencies
         WHERE ai_usage.messages < $4
       RETURNING messages`,
      [actor.id, day, emergency ? 1 : 0, daily],
    );
    if ((used.rowCount ?? 0) === 0) {
      if (!emergency) throw new ApiError(429, 'AI_DAILY_LIMIT', `Batas ${daily} pertanyaan per hari sudah tercapai. Coba lagi besok.`);
      // Pesan darurat tidak pernah dibatasi; tetap dihitung sebagai kejadian darurat.
      await deps.pool.query('UPDATE ai_usage SET emergencies = emergencies + 1 WHERE account_id = $1 AND day = $2', [actor.id, day]);
    }

    const sse = openSse(reply);
    sse.send('meta', { emergency });
    if (emergency) {
      // Pesan darurat selalu dikirim, walaupun batas harian tercapai. Model tidak dipanggil.
      sse.send('delta', { text: EMERGENCY_MESSAGE });
      sse.send('done', { stopReason: 'emergency' });
      sse.end();
      return;
    }

    const controller = new AbortController();
    const onClose = () => controller.abort();
    reply.raw.on('close', onClose);
    let sentText = false;
    let done = false;
    try {
      for await (const ev of ai.streamChat({
        system: SYSTEM_PROMPT,
        context: contextText(body.context ?? undefined),
        messages: body.messages,
        signal: controller.signal,
      })) {
        if (controller.signal.aborted) break;
        if (ev.type === 'text') {
          if (ev.text.length === 0) continue;
          sentText = true;
          sse.send('delta', { text: ev.text });
        } else {
          if (ev.stopReason === 'refusal' && !sentText) sse.send('delta', { text: REFUSAL_MESSAGE });
          sse.send('done', { stopReason: ev.stopReason });
          done = true;
        }
      }
      if (!done && !controller.signal.aborted) sse.send('error', { code: 'AI_UNAVAILABLE', message: 'Jawaban terputus. Coba lagi.' });
    } catch (err) {
      if (!controller.signal.aborted) {
        req.log.warn({ errName: (err as Error).name, status: (err as { status?: number }).status }, 'ai stream failed');
        sse.send('error', { code: 'AI_UNAVAILABLE', message: 'Tanya SEHATI sedang tidak dapat menjawab. Coba lagi nanti.' });
      }
    } finally {
      reply.raw.off('close', onClose);
      sse.end();
    }
  });
}
