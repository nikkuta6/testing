package by.pranovich.wasm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WasmRuntimeTest {
    @Test void preservesCallsAndDebugProtocol() {
        String result = RunService.run("fun int f(int n) { return n * 2; } print(f(7));", true);
        assertTrue(result.contains("\"output\":\"14\\n\""));
        assertTrue(result.contains("\"locals\":{\"n\":\"7.0\"}"));
        assertTrue(result.contains("\"steps\":["));
    }
    @Test void keepsDrawingBeforeRuntimeError() {
        String result = RunService.run("circle(20, 30, 10); int n = 1 / 0;", false);
        assertTrue(result.contains("\"name\":\"circle\""));
        assertTrue(result.contains("Деление на ноль"));
    }
    @Test void reportsSyntaxPositionAndUnicodeIdentifiers() {
        assertTrue(RunService.run("int имя = 2; print(имя);", false).contains("\"output\":\"2\\n\""));
        String result = RunService.run("int x = 1;\nprint(missing);", false);
        assertTrue(result.contains("\"syntaxError\":"));
        assertTrue(result.contains("\"line\":2"));
    }
    @Test void boundsTracesAndInputSize() {
        assertTrue(RunService.run("while 1 {}", true).contains("5000"));
        assertThrows(IllegalArgumentException.class, () -> RunService.run(" ".repeat((1 << 20) + 1), false));
    }
    @Test void escapeOnLastStepIsNotMistakenForInterior() {
        assertTrue(Mandelbrot.sampleJson(1, 0, 3).contains("\"iterations\":3"));
        assertTrue(Mandelbrot.sampleJson(1, 0, 2).contains("\"iterations\":0"));
    }
    @Test void boundsFractalBuffersAndCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> Mandelbrot.renderBand(-2, 1, -1, 1, 4097, 100, 0, 10, 200));
        assertThrows(IllegalArgumentException.class, () -> Mandelbrot.orbit(Double.NaN, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> Mandelbrot.orbit(0, 0, 1001));
    }
}
