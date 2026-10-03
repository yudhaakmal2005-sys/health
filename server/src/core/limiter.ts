/**
 * Pembatas jendela geser sederhana di memori (satu instance API). Dipakai untuk batas per akun/per ID
 * yang tidak dapat diungkapkan sebagai batas per IP (mis. AI 10/menit/akun, aktivasi 5/jam/ID).
 */
export class SlidingWindowLimiter {
  private readonly hits = new Map<string, number[]>();

  constructor(
    private readonly max: number,
    private readonly windowMs: number,
  ) {}

  /** Catat satu percobaan; false bila sudah melewati batas (percobaan tidak dicatat). */
  tryHit(key: string, now: number): boolean {
    const from = now - this.windowMs;
    const list = (this.hits.get(key) ?? []).filter((t) => t > from);
    if (list.length >= this.max) {
      this.hits.set(key, list);
      return false;
    }
    list.push(now);
    this.hits.set(key, list);
    if (this.hits.size > 10_000) this.prune(now);
    return true;
  }

  private prune(now: number): void {
    const from = now - this.windowMs;
    for (const [k, v] of this.hits) if (!v.some((t) => t > from)) this.hits.delete(k);
  }
}
