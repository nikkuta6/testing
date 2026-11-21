import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { loadRuntime } from '../dist/wasm-loader.mjs';

const runtime = await loadRuntime();
const wasm = runtime.exports;
const run = (source, debug = false) => JSON.parse(wasm.run(source, debug));

test('the actual compiled module exports the language and fractal runtimes', () => {
  assert.ok(runtime.module instanceof WebAssembly.Module);
  assert.ok(runtime.instance instanceof WebAssembly.Instance);
  for (const name of ['run', 'orbit', 'sampleEscape', 'renderBand']) assert.equal(typeof wasm[name], 'function');
  assert.equal(wasm.version(), 'WebAssembly GC');
});

test('Wasm preserves comparison direction, recursion, arrays and complex values', () => {
  assert.equal(run('print(5 <= 1, 5 < 1, 5 > 1, 5 >= 1);').output, '0 0 1 1\n');
  assert.equal(run('fun int f(int n) { if n <= 1 { return 1; } return n * f(n - 1); } print(f(5));').output, '120\n');
  assert.equal(run('complex a[] = [1i, 2 + 3i]; print(a, a[1] * conj(a[1]));').output, '[0.0+1.0i, 2.0+3.0i] 13.0+0.0i\n');
});

test('Wasm returns source locations and isolated debug snapshots', () => {
  assert.equal(run('int имя = 2;\nprint(имя);').output, '2\n');
  assert.equal(run('int x = 1;\nprint(missing);').syntaxError.line, 2);
  const debug = run('canvas(80, 60);\ncircle(20, 20, 5);', true);
  assert.equal(debug.steps[0].graphics, 0);
  assert.equal(debug.steps.at(-1).graphics, 2);
  assert.equal(debug.graphics.length, 2);
  assert.ok(debug.lines.includes(2));
});

test('Wasm preserves partial output on failure and starts each run with clean memory', () => {
  const failure = run('int x = 1; print(x); circle(10, 10, 2); int bad = 1 / 0;');
  assert.equal(failure.output, '1\n');
  assert.match(failure.runtimeError, /Деление на ноль/);
  assert.equal(failure.graphics.length, 1);
  assert.deepEqual(run('print(2);').variables, {});
});

test('Wasm agrees with every exported JVM regression fixture', async () => {
  const fixtures = JSON.parse(await readFile(new URL('../testing-9a/wasm-runtime/target/parity-fixtures.json', import.meta.url), 'utf8'));
  assert.ok(fixtures.length > 300);
  const failures = [];
  for (const fixture of fixtures) {
    try { assert.deepEqual(run(fixture.source, true), fixture.expected); }
    catch (error) { failures.push({ source: fixture.source, message: error.message }); }
  }
  assert.equal(failures.length, 0, JSON.stringify(failures.slice(0, 3), null, 2));
  console.log('Compared', fixtures.length, 'JVM/Wasm fixtures.');
});
