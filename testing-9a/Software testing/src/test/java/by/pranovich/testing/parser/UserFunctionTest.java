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

class UserFunctionTest {

    @ParameterizedTest(name = "[{index}] function: {0}")
    @MethodSource("functionPrograms")
    void executesUserFunctions(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> functionPrograms() {
        return Stream.of(
                Arguments.of("hello() { print(42); } hello();", "42\n"),
                Arguments.of(
                        "func sum(int a, int b) { return a + b; } print(sum(1, 2));",
                        "3\n"
                ),
                Arguments.of(
                        "func average(float a, float b) { return (a + b) / 2; }"
                                + " print(average(1, 2.0));",
                        "1.5\n"
                ),
                Arguments.of(
                        "func twice(int x) { return x * 2; } print(twice(4) + 1);",
                        "9\n"
                ),
                Arguments.of(
                        "func fact(int n) { if n <= 1 { return 1; } return n * fact(n - 1); }"
                                + " print(fact(5));",
                        "120\n"
                ),
                Arguments.of(
                        "func value() { return 7; } value(); print(1);",
                        "1\n"
                ),
                Arguments.of("sum(a, b) { print(a + b); } sum(2, 3);", "5.0\n"),
                Arguments.of(
                        "inner(x) { print(x); } outer(y) { inner(y + 1); } outer(4);",
                        "5.0\n"
                ),
                Arguments.of(
                        "count(n) { print(n); if n > 1 { count(n - 1); } } count(3);",
                        "3.0\n2.0\n1.0\n"
                ),
                Arguments.of(
                        "show(x) { int local = x + 1; print(local); }"
                                + " int x = 10; show(2); print(x);",
                        "3\n10\n"
                ),
                Arguments.of(
                        "showArray(n) { int a[2] = [n, n + 1]; print(a); } showArray(4);",
                        "[4, 5]\n"
                ),
                Arguments.of(
                        "pair(a, b) { print(a, b); } pair(1, 2.5); pair(-3, 4);",
                        "1.0 2.5\n-3.0 4.0\n"
                )
        );
    }

    @ParameterizedTest(name = "[{index}] return type: {0}")
    @MethodSource("returnTypePrograms")
    void returnsValuesOfDifferentTypes(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> returnTypePrograms() {
        return Stream.of(
                Arguments.of(
                        "func add(int a, int b) { return a + b; } print(add(2, 3));",
                        "5\n"
                ),
                Arguments.of(
                        "func divide(float a, float b) { return a / b; } print(divide(5, 2));",
                        "2.5\n"
                ),
                Arguments.of(
                        "func choose(int flag) { if flag { return 1; } return 2.5; }"
                                + " print(choose(1), choose(0));",
                        "1.0 2.5\n"
                ),
                Arguments.of(
                        "func magnitude(int value) { return abs(value); } print(magnitude(-7));",
                        "7\n"
                ),
                Arguments.of(
                        "func inverse(float value) { return 1 / value; } print(inverse(4));",
                        "0.25\n"
                )
        );
    }

    @ParameterizedTest(name = "[{index}] nested call: {0}")
    @MethodSource("nestedFunctionPrograms")
    void executesNestedFunctionCalls(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> nestedFunctionPrograms() {
        return Stream.of(
                Arguments.of(
                        "func increment(int value) { return value + 1; }"
                                + " func twiceIncremented(int value) { return increment(increment(value)); }"
                                + " print(twiceIncremented(5));",
                        "7\n"
                ),
                Arguments.of(
                        "func square(int value) { return value ^ 2; }"
                                + " func add(int a, int b) { return a + b; }"
                                + " print(add(square(2), square(3)));",
                        "13\n"
                ),
                Arguments.of(
                        "func clampMagnitude(int a, int b) { return max(abs(a), abs(b)); }"
                                + " print(clampMagnitude(-4, 3));",
                        "4\n"
                ),
                Arguments.of(
                        "func add(float a, float b) { return a + b; }"
                                + " func half(float value) { return value / 2; }"
                                + " print(half(add(2.5, add(1, 2))));",
                        "2.75\n"
                ),
                Arguments.of(
                        "func identity(int value) { return value; }"
                                + " print(max(identity(2), identity(identity(5))));",
                        "5\n"
                )
        );
    }

    @ParameterizedTest(name = "invalid function: {0}")
    @ValueSource(strings = {
            "missing();",
            "f(a) { print(a); } f();",
            "f(a) { print(a); } f(1, 2);",
            "f(a, a) { print(a); }",
            "f() { g() { print(1); } }",
            "f() { print(1); } f() { print(2); }",
            "int f = 1; f() { print(1); }",
            "f() { print(1); } int f = 1;",
            "f(x) { print(x); } print(f(1));",
            "f(1) { print(1); }",
            "return 1;",
            "func f(a) { return a; }",
            "func f(int a, float a) { return a; }",
            "func f(int a) { print(a); } print(f(1));",
            "func f(int a) { return a; } print(f());",
            "func f() { func g() { return 1; } return 0; }"
    })
    void rejectsInvalidUserFunctions(String source) {
        assertThrows(RuntimeException.class, () -> compile(source));
    }

    @Test
    void emitsFunctionControlFlowInstructions() {
        String disassembly = compile(
                "func echo(int value) { return value; } print(echo(7));").disassemble();
        assertTrue(disassembly.contains("JMP"), disassembly);
        assertTrue(disassembly.contains("RETURN"), disassembly);
        assertTrue(disassembly.contains("USER_CALL echo/1"), disassembly);
    }

    @Test
    void reportsMissingReturnOnExecutedPath() {
        Program program = compile(
                "func maybe(int value) { if value { return 1; } } print(maybe(0));");
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
