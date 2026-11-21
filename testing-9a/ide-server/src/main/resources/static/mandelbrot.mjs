export const PLANE = Object.freeze({ left: -2.5, right: 1, bottom: -1.3125, top: 1.3125 });
export const MAX_ZOOM = 1e12;

export function pixelToComplex(x, y, width, height, plane = PLANE) {
  return {
    re: plane.left + x / width * (plane.right - plane.left),
    im: plane.top - y / height * (plane.top - plane.bottom)
  };
}

export function complexToPixel(re, im, width, height, plane = PLANE) {
  return {
    x: (re - plane.left) / (plane.right - plane.left) * width,
    y: (plane.top - im) / (plane.top - plane.bottom) * height
  };
}

export function zoomLevel(plane) {
  return (PLANE.right - PLANE.left) / (plane.right - plane.left);
}

export function zoomPlane(plane, anchor, factor) {
  if (!Number.isFinite(factor) || factor <= 0) throw new RangeError('Zoom factor must be positive');
  const zoom = Math.max(1, Math.min(MAX_ZOOM, zoomLevel(plane) * factor));
  if (zoom === 1) return { ...PLANE };
  const ratio = zoomLevel(plane) / zoom;
  return {
    left: anchor.re + (plane.left - anchor.re) * ratio,
    right: anchor.re + (plane.right - anchor.re) * ratio,
    bottom: anchor.im + (plane.bottom - anchor.im) * ratio,
    top: anchor.im + (plane.top - anchor.im) * ratio
  };
}

export function iterationLimit(plane) {
  return Math.min(6000, 240 + Math.ceil(Math.max(0, Math.log2(zoomLevel(plane)))) * 150);
}

/** Smooth escape time avoids abrupt colour bands; 0 marks no observed escape. */
export function sampleEscape(re, im, limit = 200) {
  // Analytically known interior: the main cardioid and period-two bulb.
  const q = (re - 0.25) ** 2 + im * im;
  if (q * (q + re - 0.25) < 0.25 * im * im || (re + 1) ** 2 + im * im < 0.0625) {
    return { iterations: 0, smooth: 0 };
  }
  let zr = 0, zi = 0;
  for (let step = 1; step <= limit; step++) {
    const nextRe = zr * zr - zi * zi + re;
    zi = 2 * zr * zi + im;
    zr = nextRe;
    const magnitudeSquared = zr * zr + zi * zi;
    if (magnitudeSquared > 4) {
      return { iterations: step, smooth: Number.isFinite(magnitudeSquared)
        ? step + 1 - Math.log2(Math.log(magnitudeSquared) / 2) : step };
    }
  }
  return { iterations: 0, smooth: 0 };
}

export function escapeColor(sample) {
  if (sample.iterations === 0) return [10, 12, 20];
  const phase = sample.smooth * 0.12;
  // A repeating smooth palette retains colour detail even after thousands of iterations.
  return [0, 2, 4].map(offset => Math.round(128 + 110 * Math.cos(phase + offset)));
}

export function renderRows(pixels, width, height, fromRow, toRow, plane, limit) {
  for (let y = fromRow; y < toRow; y++) {
    for (let x = 0; x < width; x++) {
      const c = pixelToComplex(x, y, width, height, plane);
      const color = escapeColor(sampleEscape(c.re, c.im, limit));
      const offset = (y * width + x) * 4;
      pixels[offset] = color[0];
      pixels[offset + 1] = color[1];
      pixels[offset + 2] = color[2];
      pixels[offset + 3] = 255;
    }
  }
}

/** z0 = 0; the returned points begin at z1 = C, including the first escaping iterate. */
export function computeOrbit(re, im, limit = 80) {
  if (!Number.isFinite(re) || !Number.isFinite(im)) throw new RangeError('C must be finite');
  if (!Number.isInteger(limit) || limit < 1 || limit > 1000) throw new RangeError('Steps must be 1–1000');
  const points = [];
  let zr = 0, zi = 0;
  for (let step = 1; step <= limit; step++) {
    const nextRe = zr * zr - zi * zi + re;
    zi = 2 * zr * zi + im;
    zr = nextRe;
    points.push({ re: zr, im: zi });
    if (zr * zr + zi * zi > 4) return { points, escaped: true };
  }
  return { points, escaped: false };
}

/** Escape step, or 0 when no escape was seen within the limit; this does not prove membership. */
export function escapeIterations(re, im, limit = 200) {
  return sampleEscape(re, im, limit).iterations;
}

export class MandelbrotView {
  constructor({ canvas, body, button, controls, steps, status, clearButton, onEnter, onExit }) {
    Object.assign(this, { canvas, body, button, controls, steps, status, clearButton, onEnter, onExit });
    this.active = false;
    this.selected = null;
    this.background = null;
    this.plane = { ...PLANE };
    this.renderVersion = 0;
    this.worker = null;
    button.disabled = false;
    button.addEventListener('click', event => {
      event.stopPropagation();
      if (this.active) { this.hide(); this.onExit(); }
      else this.show();
    });
    canvas.addEventListener('click', event => {
      if (this.suppressClick) { this.suppressClick = false; return; }
      if (!this.active || event.button !== 0 || !this.background) return;
      const box = canvas.getBoundingClientRect();
      this.selected = pixelToComplex((event.clientX - box.left) * canvas.width / box.width,
        (event.clientY - box.top) * canvas.height / box.height, canvas.width, canvas.height, this.plane);
      this.drawOrbit();
    });
    canvas.addEventListener('wheel', event => {
      if (!this.active) return;
      event.preventDefault();
      const box = canvas.getBoundingClientRect();
      const anchor = pixelToComplex(event.clientX - box.left, event.clientY - box.top, box.width, box.height, this.plane);
      const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? box.height : 1);
      this.zoom(Math.exp(-Math.max(-240, Math.min(240, delta)) * 0.0025), anchor);
    }, { passive: false });
    canvas.addEventListener('pointerdown', event => {
      if (!this.active || event.button !== 0) return;
      this.suppressClick = false;
      this.drag = { x: event.clientX, y: event.clientY, plane: { ...this.plane } };
      canvas.setPointerCapture(event.pointerId);
    });
    canvas.addEventListener('pointermove', event => {
      if (!this.active || !this.drag) return;
      const dx = event.clientX - this.drag.x, dy = event.clientY - this.drag.y;
      if (!this.suppressClick && dx * dx + dy * dy < 16) return;
      this.suppressClick = true;
      const box = canvas.getBoundingClientRect(), start = this.drag.plane;
      const re = dx / box.width * (start.right - start.left);
      const im = dy / box.height * (start.top - start.bottom);
      this.plane = { left: start.left - re, right: start.right - re, bottom: start.bottom + im, top: start.top + im };
      this.queueRender();
    });
    const finishDrag = event => {
      if (!this.drag) return;
      this.drag = null;
      if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
    };
    canvas.addEventListener('pointerup', finishDrag);
    canvas.addEventListener('pointercancel', finishDrag);
    document.getElementById('mandelbrot-zoom-in').addEventListener('click', () => this.zoom(2));
    document.getElementById('mandelbrot-zoom-out').addEventListener('click', () => this.zoom(0.5));
    document.getElementById('mandelbrot-reset-view').addEventListener('click', () => {
      this.plane = { ...PLANE };
      this.queueRender(0);
    });
    steps.addEventListener('input', () => {
      steps.setCustomValidity('');
      if (!steps.checkValidity()) return;
      if (this.active && this.background) this.drawOrbit();
    });
    steps.addEventListener('change', () => {
      if (!steps.checkValidity()) steps.reportValidity();
    });
    this.resizeObserver = new ResizeObserver(() => { if (this.active) this.fit(); });
    this.resizeObserver.observe(body);
  }

  fit() {
    // Preserve equal scales on both axes and map clicks through the displayed canvas rectangle.
    const width = Math.max(1, Math.min(640, this.body.clientWidth - 16, (this.body.clientHeight - 16) * 4 / 3));
    this.canvas.style.width = width + 'px';
    this.canvas.style.height = width * 3 / 4 + 'px';
  }

  show() {
    this.onEnter();
    this.active = true;
    this.selected = null;
    this.controls.hidden = false;
    this.button.classList.add('active');
    this.button.setAttribute('aria-pressed', 'true');
    this.clearButton.textContent = 'Убрать траекторию';
    this.clearButton.title = 'Убрать траекторию, сохранив множество Мандельброта';
    this.body.classList.add('mandelbrot-mode');
    this.canvas.setAttribute('aria-label', 'Множество Мандельброта: щёлкните, чтобы выбрать точку C');
    this.canvas.width = 640;
    this.canvas.height = 480;
    this.updateViewportInfo();
    this.fit();
    if (this.background) this.drawOrbit();
    else this.renderBackground();
  }

  hide() {
    this.active = false;
    this.renderVersion++;
    clearTimeout(this.renderTimer);
    this.worker?.terminate();
    this.worker = null;
    this.drag = null;
    this.controls.hidden = true;
    this.button.classList.remove('active');
    this.button.setAttribute('aria-pressed', 'false');
    this.clearButton.textContent = 'Очистить';
    this.clearButton.title = 'Очистить изображение';
    this.body.classList.remove('mandelbrot-mode');
    this.canvas.style.removeProperty('width');
    this.canvas.style.removeProperty('height');
    this.canvas.setAttribute('aria-label', 'Графический вывод программы');
  }

  resetOrbit() {
    this.selected = null;
    if (this.background) this.drawOrbit();
  }

  updateViewportInfo() {
    const zoom = zoomLevel(this.plane);
    const format = value => value.toFixed(Math.min(16, 4 + Math.ceil(Math.log10(Math.max(1, zoom)))));
    document.getElementById('graphics-size').textContent = `×${zoom < 1e5 ? zoom.toFixed(1) : zoom.toExponential(2)} · ${iterationLimit(this.plane)} ит.`;
    document.getElementById('mandelbrot-viewport').textContent =
      `Re: ${format(this.plane.left)}…${format(this.plane.right)} · Im: ${format(this.plane.bottom)}…${format(this.plane.top)}`;
  }

  zoom(factor, anchor) {
    if (!this.active) return;
    const p = this.plane;
    const center = { re: (p.left + p.right) / 2, im: (p.top + p.bottom) / 2 };
    const selectedVisible = this.selected && this.selected.re >= p.left && this.selected.re <= p.right
      && this.selected.im >= p.bottom && this.selected.im <= p.top;
    if ((factor > 1 && zoomLevel(p) >= MAX_ZOOM * 0.999) || (factor < 1 && zoomLevel(p) <= 1)) return;
    this.plane = zoomPlane(p, anchor || (selectedVisible ? this.selected : center), factor);
    this.queueRender();
  }

  queueRender(delay = 100) {
    this.worker?.terminate();
    this.worker = null;
    this.renderVersion++;
    this.background = null;
    clearTimeout(this.renderTimer);
    this.updateViewportInfo();
    const ctx = this.canvas.getContext('2d');
    ctx.fillStyle = '#0a0c14';
    ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    this.status.textContent = 'Пересчёт точек новой области…';
    this.renderTimer = setTimeout(() => { if (this.active) this.renderBackground(); }, delay);
  }

  renderBackground() {
    const version = ++this.renderVersion;
    const { width, height } = this.canvas;
    this.worker?.terminate();
    const worker = new Worker(new URL('./mandelbrot-worker.mjs', import.meta.url), { type: 'module' });
    this.worker = worker;
    this.status.textContent = 'Пересчёт точек новой области: 0%';
    worker.onmessage = event => {
      if (!this.active || version !== this.renderVersion) return;
      if (event.data.pixels) {
        this.background = new ImageData(new Uint8ClampedArray(event.data.pixels), width, height);
        worker.terminate();
        this.worker = null;
        this.drawOrbit();
      } else {
        this.status.textContent = `Пересчёт точек новой области: ${event.data.progress}%`;
      }
    };
    worker.onerror = () => {
      if (!this.active || version !== this.renderVersion) return;
      worker.terminate();
      this.worker = null;
      this.status.textContent = 'Не удалось рассчитать фрактал. Нажмите «Сбросить вид», чтобы повторить.';
    };
    worker.postMessage({ width, height, plane: { ...this.plane }, limit: iterationLimit(this.plane) });
  }

  drawOrbit() {
    const { canvas, background } = this;
    if (!this.active || !background) return;
    const ctx = canvas.getContext('2d');
    ctx.putImageData(background, 0, 0);
    this.drawAxes(ctx);
    if (!this.selected) {
      this.status.textContent = 'Щелчок — орбита C · Колесо — зум под курсором · Перетаскивание — сдвиг';
      return;
    }
    if (!this.steps.checkValidity()) return;
    const { re, im } = this.selected;
    const orbit = computeOrbit(re, im, Number(this.steps.value));
    const pixels = orbit.points.map(point => complexToPixel(point.re, point.im, canvas.width, canvas.height, this.plane));
    ctx.beginPath();
    ctx.moveTo(pixels[0].x, pixels[0].y);
    pixels.slice(1).forEach(point => ctx.lineTo(point.x, point.y));
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#10121c';
    ctx.lineWidth = 5;
    ctx.stroke();
    ctx.strokeStyle = '#ffcf40';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.fillStyle = '#ffcf40';
    pixels.slice(1).forEach(point => {
      ctx.beginPath(); ctx.arc(point.x, point.y, 2, 0, Math.PI * 2); ctx.fill();
    });
    const c = pixels[0];
    ctx.beginPath(); ctx.arc(c.x, c.y, 5, 0, Math.PI * 2);
    ctx.fillStyle = '#ffffff'; ctx.fill();
    ctx.strokeStyle = '#10121c'; ctx.lineWidth = 2; ctx.stroke();
    ctx.font = 'bold 15px sans-serif';
    ctx.lineWidth = 3; ctx.strokeText('C', c.x + 9, c.y - 9);
    ctx.fillText('C', c.x + 9, c.y - 9);
    const digits = Math.min(16, 6 + Math.ceil(Math.log10(Math.max(1, zoomLevel(this.plane)))));
    const number = value => value.toFixed(digits);
    this.status.textContent = `C = ${number(re)} ${im < 0 ? '−' : '+'} ${number(Math.abs(im))}i · `
      + (orbit.escaped ? `Выход за |z| = 2 на шаге ${orbit.points.length}.`
        : `За ${orbit.points.length} шагов выхода за |z| = 2 нет.`);
  }

  drawAxes(ctx) {
    const { width, height } = this.canvas;
    const origin = complexToPixel(0, 0, width, height, this.plane);
    ctx.strokeStyle = '#ffffff55'; ctx.lineWidth = 1;
    ctx.beginPath();
    if (origin.y >= 0 && origin.y <= height) { ctx.moveTo(0, origin.y); ctx.lineTo(width, origin.y); }
    if (origin.x >= 0 && origin.x <= width) { ctx.moveTo(origin.x, 0); ctx.lineTo(origin.x, height); }
    ctx.stroke();
    ctx.font = '12px sans-serif'; ctx.fillStyle = '#ffffff';
    ctx.fillText('Re →', width - 35, height - 8);
    ctx.fillText('Im ↑', 8, 17);
  }
}
