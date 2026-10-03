import { createPool } from '../db/pool.js';
import { runMigrations } from '../db/migrate.js';

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL wajib diisi');
const pool = createPool(url);
const applied = await runMigrations(pool);
console.log(applied.length ? `Migrasi diterapkan: ${applied.join(', ')}` : 'Skema sudah terbaru.');
await pool.end();
