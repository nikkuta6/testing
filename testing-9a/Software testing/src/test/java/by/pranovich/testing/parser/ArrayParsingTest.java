package by.pranovich.testing.parser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Array syntax tests: declarations, initializers, element access and the
 * byte-code emitted for array operations.
 */
class ArrayParsingTest {

    @ParameterizedTest(name = "array expression: {0}")
    @ValueSource(strings = {
            "a[0]",
            "a[i + 1]",
            "a[a[0]]",
            "a [ 0 ]",
            "max(a[0], a[1])",
            "a[0]!",
            "a[0] ^ 2",
            "(a[0] + a[1]) * 2",
            "-a[0]",
            "a[0] < a[1]"
    })
    void parsesArrayElementExpressions(String expression) {
        assertTrue(new RecursiveDescentParser(expression).parse());
    }

    @ParameterizedTest(name = "trailing input after array expression: {0}")
    @ValueSource(strings = {
            "a[0]]",
            "a[0] a[1]",
            "a[0][1]",
            "a[0] b"
    })
    void rejectsTrailingInputAfterArrayExpression(String expression) {
        assertFalse(new RecursiveDescentParser(expression).parse());
    }

    @ParameterizedTest(name = "malformed array expression: {0}")
    @ValueSource(strings = {
            "a[",
            "a[]",
            "a[0",
            "[1, 2]"
    })
    void rejectsMalformedArrayExpressions(String expression) {
        assertThrows(RuntimeException.class, () -> new RecursiveDescentParser(expression).parse());
    }

    @ParameterizedTest(name = "array program: {0}")
    @ValueSource(strings = {
            "int a[3];",
            "int a[0];",
            "int a[3]; a[0] = 1;",
            "int a[3] = [1, 2];",
            "int a[] = [1, 2, 3];",
            "float a[] = [1, 2.5];",
            "int a[2], b[3] = [1, 2, 3];",
            "int a[2] = [1, 2], x = 5;",
            "int n = 3; int a[n];",
            "int n = 2; int a[n * 2];",
            "int a = [1 + 1, max(2, 3)];",
            "int a[3]; int i = 0; while i < 3 { a[i] = i ^ 2; i = i + 1; }",
            "int a = [1, 2, 3]; if a[1] > a[0] { a[0] = a[2]; }",
            "int a = [1, 2, 3]; print a[a[0]];",
            "int a[2] = [1, 2]; int b[3] = [a[0], a[1], a[0] + a[1]];"
    })
    void parsesArrayPrograms(String source) {
        assertDoesNotThrow(() -> new RecursiveDescentParser(source).parseProgram());
    }

    @ParameterizedTest(name = "invalid array program: {0}")
    @ValueSource(strings = {
            "int a[];",
            "int a[3] = 5;",
            "int a[] = 5;",
            "int a[3 = [1, 2];",
            "int a[3] = [1, 2;",
            "int a[2][3];",
            "int a[2] = [[1, 2]];",
            "int a[2]; int a[3];",
            "int a[2]; print a;",
            "int a[2]; int b = a;",
            "int a[2]; a = [1, 2];",
            "print a[0];",
            "int a[2]; print a[0][0];",
            "int a[2]; a[0] = 1",
            "int a[2]; a[0] 1;",
            "int a[2]; a[0] = ;"
    })
    void rejectsInvalidArrayPrograms(String source) {
        assertThrows(RuntimeException.class, () -> new RecursiveDescentParser(source).parseProgram());
    }

    @ParameterizedTest(name = "disassembly for: {0}")
    @MethodSource("disassemblyCases")
    void emitsArrayInstructions(String source, List<String> expectedInstructions) {
        RecursiveDescentParser parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        String disassembly = parser.getProgram().disassemble();

        for (String expected : expectedInstructions) {
            assertTrue(disassembly.contains(expected),
                    () -> "Ожидалась инструкция '" + expected + "' в дизассемблере:\n" + disassembly);
        }
    }

    static Stream<Arguments> disassemblyCases() {
        return Stream.of(
                Arguments.of(
                        "int a[3];",
                        List.of("PUSH 3.0", "ARRAY_NEW a")
                ),
                Arguments.of(
                        "int a = [1, 2];",
                        List.of("PUSH 1.0", "PUSH 2.0", "ARRAY_INIT a 2")
                ),
                Arguments.of(
                        "int a[3] = [1, 2];",
                        List.of("PUSH 3.0", "ARRAY_INIT a 2 sized")
                ),
                Arguments.of(
                        "int a[3]; a[0] = 7;",
                        List.of("PUSH 0.0", "PUSH 7.0", "ARRAY_STORE a")
                ),
                Arguments.of(
                        "int a[3]; print a[0];",
                        List.of("PUSH 0.0", "ARRAY_LOAD a")
                ),
                Arguments.of(
                        "int n = 2; int a[n];",
                        List.of("LOAD n", "ARRAY_NEW a")
                )
        );
    }
}
