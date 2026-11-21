package by.pranovich.ide;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.compiler.SyntaxError;
import by.pranovich.testing.runtime.VirtualMachine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HTTP API for the web IDE: serves the page and runs programs.
 * POST /api/run with the source as the body returns byte-code, output and VM memory as JSON.
 * POST /api/debug additionally returns the source line of every instruction and a VM snapshot before every step.
 */
public final class IdeServer {

    private static final int MAX_SOURCE_BYTES = 1 << 20;
    private static final int MAX_OUTPUT_BYTES = 1 << 20;
    private static final long RUN_TIMEOUT_SECONDS = 3;
    private static final int MAX_DEBUG_STEPS = 5000;

    // shortcut: a timed-out program keeps spinning on its daemon thread (Thread.stop is gone); move runs to a child process if that becomes a problem.
    private static final ExecutorService RUNNER = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "vm-run");
        thread.setDaemon(true);
        return thread;
    });

    private IdeServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        // Only localhost: the API executes arbitrary programs.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/run", exchange -> handleRun(exchange, false));
        server.createContext("/api/debug", exchange -> handleRun(exchange, true));
        server.createContext("/", IdeServer::handlePage);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("IDE: http://localhost:" + port + "/");
    }

    private static void handlePage(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/")) {
            send(exchange, 404, "text/plain", "Not found");
            return;
        }
        try (InputStream page = IdeServer.class.getResourceAsStream("/static/index.html")) {
            send(exchange, 200, "text/html", new String(page.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void handleRun(HttpExchange exchange, boolean debug) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, "text/plain", "Use POST");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_SOURCE_BYTES + 1);
        if (body.length > MAX_SOURCE_BYTES) {
            send(exchange, 413, "text/plain", "Source is too large");
            return;
        }
        send(exchange, 200, "application/json", run(new String(body, StandardCharsets.UTF_8), debug));
    }

    static String run(String source, boolean debug) {
        Compiler compiler = new Compiler(source);
        if (!compiler.compile()) {
            SyntaxError error = compiler.getSyntaxError();
            return "{\"syntaxError\":{\"message\":" + quote(error.getMessage())
                    + ",\"line\":" + error.getLineNumber()
                    + ",\"position\":" + error.getPosition() + "}}";
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VirtualMachine vm = new VirtualMachine(new PrintStream(new LimitedStream(output), true, StandardCharsets.UTF_8));
        String runtimeError = null;
        boolean timedOut = false;
        List<String> steps = new ArrayList<>();
        // Output is ASCII (numbers, spaces, brackets), so its byte count is also the client's char offset.
        Future<?> execution = RUNNER.submit(() -> vm.run(compiler.getProgram(), debug ? programCounter -> {
            if (steps.size() == MAX_DEBUG_STEPS) {
                throw new IllegalStateException("Пошаговый режим: показаны первые " + MAX_DEBUG_STEPS + " шагов");
            }
            steps.add("{\"pc\":" + programCounter + ",\"out\":" + output.size() + ",\"variables\":" + memoryJson(vm) + "}");
        } : programCounter -> { }));
        try {
            execution.get(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            execution.cancel(true);
            timedOut = true;
            runtimeError = "Превышено время выполнения (" + RUN_TIMEOUT_SECONDS + " с)";
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            runtimeError = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            runtimeError = "Выполнение прервано";
        }

        return "{\"bytecode\":" + quote(compiler.getProgram().disassemble())
                + ",\"output\":" + quote(output.toString(StandardCharsets.UTF_8))
                + ",\"runtimeError\":" + (runtimeError == null ? "null" : quote(runtimeError))
                // The VM is still running after a timeout, its maps cannot be read safely.
                + ",\"variables\":" + (timedOut ? "{},\"locals\":{},\"stack\":[]" : memoryJson(vm))
                + (debug ? ",\"lines\":" + linesJson(source, compiler.getProgram())
                        + ",\"steps\":" + (timedOut ? "[]" : "[" + String.join(",", steps) + "]") : "")
                + "}";
    }

    /** 1-based source line of every instruction, 0 when the instruction has no source statement. */
    private static String linesJson(String source, Program program) {
        StringJoiner lines = new StringJoiner(",", "[", "]");
        for (int index = 0; index < program.instructions().size(); index++) {
            int position = program.sourcePosition(index);
            lines.add(String.valueOf(position < 0 ? 0 : source.chars().limit(position).filter(ch -> ch == '\n').count() + 1));
        }
        return lines.toString();
    }

    /** VirtualMachine keeps its globals and stack private, so they are read reflectively; locals of the current call frame are public. */
    private static String memoryJson(VirtualMachine vm) {
        Collection<Object> stack = readField(vm, "stack");
        StringJoiner stackJson = new StringJoiner(",", "[", "]");
        stack.forEach(value -> stackJson.add(quote(VirtualMachine.formatValue(value))));
        return scopeJson(readField(vm, "variables"), readField(vm, "arrays"))
                + ",\"locals\":" + scopeJson(vm.localVariables(), vm.localArrays())
                + ",\"stack\":" + stackJson;
    }

    private static String scopeJson(Map<String, Object> variables, Map<String, Object[]> arrays) {
        StringJoiner json = new StringJoiner(",", "{", "}");
        new TreeMap<>(variables).forEach((name, value) -> json.add(quote(name) + ":" + quote(VirtualMachine.formatValue(value))));
        new TreeMap<>(arrays).forEach((name, value) -> json.add(quote(name) + ":" + quote(VirtualMachine.formatValue(value))));
        return json.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(VirtualMachine vm, String name) {
        try {
            Field field = VirtualMachine.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(vm);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("VirtualMachine." + name + " is unavailable", exception);
        }
    }

    static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        result.append(String.format("\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Stops runaway print loops from exhausting memory. */
    private static final class LimitedStream extends OutputStream {
        private final ByteArrayOutputStream target;

        LimitedStream(ByteArrayOutputStream target) {
            this.target = target;
        }

        @Override
        public void write(int b) {
            if (target.size() >= MAX_OUTPUT_BYTES) {
                throw new IllegalStateException("Слишком большой вывод (больше " + MAX_OUTPUT_BYTES + " байт)");
            }
            target.write(b);
        }
    }
}
