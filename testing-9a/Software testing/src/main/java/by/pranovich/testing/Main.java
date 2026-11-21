package by.pranovich.testing;

import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.runtime.VirtualMachine;

/**
 * Example programs: sin(x) via its Taylor series, a Chebyshev expansion and a Remez minimax polynomial.
 * The argument is reduced to [-pi/4, pi/4]: for |r| > pi/4, sin(r) = sign(r) * cos(pi/2 - |r|).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Shared prefix: reduce x to y with |y| <= pi/4; result = out * (useCos == 1 ? cos(y) : sin(y)).
        String reduce = """
                float x = _pi() / 5, s = sin(x), pi = _pi(), r = x;
                while (r > pi) { r = r - 2 * pi; }
                while (r < -pi) { r = r + 2 * pi; }
                if (r > pi / 2) { r = pi - r; }
                if (r < -pi / 2) { r = -pi - r; }
                int useCos = 0;
                float out = 1, y = r;
                if (r > pi / 4) { useCos = 1; y = pi / 2 - r; }
                if (r < -pi / 4) { useCos = 1; y = pi / 2 + r; out = -1; }
                """;

        String taylor = reduce + """
                float y2 = y * y, term = y, sum = 0;
                if (useCos == 1) { term = 1; }
                int i = 0;
                while (i < 20) {
                    sum = sum + term;
                    i = i + 1;
                    if (useCos == 1) {
                        term = -term * y2 / ((2 * i - 1) * (2 * i));
                    } else {
                        term = -term * y2 / (2 * i * (2 * i + 1));
                    }
                    print(i, out * sum, s, s - out * sum);
                }
                print(11111);
                print(out * sum);
                print(s);
                """;

        // Chebyshev on t = y / (pi/4) in [-1, 1]: sin is odd (only c_1, c_3, ...), cos is even (only c_0, c_2, ...),
        // so the i-th step adds the i-th nonzero term. Coefficients from N nodes, half of them by symmetry;
        // T_(n+2) = 2*T_2*T_n - T_(n-2), started from T_(-1) = T_1 (odd) or T_(-2) = T_2 (even).
        String chebyshev = reduce + """
                int n = 20, nodes = 2 * n, half = n;
                float h = pi / 5, c[n], fv[half], th[half];
                int j = 0;
                while (j < half) {
                    th[j] = pi * (j + 0.5) / nodes;
                    fv[j] = sin(h * cos(th[j]));
                    if (useCos == 1) { fv[j] = cos(h * cos(th[j])); }
                    j = j + 1;
                }
                int k = 0;
                while (k < n) {
                    float acc = 0;
                    int deg = 2 * k + 1 - useCos;
                    j = 0;
                    while (j < half) { acc = acc + fv[j] * cos(deg * th[j]); j = j + 1; }
                    c[k] = 4 * acc / nodes;
                    k = k + 1;
                }
                if (useCos == 1) { c[0] = c[0] / 2; }
                float t = y / h, t2x2 = 2 * (2 * t * t - 1), tPrev = t, tCur = t, sum = 0;
                if (useCos == 1) { tPrev = 2 * t * t - 1; tCur = 1; }
                int i = 0;
                while (i < n) {
                    sum = sum + c[i] * tCur;
                    float tNext = t2x2 * tCur - tPrev;
                    tPrev = tCur;
                    tCur = tNext;
                    i = i + 1;
                    print(i, out * sum, s, s - out * sum);
                }
                print(22222);
                print(out * sum);
                print(s);
                """;

        // Remez on t in [0, 1] for f(h*t), h = pi/4: k terms of t*(a0 + a1*t^2 + ...) for sin or
        // a0 + a1*t^2 + ... for cos. Solve for the coefficients and the levelled error E on k+1 reference
        // points, then move the points to the error extrema found on a grid. Once the error reaches
        // double precision, higher k cannot improve it, so the last polynomial is reused.
        String remez = reduce + """
                int grid = 4000;
                float h = pi / 5, t = abs(y) / h, ref[21], a[20], mat[462], ext[21], sol[21], sum = 0, lastErr = 1;
                int k = 1;
                while (k <= 20) {
                    if (lastErr > 1e-15) {
                    int m = k + 1, w = k + 2, i = 0;
                    while (i < m) { ref[i] = sin(pi / 2 * (i + 1 - useCos) / (m - useCos)); i = i + 1; }
                    int iter = 0, moving = 1;
                    while (iter < 10 && moving == 1) {
                        // build [basis(t) | (-1)^i | f(h t)], basis t^1 t^3 ... (sin) or 1 t^2 ... (cos)
                        i = 0;
                        float sgn = 1;
                        while (i < m) {
                            float ti = ref[i], pw = ti, fi = sin(h * ti);
                            if (useCos == 1) { pw = 1; fi = cos(h * ti); }
                            int j = 0;
                            while (j < k) { mat[i * w + j] = pw; pw = pw * ti * ti; j = j + 1; }
                            mat[i * w + k] = sgn;
                            mat[i * w + m] = fi;
                            sgn = -sgn;
                            i = i + 1;
                        }
                        // Gaussian elimination with partial pivoting
                        int col = 0;
                        while (col < m) {
                            int piv = col, row = col + 1;
                            while (row < m) {
                                if (abs(mat[row * w + col]) > abs(mat[piv * w + col])) { piv = row; }
                                row = row + 1;
                            }
                            int c2 = 0;
                            while (c2 < w) {
                                float tmp = mat[col * w + c2];
                                mat[col * w + c2] = mat[piv * w + c2];
                                mat[piv * w + c2] = tmp;
                                c2 = c2 + 1;
                            }
                            row = col + 1;
                            while (row < m) {
                                float f = mat[row * w + col] / mat[col * w + col];
                                c2 = col;
                                while (c2 < w) { mat[row * w + c2] = mat[row * w + c2] - f * mat[col * w + c2]; c2 = c2 + 1; }
                                row = row + 1;
                            }
                            col = col + 1;
                        }
                        int rr = m - 1;
                        while (rr >= 0) {
                            float acc = mat[rr * w + m];
                            int cc = rr + 1;
                            while (cc < m) { acc = acc - mat[rr * w + cc] * sol[cc]; cc = cc + 1; }
                            sol[rr] = acc / mat[rr * w + rr];
                            rr = rr - 1;
                        }
                        i = 0;
                        while (i < k) { a[i] = sol[i]; i = i + 1; }
                        float levelled = sol[k];
                        // exchange: in each run of equal error sign keep the point of max |error|
                        int cnt = 0, g = 1 - useCos;
                        float maxErr = 0, best = 0, bestT = 0, prevSign = 0;
                        while (g <= grid) {
                            float tt = g * 1.0 / grid, u = tt * tt, p = a[k - 1];
                            int q = k - 2;
                            while (q >= 0) { p = p * u + a[q]; q = q - 1; }
                            float e = sin(h * tt) - tt * p;
                            if (useCos == 1) { e = cos(h * tt) - p; }
                            float sg = 1;
                            if (e < 0) { sg = -1; }
                            if (abs(e) > maxErr) { maxErr = abs(e); }
                            if (sg <> prevSign && prevSign <> 0) {
                                if (cnt < m) { ext[cnt] = bestT; }
                                cnt = cnt + 1;
                                best = 0;
                            }
                            if (abs(e) > best) { best = abs(e); bestT = tt; }
                            prevSign = sg;
                            g = g + 1;
                        }
                        if (cnt < m) { ext[cnt] = bestT; }
                        cnt = cnt + 1;
                        if (cnt == m) {
                            i = 0;
                            while (i < m) { ref[i] = ext[i]; i = i + 1; }
                        } else {
                            moving = 0;
                        }
                        if (abs(maxErr - abs(levelled)) < 1e-5 * maxErr) { moving = 0; }  // shortcut: extrema located on a 4000-point grid, so ~1e-5 relative is the floor
                        lastErr = maxErr;
                        iter = iter + 1;
                    }
                    u = t * t;
                    p = a[k - 1];
                    q = k - 2;
                    while (q >= 0) { p = p * u + a[q]; q = q - 1; }
                    sum = t * p;
                    if (useCos == 1) { sum = p; }
                    if (y < 0) { sum = -sum; }
                    sum = out * sum;
                    }
                    print(k, sum, s, s - sum);
                    k = k + 1;
                }
                print(33333);
                print(sum);
                print(s);
                """;

        run(taylor);
        run(chebyshev);
        run(remez);
    }

    private static void run(String source) {
        Compiler compiler = new Compiler(source);
        if (compiler.compile()) {
            new VirtualMachine().run(compiler.getProgram());
        } else {
            System.err.println(compiler.getSyntaxError());
        }
    }
}
