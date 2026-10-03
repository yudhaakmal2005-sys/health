import type { AiClient, ChatEvent, ChatRequest } from './client.js';

export interface OpenAiCompatOptions {
  apiKey: string;
  /** Contoh: https://api.openai.com/v1, https://api.deepseek.com/v1, https://generativelanguage.googleapis.com/v1beta/openai */
  baseURL: string;
  model: string;
  maxTokens: number;
  timeoutMs?: number;
}

/**
 * Penyedia AI berformat "OpenAI-compatible" (POST {baseURL}/chat/completions, stream SSE).
 * Dipakai bila AI_PROVIDER=openai — cocok untuk OpenAI, DeepSeek, Groq, OpenRouter, Gemini (endpoint OpenAI), dll.
 */
export class OpenAiCompatClient implements AiClient {
  constructor(private readonly opts: OpenAiCompatOptions) {}

  async *streamChat(req: ChatRequest): AsyncIterable<ChatEvent> {
    const system = req.context ? `${req.system}\n\n${req.context}` : req.system;
    const timeout = AbortSignal.timeout(this.opts.timeoutMs ?? 60_000);
    const res = await fetch(`${this.opts.baseURL.replace(/\/+$/, '')}/chat/completions`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${this.opts.apiKey}` },
      body: JSON.stringify({
        model: this.opts.model,
        stream: true,
        max_tokens: this.opts.maxTokens,
        messages: [{ role: 'system', content: system }, ...req.messages.map((m) => ({ role: m.role, content: m.content }))],
      }),
      signal: AbortSignal.any([req.signal, timeout]),
    });
    if (!res.ok || !res.body) {
      const detail = (await res.text().catch(() => '')).slice(0, 200);
      throw new Error(`AI provider HTTP ${res.status}${detail ? `: ${detail}` : ''}`);
    }

    const decoder = new TextDecoder();
    let buf = '';
    let stopReason = 'end_turn';
    for await (const chunk of res.body as unknown as AsyncIterable<Uint8Array>) {
      buf += decoder.decode(chunk, { stream: true });
      let nl: number;
      while ((nl = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, nl).trim();
        buf = buf.slice(nl + 1);
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (data === '[DONE]') { yield { type: 'done', stopReason }; return; }
        let json: { choices?: { delta?: { content?: string | null }; finish_reason?: string | null }[] };
        try { json = JSON.parse(data); } catch { continue; }
        const choice = json.choices?.[0];
        const text = choice?.delta?.content;
        if (text) yield { type: 'text', text };
        if (choice?.finish_reason) stopReason = choice.finish_reason === 'length' ? 'max_tokens' : 'end_turn';
      }
    }
    yield { type: 'done', stopReason };
  }
}
