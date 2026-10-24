package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.compiler.SyntaxError;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Array creation and usage: algorithms over arrays, independence of arrays,
 * element types, compile-time error locations and runtime error messages.
 */
class ArrayOperationsTest {

    @ParameterizedTest(name = "[{index}] creation: {0}")
    @MethodSource("creationPrograms")
    void createsArrays(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> creationPrograms() {
        return Stream.of(
                Arguments.of("int a[1]; print(a[0]);", "0\n"),
                Arguments.of("float a[2]; print(a[1]);", "0.0\n"),
                Arguments.of("int x = 4; int a = [x, x * x]; print(a[1]);", "16\n"),
                Arguments.of("int s = [3]; int a[s[0]]; a[2] = 1; print(a[2]);", "1\n"),
                Arguments.of("float a = [1.5, 2.5]; print(a[0] + a[1]); print(a[0] * 2);", "4.0\n3.0\n"),
                Arguments.of("int x = 1; if x == 1 { int a = [4, 5]; print(a[1]); }", "5\n")
        );
    }

    @ParameterizedTest(name = "[{index}] usage: {0}")
    @MethodSource("usagePrograms")
    void worksWithArrayElements(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> usagePrograms() {
        return Stream.of(
                Arguments.of(
                        "int a[1]; a[0] = 1; a[0] = a[0] + 10; a[0] = a[0] * 2; print(a[0]);",
                        "22\n"
                ),
                Arguments.of(
                        "int a = [3, 2]; print(a[0]!); print(-a[1]); print(a[0] ^ a[1]); print(a[0] - a[1]);",
                        "6\n-2\n9\n1\n"
                ),
                Arguments.of(
                        "int a = [7, 8]; print(a[4 / 2 - 1]);",
                        "8\n"
                ),
                Arguments.of(
                        "int a = [0, 1]; if a[0] { print(1); } else { print(2); }",
                        "2\n"
                ),
                Arguments.of(
                        "int a[2], b[2]; a[0] = 1; b[0] = 2; print(a[0]); print(b[0]);",
                        "1\n2\n"
                ),
                Arguments.of(
                        "int a = [1, 2, 3]; int b[3]; int i = 0;"
                                + " while i < 3 { b[i] = a[i]; i = i + 1; }"
                                + " a[0] = 100; print(a[0]); print(b[0]);",
                        "100\n1\n"
                )
        );
    }

    @ParameterizedTest(name = "[{index}] algorithm: {0}")
    @MethodSource("algorithmPrograms")
    void runsAlgorithmsOverArrays(String name, String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> algorithmPrograms() {
        return Stream.of(
                Arguments.of("reverse",
                        "int a = [1, 2, 3, 4, 5]; int i = 0, t = 0;"
                                + " while i < 2 { t = a[i]; a[i] = a[4 - i]; a[4 - i] = t; i = i + 1; }"
                                + " print(a[0]); print(a[1]); print(a[2]); print(a[3]); print(a[4]);",
                        "5\n4\n3\n2\n1\n"),
                Arguments.of("bubble sort",
                        "int a = [5, 3, 4, 1, 2]; int i = 0, j = 0, t = 0;"
                                + " while i < 4 { j = 0;"
                                + "   while j < 4 - i {"
                                + "     if a[j] > a[j + 1] { t = a[j]; a[j] = a[j + 1]; a[j + 1] = t; }"
                                + "     j = j + 1; }"
                                + "   i = i + 1; }"
                                + " print(a[0]); print(a[1]); print(a[2]); print(a[3]); print(a[4]);",
                        "1\n2\n3\n4\n5\n"),
                Arguments.of("fibonacci",
                        "int f[8]; f[1] = 1; int i = 2;"
                                + " while i < 8 { f[i] = f[i - 1] + f[i - 2]; i = i + 1; }"
                                + " print(f[7]);",
                        "13\n"),
                Arguments.of("index of maximum",
                        "int a = [4, 9, 2, 9, 1]; int i = 1, best = 0;"
                                + " while i < 5 { if a[i] > a[best] { best = i; } i = i + 1; }"
                                + " print(best); print(a[best]);",
                        "1\n9\n"),
                Arguments.of("count in range",
                        "int a = [1, 5, 8, 3, 10]; int i = 0, count = 0;"
                                + " while i < 5 { if a[i] > 2 && a[i] < 9 { count = count + 1; } i = i + 1; }"
                                + " print(count);",
                        "3\n")
        );
    }

    @Test
    void rerunningProgramStartsWithFreshArrays() {
        Program program = compile("int a[2]; a[0] = a[0] + 1; print(a[0]);");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            VirtualMachine vm = new VirtualMachine(out);
            vm.run(program);
            vm.run(program);
        }
        assertEquals("1\n1\n", buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    @ParameterizedTest(name = "[{index}] compile error: {0}")
    @MethodSource("compileErrors")
    void reportsArrayCompileErrorWithLocation(String source, int expectedLine, String messagePart) {
        Compiler compiler = new Compiler(source);

        assertFalse(compiler.compile());
        SyntaxError error = compiler.getSyntaxError();
        assertNotNull(error);
        assertEquals(expectedLine, error.getLineNumber());
        assertTrue(error.getMessage().contains(messagePart),
                () -> "Сообщение '" + error.getMessage() + "' не содержит '" + messagePart + "'");
    }

    static Stream<Arguments> compileErrors() {
        return Stream.of(
                Arguments.of("int a[2];\nprint(b[0]);", 2, "'b' не объявлен"),
                Arguments.of("int a[2];\na = 1;", 2, "нужен индекс"),
                Arguments.of("int a[2];\nprint(a + 1);", 2, "нужен индекс"),
                Arguments.of("int a[2];\nint a[3];", 2, "уже объявлено"),
                Arguments.of("int x = 1;\nint a[];", 2, "инициализатор")
        );
    }

    @Test
    void reportsIndexOutOfBoundsWithArrayNameAndLength() {
        Program program = compile("int a[2]; print(a[5]);");
        IndexOutOfBoundsException error = assertThrows(IndexOutOfBoundsException.class,
                () -> new VirtualMachine().run(program));
        assertTrue(error.getMessage().contains("5"));
        assertTrue(error.getMessage().contains("'a'"));
        assertTrue(error.getMessage().contains("2"));
    }

    @Test
    void reportsTooManyInitializerValues() {
        Program program = compile("int a[1] = [1, 2];");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new VirtualMachine().run(program));
        assertTrue(error.getMessage().contains("Слишком много значений"));
    }

    @Test
    void reportsNegativeSize() {
        Program program = compile("int a[-2];");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new VirtualMachine().run(program));
        assertTrue(error.getMessage().contains("Размер массива"));
    }

    @Test
    void reportsArrayDeclaredInSkippedBranch() {
        // the parser sees the declaration, but the VM never executes ARRAY_NEW
        Program program = compile("if 0 { int a[2]; } print(a[0]);");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new VirtualMachine().run(program));
        assertTrue(error.getMessage().contains("не создан"));
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
