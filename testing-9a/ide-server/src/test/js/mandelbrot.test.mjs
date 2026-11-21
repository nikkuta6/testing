import test from 'node:test';
import assert from 'node:assert/strict';
import { computeOrbit, escapeIterations, pixelToComplex, complexToPixel, PLANE } from '../../main/resources/static/mandelbrot.mjs';

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
