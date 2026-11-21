import test from 'node:test';
import assert from 'node:assert/strict';
import { Worker as NodeWorker } from 'node:worker_threads';
import { splitRegions, startMandelbrotRender } from '../dist/mandelbrot-pool.mjs';
import { PLANE, renderRows, zoomPlane } from '../dist/mandelbrot.mjs';

test('ten regions cover every row exactly once, including uneven image heights', () => {
  for (const height of [1, 7, 479, 480, 481]) {
    const regions = splitRegions(height, 10);
    assert.equal(regions.length, Math.min(10, height));
    assert.equal(regions[0].fromRow, 0);
    assert.equal(regions.at(-1).toRow, height);
    regions.forEach((region, i) => {
      assert.ok(region.toRow > region.fromRow);
      if (i) assert.equal(region.fromRow, regions[i - 1].toRow);
    });
    const sizes = regions.map(r => r.toRow - r.fromRow);
    assert.ok(Math.max(...sizes) - Math.min(...sizes) <= 1);
  }
  for (const threads of [0, -1, 1.5, 33, NaN]) assert.throws(() => splitRegions(480, threads), RangeError);
});

function fixture(threads = 10) {
  const workers = [], progress = [], completed = [], errors = [];
  const job = startMandelbrotRender({ width: 4, height: 23, threads, plane: PLANE, limit: 80,
    createWorker() {
      const worker = { terminated: false, postMessage(data) { this.request = data; },
        terminate() { this.terminated = true; } };
      workers.push(worker);
      return worker;
    },
    onProgress: percent => progress.push(percent),
    onComplete: pixels => completed.push(pixels), onError: error => errors.push(error)
  });
  return { workers, progress, completed, errors, job };
}

function send(worker, fromRow = worker.request.fromRow, toRow = worker.request.toRow) {
  const { width } = worker.request;
  const pixels = new Uint8ClampedArray((toRow - fromRow) * width * 4);
  for (let y = fromRow; y < toRow; y++) pixels.fill(y, (y - fromRow) * width * 4, (y - fromRow + 1) * width * 4);
  worker.onmessage({ data: { fromRow, toRow, pixels: pixels.buffer } });
}

test('independent workers assemble out-of-order regions and progress counts actual rows', () => {
  const f = fixture();
  assert.equal(f.workers.length, 10);
  for (const worker of [...f.workers].reverse()) send(worker);
  assert.equal(f.completed.length, 1);
  assert.equal(f.errors.length, 0);
  assert.equal(f.progress.at(-1), 100);
  assert.ok(f.progress.every((p, i) => !i || p >= f.progress[i - 1]));
  for (let y = 0; y < 23; y++) assert.equal(f.completed[0][y * 4 * 4], y);
  assert.ok(f.workers.every(w => w.terminated));
});

test('cancel terminates the whole pool and ignores queued messages from the previous view', () => {
  const f = fixture();
  send(f.workers[0]);
  const count = f.progress.length;
  f.job.cancel();
  for (const worker of f.workers) send(worker);
  assert.ok(f.workers.every(w => w.terminated));
  assert.equal(f.completed.length, 0);
  assert.equal(f.progress.length, count);
});

test('one worker error or an overlapping band cancels all regions', () => {
  for (const failure of ['error', 'overlap', 'buffer']) {
    const f = fixture();
    const worker = f.workers[0];
    if (failure === 'error') worker.onerror({});
    else if (failure === 'overlap') send(worker, 1, 2);
    else worker.onmessage({ data: { fromRow: 0, toRow: 1, pixels: new ArrayBuffer(1) } });
    assert.equal(f.errors.length, 1);
    assert.equal(f.completed.length, 0);
    assert.ok(f.workers.every(w => w.terminated));
  }
});

test('one and ten regions execute the language program and produce identical pixels after zoom', () => {
  for (const plane of [PLANE, zoomPlane(PLANE, { re: -0.75, im: 0.1 }, 128)]) {
    const single = new Uint8ClampedArray(21 * 23 * 4);
    renderRows(single, 21, 23, 0, 23, plane, 160);
    const parallel = new Uint8ClampedArray(single.length);
    for (const region of splitRegions(23, 10)) renderRows(parallel, 21, 23, region.fromRow, region.toRow, plane, 160);
    assert.deepEqual(parallel, single);
  }
});

test('ten real worker threads each run their own Wasm VM and return the same raster', async t => {
  const width = 21, height = 23, limit = 160;
  const expected = new Uint8ClampedArray(width * height * 4);
  renderRows(expected, width, height, 0, height, PLANE, limit);
  let job;
  t.after(() => job?.cancel());
  const actual = await new Promise((resolve, reject) => {
    job = startMandelbrotRender({ width, height, threads: 10, plane: PLANE, limit,
      createWorker() {
        const worker = new NodeWorker(`
          const { parentPort } = require('node:worker_threads');
          globalThis.self = { postMessage: (...args) => parentPort.postMessage(...args) };
          import(${JSON.stringify(new URL('../dist/mandelbrot-worker.mjs', import.meta.url).href)})
            .then(() => parentPort.on('message', data => self.onmessage({ data })))
            .catch(error => { throw error; });
        `, { eval: true });
        worker.on('message', data => worker.onmessage?.({ data }));
        worker.on('error', error => worker.onerror?.(error));
        return worker;
      }, onComplete: resolve, onError: reject
    });
  });
  assert.deepEqual(actual, expected);
  assert.equal(job.regions.length, 10);
});
