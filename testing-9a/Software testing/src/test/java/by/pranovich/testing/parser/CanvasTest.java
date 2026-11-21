package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasTest {

    @Test
    void recordsDrawCommandsInOrder() {
        VirtualMachine vm = new VirtualMachine();
        vm.run(compile("clear(); color(255, 0, 0); int i = 0;"
                + " while i < 2 { line(i, 0, i * 10, 20 / 2); i = i + 1; }"
                + " point(1.5, 2); rect(0, 0, 4, 3); circle(5, 5, 2);"));
        assertEquals(List.of("clear", "color 255.0 0.0 0.0", "line 0.0 0.0 0.0 10.0", "line 1.0 0.0 10.0 10.0",
                "point 1.5 2.0", "rect 0.0 0.0 4.0 3.0", "circle 5.0 5.0 2.0"), vm.canvas());
    }

    @Test
    void goroutinesDrawOnTheSameCanvas() {
        VirtualMachine vm = new VirtualMachine();
        vm.run(compile("fun void dot(int x) { point(x, x); } go dot(3);"));
        assertEquals(List.of("point 3.0 3.0"), vm.canvas());
    }

    @Test
    void canvasIsClearedBetweenRuns() {
        VirtualMachine vm = new VirtualMachine();
        vm.run(compile("point(1, 1);"));
        vm.run(compile("int x = 0;"));
        assertEquals(List.of(), vm.canvas());
    }

    @ParameterizedTest
    @ValueSource(strings = {"line(1, 2, 3);", "clear(1);", "color(1, 2, 3, 4);", "point(1i, 2);",
            "int line = 1;", "point(1, 2)"})
    void rejectsInvalidDrawing(String source) {
        assertThrows(ParseException.class, () -> compile(source));
    }

    private Program compile(String source) {
        RecursiveDescentParser parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        return parser.getProgram();
    }
}
