package by.pranovich.wasm;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.compiler.SyntaxError;
import by.pranovich.testing.runtime.DrawingCommand;
import by.pranovich.testing.runtime.VirtualMachine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The complete compile/run/debug protocol, shared by JVM regression tests and the Wasm export. */
public final class RunService {
    public static final int MAX_SOURCE_BYTES = 1 << 20;
    public static final int MAX_OUTPUT_BYTES = 1 << 20;
    public static final int MAX_DEBUG_STEPS = 5000;
    private static final int MAX_TRACE_CHARS = 8 << 20;

    private RunService() { }

    public static String run(String source, boolean debug) {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Исходный код должен быть не больше 1 МБ");
        }
        Compiler compiler = new Compiler(source);
        if (!compiler.compile()) {
            SyntaxError error = compiler.getSyntaxError();
            return "{\"syntaxError\":{\"message\":" + quote(error.getMessage())
                    + ",\"line\":" + error.getLineNumber() + ",\"position\":" + error.getPosition() + "}}";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VirtualMachine vm = new VirtualMachine(new PrintStream(new LimitedStream(output), true, StandardCharsets.UTF_8));
        List<String> steps = new ArrayList<>();
        int[] traceSize = { 0 };
        long started = System.currentTimeMillis();
        int[] instructions = { 0 };
        String runtimeError = null;
        try {
            vm.run(compiler.getProgram(), pc -> {
                if ((instructions[0]++ & 1023) == 0 && System.currentTimeMillis() - started > 3000) {
                    throw new IllegalStateException("Превышено время выполнения (3 с)");
                }
                if (debug) {
                    if (steps.size() >= MAX_DEBUG_STEPS) {
                        throw new IllegalStateException("Пошаговый режим: показаны первые " + MAX_DEBUG_STEPS + " шагов");
                    }
                    String snapshot = "{\"pc\":" + pc + ",\"out\":" + output.size()
                            + ",\"graphics\":" + vm.drawingCommandCount() + ",\"variables\":" + memoryJson(vm) + "}";
                    traceSize[0] += snapshot.length();
                    if (traceSize[0] > MAX_TRACE_CHARS) {
                        throw new IllegalStateException("Слишком большой объём снимков отладчика (больше 8 МБ)");
                    }
                    steps.add(snapshot);
                }
            });
        } catch (RuntimeException error) {
            runtimeError = error.getMessage() == null ? error.toString() : error.getMessage();
        }
        return "{\"bytecode\":" + quote(compiler.getProgram().disassemble())
                + ",\"output\":" + quote(output.toString(StandardCharsets.UTF_8))
                + ",\"runtimeError\":" + (runtimeError == null ? "null" : quote(runtimeError))
                + ",\"graphics\":" + graphicsJson(vm.drawingCommands())
                + ",\"variables\":" + memoryJson(vm)
                + (debug ? ",\"lines\":" + linesJson(source, compiler.getProgram())
                        + ",\"steps\":[" + String.join(",", steps) + "]" : "") + "}";
    }

    private static String graphicsJson(List<DrawingCommand> commands) {
        Joiner json = new Joiner("[", "]");
        for (DrawingCommand command : commands) {
            json.add("{\"name\":" + quote(command.name()) + ",\"arguments\":" + command.arguments() + "}");
        }
        return json.toString();
    }

    private static String linesJson(String source, Program program) {
        // Prefix counts avoid rescanning the full source for every instruction.
        int[] lines = new int[source.length() + 1];
        lines[0] = 1;
        for (int i = 0; i < source.length(); i++) lines[i + 1] = lines[i] + (source.charAt(i) == '\n' ? 1 : 0);
        Joiner json = new Joiner("[", "]");
        for (int i = 0; i < program.instructions().size(); i++) {
            int position = program.sourcePosition(i);
            json.add(String.valueOf(position < 0 ? 0 : lines[position]));
        }
        return json.toString();
    }

    private static String memoryJson(VirtualMachine vm) {
        Joiner stack = new Joiner("[", "]");
        for (Object value : vm.stackValues()) stack.add(quote(VirtualMachine.formatValue(value)));
        return scopeJson(vm.globalVariables(), vm.globalArrays())
                + ",\"locals\":" + scopeJson(vm.localVariables(), vm.localArrays()) + ",\"stack\":" + stack;
    }

    private static String scopeJson(Map<String, Object> variables, Map<String, Object[]> arrays) {
        Joiner json = new Joiner("{", "}");
        new TreeMap<>(variables).forEach((name, value) -> json.add(quote(name) + ":" + quote(VirtualMachine.formatValue(value))));
        new TreeMap<>(arrays).forEach((name, value) -> json.add(quote(name) + ":" + quote(VirtualMachine.formatValue(value))));
        return json.toString();
    }

    public static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        result.append("\\u");
                        String hex = Integer.toHexString(ch);
                        result.append("0".repeat(4 - hex.length())).append(hex);
                    } else result.append(ch);
                }
            }
        }
        return result.append('"').toString();
    }

    private static final class Joiner {
        private final StringBuilder content;
        private final String suffix;
        private boolean first = true;
        Joiner(String prefix, String suffix) { content = new StringBuilder(prefix); this.suffix = suffix; }
        void add(String value) {
            if (!first) content.append(',');
            content.append(value);
            first = false;
        }
        @Override public String toString() { return content.toString() + suffix; }
    }

    private static final class LimitedStream extends OutputStream {
        private final ByteArrayOutputStream target;
        LimitedStream(ByteArrayOutputStream target) { this.target = target; }
        @Override public void write(int b) {
            if (target.size() >= MAX_OUTPUT_BYTES) {
                throw new IllegalStateException("Слишком большой вывод (больше " + MAX_OUTPUT_BYTES + " байт)");
            }
            target.write(b);
        }
    }
}
