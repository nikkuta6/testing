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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exponentiation ({@code ^}) tests: parsing, precedence, associativity and
 * execution on the stack machine.
 */
class PowerOperatorTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("expressions")
    void evaluatesPowerExpressions(String expression, String expected) {
        assertEquals(expected + "\n", execute("print(" + expression + ");"));
    }

    static Stream<Arguments> expressions() {
        return Stream.of(
                Arguments.of("2 ^ 3", "8"),
                Arguments.of("2^10", "1024"),
                Arguments.of("0 ^ 0", "1"),
                Arguments.of("2 ^ 0", "1"),
                Arguments.of("2 ^ 3 ^ 2", "512"),
                Arguments.of("(2 ^ 3) ^ 2", "64"),
                Arguments.of("-2 ^ 2", "-4"),
                Arguments.of("(-2) ^ 2", "4"),
                Arguments.of("-2 ^ 3", "-8"),
                Arguments.of("2.5 ^ 2", "6.25"),
                Arguments.of("4 ^ 0.5", "2.0"),
                Arguments.of("2 ^ 3 * 4", "32"),
                Arguments.of("2 * 3 ^ 2", "18"),
                Arguments.of("2 + 3 ^ 2", "11"),
                Arguments.of("10 - 2 ^ 3", "2"),
                Arguments.of("3! ^ 2", "36"),
                Arguments.of("2 ^ 3!", "64"),
                Arguments.of("2 ^ 3 == 8", "1"),
                Arguments.of("max(2 ^ 3, 3 ^ 2)", "9")
        );
    }

    @ParameterizedTest(name = "[{index}] program: {0}")
    @MethodSource("programs")
    void executesPowerInPrograms(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of(
                        "int x = 2; x = x ^ 3; print(x);",
                        "8\n"
                ),
                Arguments.of(
                        "float x = 2.0; print(x ^ 3);",
                        "8.0\n"
                ),
                Arguments.of(
                        "float x = 2 ^ -2; print(x);",
                        "0.25\n"
                ),
                Arguments.of(
                        "int a = [1, 2, 3]; print(a[2] ^ 2);",
                        "9\n"
                ),
                Arguments.of(
                        "if 2 ^ 3 == 8 { print(1); } else { print(0); }",
                        "1\n"
                ),
                Arguments.of(
                        "int p = 1, i = 0; while i < 3 { p = p * 2; i = i + 1; } print(p ^ 2);",
                        "64\n"
                )
        );
    }

    @ParameterizedTest(name = "expression parser accepts {0}")
    @ValueSource(strings = {
            "2^3",
            "2 ^ 3 ^ 2",
            "-2^2",
            "2^-1",
            "a^b",
            "(a + b)^2",
            "3!^2",
            "2^3!"
    })
    void parsesPowerExpressions(String expression) {
        assertTrue(new RecursiveDescentParser(expression).parse());
    }

    @ParameterizedTest(name = "expression parser rejects trailing input in {0}")
    @ValueSource(strings = {
            "2^3)",
            "2^3 4"
    })
    void rejectsTrailingInputAfterPowerExpression(String expression) {
        assertFalse(new RecursiveDescentParser(expression).parse());
    }

    @ParameterizedTest(name = "invalid power source: {0}")
    @ValueSource(strings = {
            "print(2 ^);",
            "print(^ 2);",
            "print(2 ^ ^ 3);",
            "print(2 ^ * 3);",
            "print((2 ^ 3);"
    })
    void rejectsMalformedPowerExpressions(String source) {
        assertThrows(RuntimeException.class, () -> compile(source));
    }

    @ParameterizedTest(name = "invalid power expression: {0}")
    @ValueSource(strings = {
            "2^",
            "^2",
            "2^^3"
    })
    void rejectsMalformedPowerExpressionsInExpressionMode(String expression) {
        assertThrows(RuntimeException.class, () -> new RecursiveDescentParser(expression).parse());
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
