import { renderRows } from './mandelbrot.mjs';

self.onmessage = ({ data: { width, height, plane, limit } }) => {
  const pixels = new Uint8ClampedArray(width * height * 4);
  for (let row = 0; row < height; row += 16) {
    const end = Math.min(row + 16, height);
    renderRows(pixels, width, height, row, end, plane, limit);
    self.postMessage({ progress: Math.round(end / height * 100) });
  }
  self.postMessage({ pixels: pixels.buffer }, [pixels.buffer]);
};
