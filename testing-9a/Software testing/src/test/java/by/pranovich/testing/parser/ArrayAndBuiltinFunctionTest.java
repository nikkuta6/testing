package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArrayAndBuiltinFunctionTest {

    @ParameterizedTest(name = "[{index}] built-ins: {0}")
    @MethodSource("builtinPrograms")
    void executesBuiltinFunctions(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> builtinPrograms() {
        return Stream.of(
                Arguments.of(
                        "print sin(0); print cos(0); print cod(0); print tg(0); print ctg(0);",
                        "0.0\n1.0\n1.0\n0.0\nInfinity\n"
                ),
                Arguments.of(
                        "print abs(-5); print max(7, 2 + 3, abs(-8)); print min(7, 2, 4);",
                        "5\n8\n2\n"
                ),
                Arguments.of(
                        "float x = max(1, min(2.5, 4)); print x;",
                        "2.5\n"
                )
        );
    }

    @ParameterizedTest(name = "[{index}] arrays: {0}")
    @MethodSource("arrayPrograms")
    void createsReadsAndWritesArrays(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> arrayPrograms() {
        return Stream.of(
                Arguments.of(
                        "int a[3]; a[0] = 4; a[1] = a[0] + 2; print a[0]; print a[1]; print a[2];",
                        "4\n6\n0\n"
                ),
                Arguments.of(
                        "int a = [1, 5, 2]; print max(a[0], a[1], a[2]);",
                        "5\n"
                ),
                Arguments.of(
                        "float a[] = [1, 2.5, 3]; print a[1];",
                        "2.5\n"
                ),
                Arguments.of(
                        "int a[4] = [1, 2]; print a[0]; print a[3];",
                        "1\n0\n"
                ),
                Arguments.of(
                        "int a = [1, 2, 3]; int i = 0, sum = 0; while i < 3 { sum = sum + a[i]; i = i + 1; } print sum;",
                        "6\n"
                )
        );
    }

    @ParameterizedTest(name = "invalid source: {0}")
    @ValueSource(strings = {
            "print unknown(1);",
            "print sin(1, 2);",
            "print max(1);",
            "int a[];",
            "int x = 1; print x[0];",
            "int a[2]; print a;"
    })
    void rejectsInvalidFunctionAndArraySyntax(String source) {
        assertThrows(RuntimeException.class, () -> compile(source));
    }

    @ParameterizedTest(name = "runtime bounds/size error: {0}")
    @ValueSource(strings = {
            "int a[2]; print a[2];",
            "int a[2]; a[-1] = 5;",
            "int a[1.5];",
            "int a[-1];",
            "int a[1] = [1, 2];"
    })
    void reportsInvalidArraySizeOrIndex(String source) {
        Program program = compile(source);
        assertThrows(RuntimeException.class, () -> new VirtualMachine().run(program));
    }

    private String execute(String source) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            new VirtualMachine(out).run(compile(source));
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private Program compile(String source) {
        RecursiveDescentParser parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        return parser.getProgram();
    }
}
