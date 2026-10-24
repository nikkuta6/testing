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

class LogicalExpressionTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("expressions")
    void respectsLogicalAndArithmeticPrecedence(String expression, String expected) {
        assertEquals(expected + "\n", execute("print(" + expression + ");"));
    }

    static Stream<Arguments> expressions() {
        return Stream.of(
                Arguments.of("1 < 2 || 3 > 4", "1"),
                Arguments.of("1 > 2 || 3 < 4", "1"),
                Arguments.of("1 > 2 && 3 < 4", "0"),
                Arguments.of("1 < 2 && 3 < 4", "1"),
                Arguments.of("1 || 0 && 0", "1"),
                Arguments.of("(1 || 0) && 0", "0"),
                Arguments.of("1 + 2 * 3 == 7", "1"),
                Arguments.of("10 - 2 * 3 > 3 && 4 <> 5", "1"),
                Arguments.of("!(1 <> 1)", "1"),
                Arguments.of("max(1, 2) == 2 && abs(-3) <> 2", "1")
        );
    }

    @ParameterizedTest(name = "unsupported not-equal spelling: {0}")
    @ValueSource(strings = {"print(1 != 2);", "int x = 1; if x != 2 { x = 3; }"})
    void rejectsExclamationEqualsBecauseLanguageUsesAngleBrackets(String source) {
        assertThrows(RuntimeException.class, () -> compile(source));
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
