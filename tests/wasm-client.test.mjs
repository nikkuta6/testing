import test from 'node:test';
import assert from 'node:assert/strict';
import { WasmRunner } from '../dist/wasm-client.mjs';

// Protocol/lifecycle tests. Real language execution is separately checked against the Wasm binary.
class FakeWorker {
  static instances = [];
  constructor() { this.terminated = false; FakeWorker.instances.push(this); }
  postMessage(data) {
    if (data.type === 'init') queueMicrotask(() => this.onmessage?.({ data: { type: 'ready' } }));
    if (data.type === 'run' && data.source !== 'hang') {
      queueMicrotask(() => this.onmessage?.({ data: data.source === 'fail'
        ? { type: 'error', message: 'test failure' } : { type: 'result', id: data.id, result: { output: data.source } } }));
    }
  }
  terminate() { this.terminated = true; }
}
const original = globalThis.Worker;
globalThis.Worker = FakeWorker;
process.on('exit', () => { globalThis.Worker = original; });

test('new runs during module loading supersede old requests without losing a promise', async () => {
  const runner = new WasmRunner();
  const first = runner.run('first'), second = runner.run('second');
  const results = await Promise.allSettled([first, second]);
  assert.equal(results[0].status, 'rejected');
  assert.equal(results[0].reason.name, 'AbortError');
  assert.deepEqual(results[1].value, { output: 'second' });
  runner.cancel();
});

test('cancel terminates the worker and a new run uses a fresh instance', async () => {
  const runner = new WasmRunner();
  await runner.ready();
  const old = runner.worker;
  const pending = runner.run('hang');
  const rejected = assert.rejects(pending, { name: 'AbortError' });
  await Promise.resolve();
  runner.cancel();
  await rejected;
  assert.equal(old.terminated, true);
  assert.deepEqual(await runner.run('recovered'), { output: 'recovered' });
  assert.notEqual(runner.worker, old);
  runner.cancel();
});

test('worker errors reject the request and clear broken state', async () => {
  const runner = new WasmRunner();
  await assert.rejects(runner.run('fail'), /test failure/);
  assert.equal(runner.worker, null);
  assert.deepEqual(await runner.run('ok'), { output: 'ok' });
  runner.cancel();
});

test('source limits are enforced before a worker is created', async () => {
  const runner = new WasmRunner();
  await assert.rejects(runner.run('я'.repeat(1 << 20)), /1 МБ/);
  assert.equal(runner.worker, null);
});
