import { loadRuntime, decodeBand } from './wasm-loader.mjs';

const ready = loadRuntime();
self.onmessage = async ({ data: { width, height, plane, limit } }) => {
  try {
    const { exports: wasm } = await ready;
    const pixels = new Uint8ClampedArray(width * height * 4);
    for (let row = 0; row < height; row += 16) {
      const end = Math.min(row + 16, height);
      const band = wasm.renderBand(plane.left, plane.right, plane.bottom, plane.top, width, height, row, end, limit);
      pixels.set(decodeBand(band), row * width * 4);
      self.postMessage({ progress: Math.round(end / height * 100) });
    }
    self.postMessage({ pixels: pixels.buffer }, [pixels.buffer]);
  } catch (error) {
    self.postMessage({ error: error.message || String(error) });
  }
};
