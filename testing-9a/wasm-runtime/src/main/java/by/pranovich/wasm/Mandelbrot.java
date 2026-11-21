package by.pranovich.wasm;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.runtime.DrawingCommand;
import by.pranovich.testing.runtime.VirtualMachine;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Host adapter only: formulas, iteration, palette and pixel coordinates live in mandelbrot.lang. */
public final class Mandelbrot {
    private static final String PARAMETERS_END = "// @mandelbrot-parameters-end";
    private static Program program;
    private static Map<String, Object> defaults;
    private static int entryPoint;
    private static final PrintStream SILENT = new PrintStream(OutputStream.nullOutputStream());

    private Mandelbrot() { }

    /** Each worker compiles its own copy once; later bands reuse the bytecode. */
    public static void install(String source) {
        if (source == null || source.length() > RunService.MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Некорректная программа Мандельброта");
        }
        int boundary = source.indexOf(PARAMETERS_END);
        if (boundary < 0) throw new IllegalArgumentException("Нет границы параметров Мандельброта");
        Program header = compile(source.substring(0, boundary));
        Program compiled = compile(source);
        VirtualMachine vm = new VirtualMachine(SILENT);
        vm.run(header);
        defaults = vm.globalVariables();
        entryPoint = header.instructions().size() - 1; // Skip defaults; preserve absolute jumps.
        program = compiled;
    }

    private static Program compile(String source) {
        Compiler compiler = new Compiler(source);
        if (!compiler.compile()) {
            throw new IllegalArgumentException("Мандельброт: " + compiler.getSyntaxError().getMessage()
                    + " (строка " + compiler.getSyntaxError().getLineNumber() + ")");
        }
        return compiler.getProgram();
    }

    private static VirtualMachine execute(int mode, Map<String, Object> parameters, Consumer<DrawingCommand> sink) {
        if (program == null) throw new IllegalStateException("Программа Мандельброта не загружена");
        Map<String, Object> globals = new HashMap<>(defaults);
        globals.putAll(parameters);
        globals.put("mode", (double) mode);
        VirtualMachine vm = new VirtualMachine(SILENT, sink);
        long started = System.currentTimeMillis();
        int[] instructions = { 0 };
        vm.run(program, pc -> {
            if ((instructions[0]++ & 4095) == 0 && System.currentTimeMillis() - started > 30_000) {
                throw new IllegalStateException("Превышено время расчёта полосы (30 с)");
            }
        }, globals, entryPoint);
        return vm;
    }

    public static String sampleJson(double re, double im, int limit) {
        validatePoint(re, im);
        validateLimit(limit, 6000);
        Map<String, Object> values = execute(1, Map.of("selectedRe", re, "selectedIm", im,
                "iterationLimit", (double) limit), null).globalVariables();
        return "{\"iterations\":" + ((Double) values.get("resultStep")).intValue()
                + ",\"smooth\":" + values.get("resultSmooth") + "}";
    }

    public static int color(double smooth, boolean escaped) {
        validatePoint(smooth, 0);
        Map<String, Object> values = execute(3, Map.of("colorSmooth", smooth,
                "colorEscaped", escaped ? 1.0 : 0.0), null).globalVariables();
        return ((Double) values.get("resultR")).intValue() << 16
                | ((Double) values.get("resultG")).intValue() << 8
                | ((Double) values.get("resultB")).intValue();
    }

    public static String orbit(double re, double im, int limit) {
        validatePoint(re, im);
        validateLimit(limit, 1000);
        VirtualMachine vm = execute(2, Map.of("selectedRe", re, "selectedIm", im,
                "orbitLimit", (double) limit), null);
        Map<String, Object> values = vm.globalVariables();
        Object[] real = vm.globalArrays().get("orbitRe"), imaginary = vm.globalArrays().get("orbitIm");
        int count = ((Double) values.get("orbitCount")).intValue();
        StringBuilder result = new StringBuilder("{\"points\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) result.append(',');
            result.append("{\"re\":").append(real[i]).append(",\"im\":").append(imaginary[i]).append('}');
        }
        return result.append("],\"escaped\":").append((Double) values.get("orbitEscaped") != 0).append('}').toString();
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
        int[] count = { 0 };
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("viewLeft", left); parameters.put("viewRight", right);
        parameters.put("viewBottom", bottom); parameters.put("viewTop", top);
        parameters.put("imageWidth", (double) width); parameters.put("imageHeight", (double) height);
        parameters.put("fromRow", (double) fromRow); parameters.put("toRow", (double) toRow);
        parameters.put("iterationLimit", (double) limit);
        execute(0, parameters, command -> {
            if (command.name().equals("canvas")) return;
            if (!command.name().equals("pixel")) throw new IllegalStateException("Ожидалась команда pixel");
            var a = command.arguments();
            int x = a.get(0).intValue(), y = a.get(1).intValue();
            if (x >= width || y < fromRow || y >= toRow) throw new IllegalStateException("Пиксель вне области потока");
            int offset = ((y - fromRow) * width + x) * 4;
            if (pixels[offset + 3] != 0) throw new IllegalStateException("Повторный пиксель");
            pixels[offset] = a.get(2).byteValue();
            pixels[offset + 1] = a.get(3).byteValue();
            pixels[offset + 2] = a.get(4).byteValue();
            pixels[offset + 3] = (byte) 255;
            count[0]++;
        });
        if (count[0] != width * (toRow - fromRow)) throw new IllegalStateException("Полоса рассчитана не полностью");
        return Base64.getEncoder().encodeToString(pixels);
    }

    private static void validatePoint(double re, double im) {
        if (!Double.isFinite(re) || !Double.isFinite(im)) throw new IllegalArgumentException("C должна быть конечной");
    }

    private static void validateLimit(int limit, int maximum) {
        if (limit < 1 || limit > maximum) throw new IllegalArgumentException("Некорректное число итераций");
    }
}
