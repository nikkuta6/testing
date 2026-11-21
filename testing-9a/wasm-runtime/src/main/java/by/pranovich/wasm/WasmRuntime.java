package by.pranovich.wasm;

import org.teavm.jso.JSExport;

public final class WasmRuntime {
    private WasmRuntime() { }

    @JSExport
    public static String version() {
        return "WebAssembly GC";
    }

    @JSExport
    public static String run(String source, boolean debug) {
        return RunService.run(source, debug);
    }

    @JSExport
    public static void installMandelbrotProgram(String source) {
        Mandelbrot.install(source);
    }

    @JSExport
    public static String orbit(double re, double im, int limit) {
        return Mandelbrot.orbit(re, im, limit);
    }

    @JSExport
    public static String sampleEscape(double re, double im, int limit) {
        return Mandelbrot.sampleJson(re, im, limit);
    }

    @JSExport
    public static int escapeColor(double smooth, boolean escaped) {
        return Mandelbrot.color(smooth, escaped);
    }

    @JSExport
    public static String renderBand(double left, double right, double bottom, double top,
                                    int width, int height, int fromRow, int toRow, int limit) {
        return Mandelbrot.renderBand(left, right, bottom, top, width, height, fromRow, toRow, limit);
    }
}
