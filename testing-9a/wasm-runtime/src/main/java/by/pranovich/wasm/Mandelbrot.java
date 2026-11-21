package by.pranovich.wasm;

import java.util.Base64;

/** Fractal, smooth escape colour and selected orbit; all numerical work is compiled to Wasm. */
public final class Mandelbrot {
    private Mandelbrot() { }

    // Results are confined to the worker running this Wasm instance; no shared mutable memory.
    private static int escapedAt;
    private static double smooth;

    private static void sample(double re, double im, int limit) {
        escapedAt = 0;
        smooth = 0;
        double q = (re - 0.25) * (re - 0.25) + im * im;
        if (q * (q + re - 0.25) < 0.25 * im * im || (re + 1) * (re + 1) + im * im < 0.0625) return;
        double zr = 0, zi = 0;
        for (int step = 1; step <= limit; step++) {
            double nextRe = zr * zr - zi * zi + re;
            zi = 2 * zr * zi + im;
            zr = nextRe;
            double magnitude = zr * zr + zi * zi;
            if (magnitude > 4) {
                escapedAt = step;
                smooth = Double.isFinite(magnitude) ? step + 1 - Math.log(Math.log(magnitude) / 2) / Math.log(2) : step;
                return;
            }
        }
    }

    public static String sampleJson(double re, double im, int limit) {
        validatePoint(re, im);
        validateLimit(limit, 6000);
        sample(re, im, limit);
        return "{\"iterations\":" + escapedAt + ",\"smooth\":" + smooth + "}";
    }

    public static int color(double smooth, boolean escaped) {
        if (!escaped) return 0x0a0c14;
        double phase = smooth * 0.12;
        return channel(phase) << 16 | channel(phase + 2) << 8 | channel(phase + 4);
    }

    private static int channel(double phase) {
        return (int) Math.floor(128 + 110 * Math.cos(phase) + 0.5);
    }

    public static String orbit(double re, double im, int limit) {
        validatePoint(re, im);
        validateLimit(limit, 1000);
        StringBuilder result = new StringBuilder("{\"points\":[");
        double zr = 0, zi = 0;
        boolean escaped = false;
        for (int step = 1; step <= limit; step++) {
            double nextRe = zr * zr - zi * zi + re;
            zi = 2 * zr * zi + im;
            zr = nextRe;
            if (step > 1) result.append(',');
            // The interactive plane stays finite; reject non-finite results at the boundary as well.
            if (!Double.isFinite(zr) || !Double.isFinite(zi)) throw new IllegalArgumentException("Слишком большая координата C");
            result.append("{\"re\":").append(zr).append(",\"im\":").append(zi).append('}');
            if (zr * zr + zi * zi > 4) { escaped = true; break; }
        }
        return result.append("],\"escaped\":").append(escaped).append('}').toString();
    }

    public static String renderBand(double left, double right, double bottom, double top,
                                    int width, int height, int fromRow, int toRow, int limit) {
        if (width < 1 || width > 4096 || height < 1 || height > 4096
                || fromRow < 0 || toRow < fromRow || toRow > height || toRow - fromRow > 64) {
            throw new IllegalArgumentException("Некорректный размер полосы фрактала");
        }
        validatePoint(left, bottom);
        validatePoint(right, top);
        if (!(right > left) || !(top > bottom)) throw new IllegalArgumentException("Некорректная область фрактала");
        validateLimit(limit, 6000);
        byte[] pixels = new byte[width * (toRow - fromRow) * 4];
        int offset = 0;
        for (int y = fromRow; y < toRow; y++) {
            double im = top - (double) y / height * (top - bottom);
            for (int x = 0; x < width; x++) {
                double re = left + (double) x / width * (right - left);
                sample(re, im, limit);
                int rgb = color(smooth, escapedAt != 0);
                pixels[offset++] = (byte) (rgb >>> 16);
                pixels[offset++] = (byte) (rgb >>> 8);
                pixels[offset++] = (byte) rgb;
                pixels[offset++] = (byte) 255;
            }
        }
        return Base64.getEncoder().encodeToString(pixels);
    }

    private static void validatePoint(double re, double im) {
        if (!Double.isFinite(re) || !Double.isFinite(im)) throw new IllegalArgumentException("C должна быть конечной");
    }

    private static void validateLimit(int limit, int maximum) {
        if (limit < 1 || limit > maximum) throw new IllegalArgumentException("Некорректное число итераций");
    }
}
