import test from 'node:test';
import assert from 'node:assert/strict';
import { startServer } from '../scripts/serve.mjs';

test('static deployment serves Wasm MIME and does not provide a program execution API', async t => {
  const server = await startServer({ port: 0 });
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const page = await fetch(origin);
  assert.match(await page.text(), /WebAssembly/);
  const binary = await fetch(origin + '/wasm/app.wasm');
  assert.equal(binary.headers.get('content-type'), 'application/wasm');
  assert.deepEqual(Array.from(new Uint8Array(await binary.arrayBuffer()).slice(0, 4)), [0, 97, 115, 109]);
  assert.equal((await fetch(origin + '/api/run', { method: 'POST', body: 'print(1);' })).status, 405);
  assert.equal((await fetch(origin + '/api/run')).status, 404);
  assert.equal((await fetch(origin + '/..%2fpackage.json')).status, 404);
  assert.equal((await fetch(origin + '/%ZZ')).status, 400);
});
