/** Owns the isolated Wasm execution worker; terminating it actually stops runaway code. */
export class WasmRunner {
  constructor(onState = () => {}) {
    this.onState = onState;
    this.worker = null;
    this.pending = null;
    this.sequence = 0;
  }

  ready() {
    if (this.readyPromise) return this.readyPromise;
    this.onState('loading');
    const worker = new Worker(new URL('./runtime-worker.mjs', import.meta.url), { type: 'module' });
    this.worker = worker;
    this.readyPromise = new Promise((resolve, reject) => {
      this.rejectReady = reject;
      this.initTimer = setTimeout(() => this.fail(new Error('Не удалось загрузить WebAssembly за 30 с')), 30000);
      worker.onmessage = ({ data }) => {
        if (this.worker !== worker) return;
        if (data.type === 'ready') {
          clearTimeout(this.initTimer);
          this.rejectReady = null;
          this.onState('ready');
          resolve();
        } else if (data.type === 'error') {
          this.fail(new Error(data.message));
        } else if (data.type === 'result' && this.pending?.id === data.id) {
          clearTimeout(this.runTimer);
          const pending = this.pending;
          this.pending = null;
          pending.resolve(data.result);
        }
      };
      worker.onerror = event => { if (this.worker === worker) this.fail(new Error(event.message || 'Ошибка WebAssembly Worker')); };
      worker.postMessage({ type: 'init' });
    });
    return this.readyPromise;
  }

  async run(source, debug = false) {
    if (new TextEncoder().encode(source).length > (1 << 20)) throw new Error('Исходный код должен быть не больше 1 МБ');
    if (this.pending) this.cancel();
    const id = ++this.sequence;
    await this.ready();
    if (id !== this.sequence) throw new DOMException('Выполнение заменено новым запуском', 'AbortError');
    return new Promise((resolve, reject) => {
      this.pending = { id, resolve, reject };
      this.runTimer = setTimeout(() => this.fail(new Error('Превышено время выполнения (5 с); выполнение остановлено')), 5000);
      this.worker.postMessage({ type: 'run', id, source, debug });
    });
  }

  fail(error) {
    this.sequence++;
    clearTimeout(this.initTimer);
    clearTimeout(this.runTimer);
    this.worker?.terminate();
    this.worker = null;
    this.readyPromise = null;
    this.rejectReady?.(error);
    this.rejectReady = null;
    this.pending?.reject(error);
    this.pending = null;
    this.onState('stopped');
  }

  cancel() { this.fail(new DOMException('Выполнение остановлено', 'AbortError')); }
}
