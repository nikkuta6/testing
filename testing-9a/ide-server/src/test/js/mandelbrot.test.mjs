import test from 'node:test';
import assert from 'node:assert/strict';
import { computeOrbit, escapeIterations, pixelToComplex, complexToPixel, PLANE,
  zoomPlane, zoomLevel, iterationLimit, sampleEscape, escapeColor, renderRows, MAX_ZOOM
} from '../../main/resources/static/mandelbrot.mjs';

test('orbit starts at C, follows z² + C and includes the first escaping point', () => {
  assert.deepEqual(computeOrbit(1, 0), {
    points: [{ re: 1, im: 0 }, { re: 2, im: 0 }, { re: 5, im: 0 }], escaped: true
  });
  assert.deepEqual(computeOrbit(3, 0), { points: [{ re: 3, im: 0 }], escaped: true });
});

test('fixed points and periodic orbits stop at the requested limit', () => {
  const zero = computeOrbit(0, 0, 5);
  assert.equal(zero.escaped, false);
  assert.deepEqual(zero.points, Array(5).fill({ re: 0, im: 0 }));
  assert.deepEqual(computeOrbit(-1, 0, 4).points, [
    { re: -1, im: 0 }, { re: 0, im: 0 }, { re: -1, im: 0 }, { re: 0, im: 0 }
  ]);
  assert.equal(computeOrbit(-2, 0, 1000).escaped, false); // |z| = 2 alone is not escape.
});

test('complex orbit uses both components and respects conjugation symmetry', () => {
  assert.deepEqual(computeOrbit(0, 1, 4).points, [
    { re: 0, im: 1 }, { re: -1, im: 1 }, { re: 0, im: -1 }, { re: -1, im: 1 }
  ]);
  const upper = computeOrbit(-0.75, 0.1, 80);
  const lower = computeOrbit(-0.75, -0.1, 80);
  assert.equal(upper.escaped, true);
  assert.equal(upper.points.length, 33);
  assert.deepEqual(lower.points, upper.points.map(p => ({ re: p.re, im: -p.im })));
});

test('background and orbit agree on escape times', () => {
  for (const [re, im] of [[1, 0], [0, 0], [-1, 0], [-0.75, 0.1], [0, 1], [3, 0]]) {
    const orbit = computeOrbit(re, im, 200);
    assert.equal(escapeIterations(re, im, 200), orbit.escaped ? orbit.points.length : 0);
  }
});

test('escape on the final permitted step differs from no observed escape', () => {
  assert.equal(escapeIterations(1, 0, 3), 3);
  assert.equal(escapeIterations(1, 0, 2), 0);
});

test('pixel mapping reverses the screen Y axis and preserves complex-plane scale', () => {
  assert.deepEqual(pixelToComplex(0, 0, 640, 480), { re: PLANE.left, im: PLANE.top });
  assert.deepEqual(pixelToComplex(640, 480, 640, 480), { re: PLANE.right, im: PLANE.bottom });
  assert.deepEqual(pixelToComplex(320, 240, 640, 480), { re: -0.75, im: 0 });
  for (const [re, im] of [[0, 0], [-1, 0.5], [-2.5, -1.3125], [1, 1.3125]]) {
    const p = complexToPixel(re, im, 640, 480);
    const c = pixelToComplex(p.x, p.y, 640, 480);
    assert.ok(Math.abs(c.re - re) < 1e-12);
    assert.ok(Math.abs(c.im - im) < 1e-12);
  }
  const origin = complexToPixel(0, 0, 640, 480);
  const unit = complexToPixel(1, 1, 640, 480);
  assert.ok(Math.abs(unit.x - origin.x - (origin.y - unit.y)) < 1e-12);
});

test('click mapping remains the same when the displayed canvas is scaled', () => {
  for (const scale of [0.25, 0.5, 1, 1.5]) {
    const c = pixelToComplex((320 * scale) * 640 / (640 * scale),
      (240 * scale) * 480 / (480 * scale), 640, 480);
    assert.deepEqual(c, { re: -0.75, im: 0 });
  }
});

test('invalid iteration counts and non-finite C are rejected', () => {
  for (const limit of [0, -1, 1.5, 1001, NaN, Infinity]) {
    assert.throws(() => computeOrbit(0, 0, limit), RangeError);
  }
  assert.throws(() => computeOrbit(Infinity, 0), RangeError);
  assert.throws(() => computeOrbit(0, NaN), RangeError);
});

test('zoom preserves the complex coordinate under the pointer and equal axis scales', () => {
  const anchor = pixelToComplex(213, 150, 640, 480);
  const plane = zoomPlane(PLANE, anchor, 8);
  const after = pixelToComplex(213, 150, 640, 480, plane);
  assert.ok(Math.abs(after.re - anchor.re) < 1e-14);
  assert.ok(Math.abs(after.im - anchor.im) < 1e-14);
  assert.ok(Math.abs(zoomLevel(plane) - 8) < 1e-12);
  assert.ok(Math.abs((plane.right - plane.left) / (plane.top - plane.bottom) - 4 / 3) < 1e-12);
  const pixel = complexToPixel(anchor.re, anchor.im, 640, 480, plane);
  assert.ok(Math.abs(pixel.x - 213) < 1e-10);
  assert.ok(Math.abs(pixel.y - 150) < 1e-10);
});

test('zoom-out restores the original view and invalid zoom factors are rejected', () => {
  const anchor = { re: -0.75, im: 0.1 };
  const plane = zoomPlane(PLANE, anchor, 16);
  assert.deepEqual(zoomPlane(plane, anchor, 1 / 32), PLANE);
  for (const factor of [0, -1, Infinity, NaN]) assert.throws(() => zoomPlane(PLANE, anchor, factor), RangeError);
});

test('deep zoom adds iterations and keeps adjacent pixels numerically distinct', () => {
  const anchor = { re: -0.743643887037151, im: 0.13182590420533 };
  const deep = zoomPlane(PLANE, anchor, 1e20);
  assert.ok(Math.abs(zoomLevel(deep) / MAX_ZOOM - 1) < 0.001);
  assert.ok(iterationLimit(deep) > iterationLimit(PLANE));
  assert.ok(iterationLimit(deep) <= 6000);
  assert.notEqual(pixelToComplex(320, 240, 640, 480, deep).re, pixelToComplex(321, 240, 640, 480, deep).re);
  assert.notEqual(pixelToComplex(320, 240, 640, 480, deep).im, pixelToComplex(320, 241, 640, 480, deep).im);
  let last = 0;
  for (const zoom of [1, 2, 16, 1024, 1e6, 1e12]) {
    const limit = iterationLimit(zoomPlane(PLANE, anchor, zoom));
    assert.ok(limit >= last);
    last = limit;
  }
});

test('escape colors distinguish outside points and retain variation at late escape times', () => {
  assert.deepEqual(escapeColor(sampleEscape(0, 0)), [10, 12, 20]);
  assert.notDeepEqual(escapeColor(sampleEscape(1, 0)), escapeColor(sampleEscape(3, 0)));
  assert.notDeepEqual(escapeColor({ iterations: 100, smooth: 100 }), escapeColor({ iterations: 200, smooth: 200 }));
  const a = sampleEscape(3, 0), b = sampleEscape(3.00000001, 0);
  assert.notEqual(a.smooth, b.smooth);
  assert.ok(Math.abs(a.smooth - b.smooth) < 1e-6);
  for (const channel of escapeColor(a)) assert.ok(Number.isInteger(channel) && channel >= 0 && channel <= 255);
});

test('a new viewport is sampled again and row chunks produce the same raster', () => {
  const plane = { left: 0, right: 2, top: 2, bottom: -2 };
  const pixels = new Uint8ClampedArray(4 * 4 * 4);
  renderRows(pixels, 4, 4, 0, 4, plane, 50);
  assert.deepEqual(Array.from(pixels.slice(32, 36)), [10, 12, 20, 255]); // C = 0
  assert.deepEqual(Array.from(pixels.slice(40, 44)), [...escapeColor(sampleEscape(1, 0, 50)), 255]); // C = 1
  const chunks = new Uint8ClampedArray(pixels.length);
  renderRows(chunks, 4, 4, 0, 2, plane, 50);
  renderRows(chunks, 4, 4, 2, 4, plane, 50);
  assert.deepEqual(chunks, pixels);
  const other = new Uint8ClampedArray(pixels.length);
  renderRows(other, 4, 4, 0, 4, zoomPlane(plane, { re: 0.5, im: 0 }, 4), 150);
  assert.notDeepEqual(other, pixels);
});

test('interior shortcuts agree with direct iteration throughout the overview', () => {
  for (let re = -2; re <= 1; re += 0.2) {
    for (let im = -1; im <= 1; im += 0.2) {
      const expected = computeOrbit(re, im, 200);
      assert.equal(escapeIterations(re, im, 200), expected.escaped ? expected.points.length : 0);
    }
  }
});
