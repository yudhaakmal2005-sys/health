import Anthropic from '@anthropic-ai/sdk';
import type { Effort } from '../config.js';

export interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatRequest {
  /** Prompt sistem stabil (di-cache). */
  system: string;
  /** Konteks per permintaan (opsional), diletakkan setelah prompt sistem agar cache tetap berlaku. */
  context: string | null;
  messages: ChatTurn[];
  signal: AbortSignal;
}

export type ChatEvent = { type: 'text'; text: string } | { type: 'done'; stopReason: string };

/** Antarmuka model AI; pengujian menyuntikkan implementasi palsu sehingga tidak ada panggilan API nyata. */
export interface AiClient {
  streamChat(req: ChatRequest): AsyncIterable<ChatEvent>;
}

export interface AnthropicOptions {
  apiKey: string;
  model: string;
  effort: Effort;
  maxTokens: number;
  /** Hanya untuk pengujian terhadap server tiruan lokal. */
  baseURL?: string;
}

/**
 * Implementasi dengan @anthropic-ai/sdk: streaming, thinking adaptif (bawaan Claude Opus 5.5 — tidak dapat
 * dimatikan; kedalaman diatur lewat effort), dan fallback sisi server "default" bila model menolak (refusal).
 */
export class AnthropicAiClient implements AiClient {
  private readonly client: Anthropic;

  constructor(private readonly opts: AnthropicOptions) {
    this.client = new Anthropic({ apiKey: opts.apiKey, maxRetries: 2, timeout: 60_000, ...(opts.baseURL ? { baseURL: opts.baseURL } : {}) });
  }

  async *streamChat(req: ChatRequest): AsyncIterable<ChatEvent> {
    const system: Anthropic.Beta.BetaTextBlockParam[] = [
      { type: 'text', text: req.system, cache_control: { type: 'ephemeral' } },
    ];
    if (req.context) system.push({ type: 'text', text: req.context });

    const caps = modelCapabilities(this.opts.model);
    const stream = this.client.beta.messages.stream(
      {
        model: this.opts.model,
        max_tokens: this.opts.maxTokens,
        ...(caps.adaptive ? { thinking: { type: 'adaptive' as const }, output_config: { effort: this.opts.effort } } : {}),
        ...(caps.defaultFallbacks ? { betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default' as const } : {}),
        system,
        messages: req.messages.map((m) => ({ role: m.role, content: m.content })),
      },
      { signal: req.signal },
    );

    for await (const event of stream) {
      if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') {
        yield { type: 'text', text: event.delta.text };
      }
    }
    const final = await stream.finalMessage();
    yield { type: 'done', stopReason: final.stop_reason ?? 'end_turn' };
  }
}

/**
 * Parameter yang didukung model (AI_MODEL dapat diganti). Thinking adaptif + effort: generasi 4.6 ke atas
 * (bukan Haiku 4.5). Fallback sisi server "default": Claude Opus 5.5 / Opus 5 / Sonnet 5.5 / Fable 5.1.
 */
export function modelCapabilities(model: string): { adaptive: boolean; defaultFallbacks: boolean } {
  const m = model.toLowerCase();
  const legacy = /haiku|claude-3|-4-5(\b|$)|-4-1(\b|$)|-4-0|sonnet-4(\b|$)|opus-4(\b|$)/.test(m) && !/-4-[6-9]/.test(m);
  const defaultFallbacks = /^claude-(opus-5-5|opus-5|sonnet-5-5|fable-5-1)$/.test(m);
  return { adaptive: !legacy, defaultFallbacks };
}
