import pg from 'pg';

export type Pool = pg.Pool;
export type PoolClient = pg.PoolClient;
/** Apa pun yang bisa menjalankan query: pool atau klien di dalam transaksi. */
export type Queryable = Pick<pg.Pool, 'query'> | pg.PoolClient;

// BIGINT (int8) dikembalikan sebagai number: semua nilai kita (epoch ms, seq) < 2^53.
pg.types.setTypeParser(20, (v) => Number(v));
// DATE tetap string YYYY-MM-DD, jangan dikonversi ke Date (zona waktu).
pg.types.setTypeParser(1082, (v) => v);

export function createPool(databaseUrl: string): Pool {
  return new pg.Pool({ connectionString: databaseUrl, max: 10, idleTimeoutMillis: 30_000 });
}

/** Jalankan fn di dalam transaksi; rollback bila melempar galat. */
export async function withTx<T>(pool: Pool, fn: (c: PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const out = await fn(client);
    await client.query('COMMIT');
    return out;
  } catch (e) {
    await client.query('ROLLBACK').catch(() => undefined);
    throw e;
  } finally {
    client.release();
  }
}

/**
 * Kunci penulisan entitas. Semua transaksi yang mengambil nilai `seq` baru memegang kunci ini,
 * sehingga urutan commit = urutan seq dan pull dengan kursor tidak pernah melewatkan baris.
 */
export async function lockEntityWrites(c: PoolClient): Promise<void> {
  await c.query('SELECT pg_advisory_xact_lock(726201)');
}
