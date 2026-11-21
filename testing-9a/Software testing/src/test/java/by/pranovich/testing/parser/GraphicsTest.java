package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.DrawingCommand;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphicsTest {
    @Test
    void recordsDrawingFromExpressionsLoopsAndFunctionsInOrder() {
        VirtualMachine vm = new VirtualMachine();
        vm.run(compile("""
                canvas(400, 240);
                color(40, 110, 220);
                fun void draw(float x) { line(x, 0, x + 10, 20); }
                int n = 0;
                while n < 2 { draw(n * 10); n = n + 1; }
                circle(100, 80, 20 + 5);
                rect(10, 20, 30, 40);
                clear();
                """));
        assertEquals(List.of(
                new DrawingCommand("canvas", List.of(400.0, 240.0)),
                new DrawingCommand("color", List.of(40.0, 110.0, 220.0)),
                new DrawingCommand("line", List.of(0.0, 0.0, 10.0, 20.0)),
                new DrawingCommand("line", List.of(10.0, 0.0, 20.0, 20.0)),
                new DrawingCommand("circle", List.of(100.0, 80.0, 25.0)),
                new DrawingCommand("rect", List.of(10.0, 20.0, 30.0, 40.0)),
                new DrawingCommand("clear", List.of())), vm.drawingCommands());
    }

    @Test
    void debugSnapshotsCountOnlyCompletedCommandsAndRunResetsDrawing() {
        VirtualMachine vm = new VirtualMachine();
        List<Integer> counts = new ArrayList<>();
        vm.run(compile("clear(); circle(10, 20, 3);"), pc -> counts.add(vm.drawingCommandCount()));
        assertEquals(0, counts.getFirst());
        assertEquals(2, counts.getLast());
        assertTrue(counts.contains(1));
        assertThrows(UnsupportedOperationException.class, () -> vm.drawingCommands().clear());
        vm.run(compile("int x = 1;"));
        assertEquals(0, vm.drawingCommandCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "line(1, 2, 3);", "circle(1, 2, 3, 4);", "rect(1, 2);", "canvas();",
            "color(1, 2);", "clear(1);", "line(1i, 0, 1, 2);", "print(circle(1, 2, 3));",
            "int line = 1;", "fun void rect() {}"
    })
    void rejectsInvalidDrawingSyntax(String source) {
        assertThrows(ParseException.class, () -> compile(source));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "canvas(0, 20);", "canvas(4097, 20);", "canvas(10.5, 20);",
            "color(-1, 0, 0);", "color(0, 256, 0);", "color(0, 0, 0.5);",
            "circle(1, 2, -1);", "rect(1, 2, -1, 5);", "rect(1, 2, 5, -1);"
    })
    void rejectsInvalidDrawingValuesWithoutRecordingTheCommand(String source) {
        VirtualMachine vm = new VirtualMachine();
        assertThrows(IllegalArgumentException.class, () -> vm.run(compile(source)));
        assertTrue(vm.drawingCommands().isEmpty());
    }

    @Test
    void boundsDrawingHistoryAndKeepsTheValidPrefixOnFailure() {
        VirtualMachine vm = new VirtualMachine();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> vm.run(compile("int n = 0; while n < 10001 { clear(); n = n + 1; }")));
        assertTrue(error.getMessage().contains("10000"));
        assertEquals(10000, vm.drawingCommandCount());
    }

    private Program compile(String source) {
        RecursiveDescentParser parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        return parser.getProgram();
    }
}
