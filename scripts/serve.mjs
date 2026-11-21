import { createServer } from 'node:http';
import { readFile, realpath, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { dist } from './paths.mjs';

const types = { '.lang': 'text/plain; charset=utf-8', '.html': 'text/html; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.wasm': 'application/wasm', '.json': 'application/json; charset=utf-8' };

export async function startServer({ port = 8080, directory = dist } = {}) {
  const base = await realpath(directory).catch(() => { throw new Error('Build the app first: npm run build'); });
  const server = createServer(async (req, res) => {
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405, { Allow: 'GET, HEAD' }); res.end(); return; }
    try {
      const urlPath = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
      const candidate = path.resolve(base, '.' + (urlPath === '/' ? '/index.html' : urlPath));
      const file = await realpath(candidate);
      const relative = path.relative(base, file);
      if (relative.startsWith('..') || path.isAbsolute(relative) || !(await stat(file)).isFile()) {
        res.writeHead(404); res.end('Not found'); return;
      }
      const bytes = await readFile(file);
      res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream',
        'Content-Length': bytes.length, 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'no-cache' });
      res.end(req.method === 'HEAD' ? undefined : bytes);
    } catch (error) {
      res.writeHead(error instanceof URIError ? 400 : 404); res.end('Not found');
    }
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
  return server;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 8080);
  await startServer({ port });
  console.log(`WebAssembly IDE: http://127.0.0.1:${port}/ (static files only)`);
}
