/** Non-overlapping horizontal regions; every row belongs to exactly one worker. */
export function splitRegions(height, threads) {
  if (!Number.isInteger(height) || height < 1 || height > 4096
      || !Number.isInteger(threads) || threads < 1 || threads > 32) {
    throw new RangeError('Размер изображения: 1–4096, число потоков: 1–32');
  }
  const count = Math.min(height, threads);
  return Array.from({ length: count }, (_, index) => ({
    fromRow: Math.floor(index * height / count),
    toRow: Math.floor((index + 1) * height / count)
  }));
}

/** One independent Wasm VM per region; transfers contain only that region's rows. */
export function startMandelbrotRender({ width, height, threads, plane, limit,
  onProgress = () => {}, onBand = () => {}, onComplete = () => {}, onError = () => {},
  createWorker = () => new Worker(new URL('./mandelbrot-worker.mjs', import.meta.url), { type: 'module' }) }) {
  if (!Number.isInteger(width) || width < 1 || width > 4096) throw new RangeError('Некорректная ширина');
  const regions = splitRegions(height, threads);
  const pixels = new Uint8ClampedArray(width * height * 4);
  const workers = [];
  let active = true, rows = 0, remaining = regions.length;
  function cancel() {
    active = false;
    for (const worker of workers) worker.terminate();
  }
  function fail(error) {
    if (!active) return;
    cancel();
    onError(error instanceof Error ? error : new Error(String(error)));
  }
  try {
    for (const region of regions) {
      const worker = createWorker();
      workers.push(worker);
      let nextRow = region.fromRow, finished = false;
      worker.onmessage = ({ data }) => {
        if (!active || finished) return;
        if (data.error) { fail(data.error); return; }
        if (data.fromRow !== nextRow || !Number.isInteger(data.toRow) || data.toRow <= nextRow
            || data.toRow > region.toRow || !(data.pixels instanceof ArrayBuffer)
            || data.pixels.byteLength !== (data.toRow - nextRow) * width * 4) {
          fail(new Error('Поток вернул некорректную область изображения'));
          return;
        }
        pixels.set(new Uint8ClampedArray(data.pixels), nextRow * width * 4);
        rows += data.toRow - nextRow;
        nextRow = data.toRow;
        onBand(pixels, data.fromRow, data.toRow);
        onProgress(Math.floor(rows / height * 100), regions.length);
        if (nextRow === region.toRow) {
          finished = true;
          worker.terminate();
          if (--remaining === 0) {
            active = false;
            onComplete(pixels);
          }
        }
      };
      worker.onerror = () => {
        if (!finished) fail(new Error('Ошибка потока расчёта Мандельброта. Нажмите «Сбросить вид».'));
      };
      worker.postMessage({ width, height, plane: { ...plane }, limit, ...region });
    }
  } catch (error) { fail(error); }
  return { cancel, regions };
}
