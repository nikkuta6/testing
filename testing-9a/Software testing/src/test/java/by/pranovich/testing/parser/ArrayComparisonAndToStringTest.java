package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whole-array comparison ({@code a == b}, {@code a <> b}) and {@code a.toString()}. */
class ArrayComparisonAndToStringTest {

    @ParameterizedTest(name = "[{index}] comparison: {0}")
    @MethodSource("comparisonPrograms")
    void comparesWholeArrays(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> comparisonPrograms() {
        return Stream.of(
                Arguments.of("int a = [1, 2, 3]; int b = [1, 2, 3]; print a == b; print a <> b;", "1\n0\n"),
                Arguments.of("int a = [1, 2, 3]; int b = [1, 5, 3]; print a == b; print a <> b;", "0\n1\n"),
                Arguments.of("int a = [1, 2]; int b = [1, 2, 0]; print a == b; print a <> b;", "0\n1\n"),
                Arguments.of("int a = [1, 2]; print a == a;", "1\n"),
                Arguments.of("int a[0]; int b[] = []; print a == b;", "1\n"),
                Arguments.of("int a[3]; int b = [0, 0, 0]; print a == b;", "1\n"),
                Arguments.of("int a = [1, 2]; float b = [1.0, 2.0]; print a == b;", "1\n"),
                Arguments.of(
                        "int a = [1, 2, 3]; int b = [1, 2, 3]; b[2] = 4; print a == b; b[2] = 3; print a == b;",
                        "0\n1\n"
                ),
                Arguments.of(
                        "int a = [1, 2, 3]; int b[3]; int i = 0;"
                                + " while i < 3 { b[i] = a[i]; i = i + 1; } print a == b;",
                        "1\n"
                ),
                Arguments.of("int a = [1]; int b = [1]; if a == b { print 1; } else { print 2; }", "1\n"),
                Arguments.of("int a = [1]; int b = [2]; if a <> b { print 1; } else { print 2; }", "1\n"),
                Arguments.of("int a = [1]; int b = [1]; print a == b && a[0] == 1; print a <> b || a[0] == 2;", "1\n0\n"),
                Arguments.of("int a = [1]; int b = [1]; int r = a <> b; print r;", "0\n"),
                Arguments.of("int a = [1]; int b = [1]; print (a == b) + 1; print !(a == b);", "2\n0\n"),
                Arguments.of("int a = [1]; int b = [1]; while a == b { b[0] = 2; } print b[0];", "2\n")
        );
    }

    @ParameterizedTest(name = "[{index}] toString: {0}")
    @MethodSource("toStringPrograms")
    void printsArrayWithToString(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> toStringPrograms() {
        return Stream.of(
                Arguments.of("int a = [1, 2, 3]; print a.toString();", "[1, 2, 3]\n"),
                Arguments.of("int a = [-1, 2]; print a.toString();", "[-1, 2]\n"),
                Arguments.of("float a = [1.5, 2]; print a.toString();", "[1.5, 2.0]\n"),
                Arguments.of("int a[3]; print a.toString();", "[0, 0, 0]\n"),
                Arguments.of("int a[0]; print a.toString();", "[]\n"),
                Arguments.of("int a[3] = [7]; print a.toString();", "[7, 0, 0]\n"),
                Arguments.of("int a = [5]; print a . toString ( ) ;", "[5]\n"),
                Arguments.of(
                        "int a[4]; int i = 0; while i < 4 { a[i] = i * i; i = i + 1; } print a.toString();",
                        "[0, 1, 4, 9]\n"
                ),
                Arguments.of(
                        "int a = [1, 2]; if a[0] == 1 { a[1] = 5; print a.toString(); }",
                        "[1, 5]\n"
                ),
                Arguments.of(
                        "int a = [3, 1, 2]; print a.toString(); a[0] = a[1]; print a.toString();",
                        "[3, 1, 2]\n[1, 1, 2]\n"
                )
        );
    }

    @ParameterizedTest(name = "invalid source: {0}")
    @ValueSource(strings = {
            "int a = [1]; print a == 1;",
            "int a = [1]; int x = 1; print a == x;",
            "int a = [1]; int x = 1; print x == a;",
            "int a = [1]; print a == b;",
            "int a = [1]; int b = [1]; print a == b[0];",
            "int a = [1]; int b = [1]; print a < b;",
            "int a = [1]; print a.toString;",
            "int a = [1]; print a.toString(;",
            "int a = [1]; print a.size();",
            "int x = 1; print x.toString();",
            "print b.toString();",
            "int a = [1]; print a.toString() + 1;",
            "int a = [1]; int s = a.toString();",
            "int a = [1]; a.toString();"
    })
    void rejectsInvalidComparisonAndToString(String source) {
        assertThrows(RuntimeException.class, () -> compile(source));
    }

    @Test
    void emitsArrayComparisonAndPrintInstructions() {
        String disassembly = compile("int a = [1]; int b = [1]; print a <> b; print a.toString();").disassemble();
        assertTrue(disassembly.contains("ARRAY_EQ [a, b]"), disassembly);
        assertTrue(disassembly.contains("NOT"), disassembly);
        assertTrue(disassembly.contains("ARRAY_PRINT [a, int]"), disassembly);
    }

    @Test
    void comparingArrayDeclaredInSkippedBranchFailsAtRuntime() {
        Program program = compile("int a = [1]; if 0 { int b[1]; } print a == b;");
        assertThrows(IllegalStateException.class, () -> new VirtualMachine().run(program));
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
