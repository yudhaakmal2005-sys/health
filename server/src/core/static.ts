import fs from 'node:fs';
import path from 'node:path';
import type { FastifyReply, FastifyRequest } from 'fastify';

const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon',
  '.json': 'application/json', '.woff2': 'font/woff2', '.woff': 'font/woff', '.txt': 'text/plain; charset=utf-8',
  '.webmanifest': 'application/manifest+json', '.apk': 'application/vnd.android.package-archive',
};

/** CSP untuk dashboard (aset dari domain sendiri saja); API tetap memakai CSP ketat bawaan helmet. */
const WEB_CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

/**
 * Penyaji berkas dashboard (opsional, WEB_DIR) untuk hosting tanpa Caddy/Nginx — mis. "Setup Node.js App" di cPanel.
 * Mengembalikan false bila permintaan bukan untuk dashboard sehingga pemanggil mengirim 404 API.
 */
export function serveWeb(webDir: string, req: FastifyRequest, reply: FastifyReply): boolean {
  if (req.method !== 'GET' && req.method !== 'HEAD') return false;
  const urlPath = decodeURIComponent((req.url.split('?')[0] ?? '/'));
  if (urlPath.startsWith('/api/')) return false;
  const root = path.resolve(webDir);
  let file = path.resolve(root, '.' + path.posix.normalize(urlPath));
  if (file !== root && !file.startsWith(root + path.sep)) return false; // cegah path traversal
  let stat = fs.statSync(file, { throwIfNoEntry: false });
  if (stat?.isDirectory()) { file = path.join(file, 'index.html'); stat = fs.statSync(file, { throwIfNoEntry: false }); }
  if (!stat?.isFile()) {
    if (path.extname(urlPath)) return false; // aset yang tidak ada → 404
    file = path.join(root, 'index.html'); // SPA fallback
    stat = fs.statSync(file, { throwIfNoEntry: false });
    if (!stat?.isFile()) return false;
  }
  const ext = path.extname(file).toLowerCase();
  reply.header('content-type', TYPES[ext] ?? 'application/octet-stream');
  reply.header('content-security-policy', WEB_CSP);
  reply.header('content-length', stat.size);
  if (ext === '.apk') reply.header('content-disposition', `attachment; filename="${path.basename(file)}"`);
  reply.header('cache-control', file.includes(`${path.sep}assets${path.sep}`) ? 'public, max-age=31536000, immutable' : 'no-cache');
  reply.code(200).send(req.method === 'HEAD' ? '' : fs.createReadStream(file));
  return true;
}
