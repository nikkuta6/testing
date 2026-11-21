export const PLANE = Object.freeze({ left: -2.5, right: 1, bottom: -1.3125, top: 1.3125 });

export function pixelToComplex(x, y, width, height) {
  return {
    re: PLANE.left + x / width * (PLANE.right - PLANE.left),
    im: PLANE.top - y / height * (PLANE.top - PLANE.bottom)
  };
}

export function complexToPixel(re, im, width, height) {
  return {
    x: (re - PLANE.left) / (PLANE.right - PLANE.left) * width,
    y: (PLANE.top - im) / (PLANE.top - PLANE.bottom) * height
  };
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
  let zr = 0, zi = 0;
  for (let step = 1; step <= limit; step++) {
    const nextRe = zr * zr - zi * zi + re;
    zi = 2 * zr * zi + im;
    zr = nextRe;
    if (zr * zr + zi * zi > 4) return step;
  }
  return 0;
}

export class MandelbrotView {
  constructor({ canvas, body, button, controls, steps, status, clearButton, onEnter, onExit }) {
    Object.assign(this, { canvas, body, button, controls, steps, status, clearButton, onEnter, onExit });
    this.active = false;
    this.selected = null;
    this.background = null;
    this.renderVersion = 0;
    button.disabled = false;
    button.addEventListener('click', event => {
      event.stopPropagation();
      if (this.active) { this.hide(); this.onExit(); }
      else this.show();
    });
    canvas.addEventListener('click', event => {
      if (!this.active || event.button !== 0 || !this.background) return;
      const box = canvas.getBoundingClientRect();
      this.selected = pixelToComplex((event.clientX - box.left) * canvas.width / box.width,
        (event.clientY - box.top) * canvas.height / box.height, canvas.width, canvas.height);
      this.drawOrbit();
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
    document.getElementById('graphics-size').textContent = 'Re: −2.5…1 · Im: ±1.3125';
    this.fit();
    if (this.background) this.drawOrbit();
    else this.renderBackground();
  }

  hide() {
    this.active = false;
    this.renderVersion++;
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

  renderBackground() {
    const version = ++this.renderVersion;
    const width = 640, height = 480;
    const ctx = this.canvas.getContext('2d');
    const image = ctx.createImageData(width, height);
    let row = 0;
    this.status.textContent = 'Построение множества Мандельброта…';
    ctx.fillStyle = '#10121c';
    ctx.fillRect(0, 0, width, height);
    const renderRows = () => {
      if (!this.active || version !== this.renderVersion) return;
      const end = Math.min(row + 12, height);
      for (; row < end; row++) {
        for (let x = 0; x < width; x++) {
          const c = pixelToComplex(x, row, width, height);
          const count = escapeIterations(c.re, c.im);
          const offset = (row * width + x) * 4;
          if (count === 0) {
            image.data[offset] = 16;
            image.data[offset + 1] = 18;
            image.data[offset + 2] = 28;
          } else {
            const t = Math.min(0.95, count / 45);
            image.data[offset] = Math.round(9 * (1 - t) * t ** 3 * 255);
            image.data[offset + 1] = Math.round(15 * (1 - t) ** 2 * t ** 2 * 255);
            image.data[offset + 2] = Math.round(8.5 * (1 - t) ** 3 * t * 255);
          }
          image.data[offset + 3] = 255;
        }
      }
      if (row < height) requestAnimationFrame(renderRows);
      else {
        this.background = image;
        this.drawOrbit();
      }
    };
    requestAnimationFrame(renderRows);
  }

  drawOrbit() {
    const { canvas, background } = this;
    if (!this.active || !background) return;
    const ctx = canvas.getContext('2d');
    ctx.putImageData(background, 0, 0);
    this.drawAxes(ctx);
    if (!this.selected) {
      this.status.textContent = 'Щёлкните по множеству: отрезки соединят C → C² + C → …';
      return;
    }
    if (!this.steps.checkValidity()) return;
    const { re, im } = this.selected;
    const orbit = computeOrbit(re, im, Number(this.steps.value));
    const pixels = orbit.points.map(point => complexToPixel(point.re, point.im, canvas.width, canvas.height));
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
    const number = value => (Math.abs(value) < 0.0000005 ? 0 : value).toFixed(6);
    this.status.textContent = `C = ${number(re)} ${im < 0 ? '−' : '+'} ${number(Math.abs(im))}i · `
      + (orbit.escaped ? `Выход за |z| = 2 на шаге ${orbit.points.length}.`
        : `За ${orbit.points.length} шагов выхода за |z| = 2 нет.`);
  }

  drawAxes(ctx) {
    const { width, height } = this.canvas;
    const origin = complexToPixel(0, 0, width, height);
    ctx.strokeStyle = '#ffffff55'; ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, origin.y); ctx.lineTo(width, origin.y);
    ctx.moveTo(origin.x, 0); ctx.lineTo(origin.x, height); ctx.stroke();
    ctx.font = '12px sans-serif'; ctx.fillStyle = '#ffffff';
    for (const re of [-2, -1, 0]) {
      const p = complexToPixel(re, 0, width, height);
      ctx.fillText(String(re), p.x + 4, p.y + 15);
    }
    ctx.fillText('Re', width - 23, origin.y - 7);
    ctx.fillText('Im', origin.x + 7, 15);
    ctx.fillText('+1i', origin.x + 7, complexToPixel(0, 1, width, height).y);
    ctx.fillText('−1i', origin.x + 7, complexToPixel(0, -1, width, height).y);
  }
}
