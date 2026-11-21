import { loadMandelbrotRuntime, decodeBand } from './wasm-loader.mjs';

const ready = loadMandelbrotRuntime();
self.onmessage = async ({ data: { width, height, plane, limit, fromRow, toRow } }) => {
  try {
    const { exports: wasm } = await ready;
    // The region stays fixed for this worker. Small bands provide progress even at deep zoom.
    const bandRows = limit > 1000 ? 1 : 2;
    for (let row = fromRow; row < toRow; row += bandRows) {
      const end = Math.min(row + bandRows, toRow);
      const band = wasm.renderBand(plane.left, plane.right, plane.bottom, plane.top, width, height, row, end, limit);
      const pixels = decodeBand(band);
      self.postMessage({ fromRow: row, toRow: end, pixels: pixels.buffer }, [pixels.buffer]);
    }
  } catch (error) {
    self.postMessage({ error: error.message || String(error) });
  }
};
