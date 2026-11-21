import { load } from './wasm/app.wasm-runtime.mjs';

let runtimePromise;
export function loadRuntime() {
  if (!runtimePromise) {
    const location = new URL('./wasm/app.wasm', import.meta.url);
    runtimePromise = (async () => {
      // Node test runs and the browser load exactly the same Wasm binary and generated loader.
      const bytes = location.protocol === 'file:'
        ? await (await import('node:fs/promises')).readFile(location)
        : await fetch(location).then(response => {
          if (!response.ok) throw new Error('Не удалось загрузить WebAssembly: HTTP ' + response.status);
          return response.arrayBuffer();
        });
      const runtime = await load(bytes);
      return runtime;
    })().catch(error => {
      runtimePromise = undefined;
      throw new Error('Не удалось запустить WebAssembly GC. Нужен современный браузер. ' + error.message, { cause: error });
    });
  }
  return runtimePromise;
}

export function decodeBand(encoded) {
  const text = atob(encoded);
  const pixels = new Uint8ClampedArray(text.length);
  for (let i = 0; i < text.length; i++) pixels[i] = text.charCodeAt(i);
  return pixels;
}
