package by.pranovich.wasm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WasmRuntimeTest {
    @BeforeAll static void installLanguageProgram() throws Exception {
        Mandelbrot.install(Files.readString(Path.of(System.getProperty("mandelbrotProgramSource"))));
    }

    @Test void languageRasterAndOrbitMatchThePreviousReference() {
        for (double[] plane : new double[][] {{-2.5, 1, -1.3125, 1.3125}, {-0.752, -0.742, 0.095, 0.105}}) {
            assertEquals(MandelbrotReference.renderBand(plane[0], plane[1], plane[2], plane[3], 24, 18, 0, 18, 120),
                    Mandelbrot.renderBand(plane[0], plane[1], plane[2], plane[3], 24, 18, 0, 18, 120));
        }
        for (double[] c : new double[][] {{0, 0}, {-1, 0}, {-0.75, 0.1}, {1, 0}, {0, 1}}) {
            assertEquals(MandelbrotReference.orbit(c[0], c[1], 80), Mandelbrot.orbit(c[0], c[1], 80));
        }
    }

    @Test void rendersMoreThanTheDebuggerHistoryLimitThroughStreamingPixels() {
        assertEquals(160 * 64 * 4, java.util.Base64.getDecoder().decode(
                Mandelbrot.renderBand(-2.5, 1, -1.3125, 1.3125, 160, 64, 0, 64, 20)).length);
    }

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
