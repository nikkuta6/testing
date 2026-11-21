import { loadRuntime } from './wasm-loader.mjs';

const ready = loadRuntime();
self.onmessage = async ({ data }) => {
  try {
    const runtime = await ready;
    if (data.type === 'init') {
      self.postMessage({ type: 'ready', engine: runtime.exports.version() });
    } else if (data.type === 'run') {
      self.postMessage({ type: 'result', id: data.id, result: JSON.parse(runtime.exports.run(data.source, data.debug)) });
    }
  } catch (error) {
    self.postMessage({ type: 'error', id: data.id, message: error.message || String(error) });
  }
};
