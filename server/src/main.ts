import { loadConfig } from './config.js';
import { createPool } from './db/pool.js';
import { runMigrations } from './db/migrate.js';
import { buildApp } from './app.js';
import { ensureAdmin } from './core/bootstrap.js';
import { AnthropicAiClient } from './ai/client.js';
import type { Deps } from './core/types.js';

async function main(): Promise<void> {
  const config = loadConfig();
  const pool = createPool(config.databaseUrl);
  const applied = await runMigrations(pool);
  const ai = config.anthropicApiKey
    ? new AnthropicAiClient({ apiKey: config.anthropicApiKey, model: config.aiModel, effort: config.aiEffort, maxTokens: config.aiMaxTokens })
    : null;
  const deps: Deps = { pool, config, ai, now: () => Date.now() };
  const app = await buildApp(deps);
  if (applied.length > 0) app.log.info({ applied }, 'migrations applied');
  await ensureAdmin(pool, config, app.log);
  if (!ai) app.log.warn('ANTHROPIC_API_KEY tidak diatur: Tanya SEHATI AI nonaktif (503 AI_DISABLED).');

  // Pemeliharaan berkala: sesi kedaluwarsa & receipt idempoten lama (> 180 hari) dihapus.
  const maintenance = setInterval(() => {
    const now = Date.now();
    pool.query('DELETE FROM sessions WHERE expires_at < $1', [now])
      .then(() => pool.query('DELETE FROM sync_receipts WHERE created_at < $1', [now - 180 * 86_400_000]))
      .catch((err: unknown) => app.log.warn({ err }, 'maintenance failed'));
  }, 6 * 3_600_000);
  maintenance.unref();

  const shutdown = async (signal: string) => {
    app.log.info({ signal }, 'shutting down');
    clearInterval(maintenance);
    await app.close();
    await pool.end();
    process.exit(0);
  };
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGINT', () => void shutdown('SIGINT'));

  await app.listen({ port: config.port, host: config.host });
}

main().catch((err: unknown) => {
  console.error('Gagal menjalankan server:', err instanceof Error ? err.message : err);
  process.exit(1);
});
