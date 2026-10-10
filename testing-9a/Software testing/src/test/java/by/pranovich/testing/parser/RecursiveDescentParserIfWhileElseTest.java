package by.pranovich.testing.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecursiveDescentParserIfWhileElseTest {

    private RecursiveDescentParser parser;

    @BeforeEach
    void setUp() {
        // Инициализация перед каждым тестом
        parser = null;
    }

    // ========== Тесты для оператора присваивания ==========

    @ParameterizedTest(name = "Assignment: {0}")
    @MethodSource("assignmentTestCases")
    void testAssignmentOperators(String source, String expectedDisassembly) {
        parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        String actual = parser.getProgram().disassemble();

        assertNotNull(actual, "Disassembly should not be null");
        assertTrue(actual.contains(expectedDisassembly),
                "Expected disassembly to contain: " + expectedDisassembly +
                        "\nActual: " + actual);
    }

    static Stream<Arguments> assignmentTestCases() {
        return Stream.of(
                // Простое присваивание int
                Arguments.of(
                        "int x = 1;",
                        "x"
                ),
                // Множественное объявление с инициализацией
                Arguments.of(
                        "int x = 1, y;",
                        "x"
                ),
                // Присваивание float
                Arguments.of(
                        "float z;",
                        "z"
                ),
                // Присваивание с выражением
                Arguments.of(
                        "int x; x = 5 + 3;",
                        "x"
                ),
                // Присваивание с float литералом
                Arguments.of(
                        "float z = 1.5e-1;",
                        "z"
                ),
                // Присваивание с умножением
                Arguments.of(
                        "int y = 10; float z; z = y * 1.5;",
                        "z"
                )
        );
    }

    // ========== Тесты для оператора if ==========

    @ParameterizedTest(name = "If statement: {0}")
    @MethodSource("ifTestCases")
    void testIfOperators(String source, List<String> expectedTokens) {
        parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        String actual = parser.getProgram().disassemble();

        assertNotNull(actual, "Disassembly should not be null");
        for (String token : expectedTokens) {
            assertTrue(actual.contains(token),
                    "Expected disassembly to contain: " + token +
                            "\nActual: " + actual);
        }
    }

    static Stream<Arguments> ifTestCases() {
        return Stream.of(
                // Простой if
                Arguments.of(
                        "int x = 1; if x < 5 { x = 7; }",
                        List.of("JZ", "x", "7")
                ),
                // If с условием сравнения
                Arguments.of(
                        "int x = 1; if x < 5 { x = 7; }",
                        List.of("JZ", "LT")
                ),
                // Вложенный if
                Arguments.of(
                        "int x = 1; if x < 5 { if x > 0 { x = 7; } }",
                        List.of("JZ", "JZ")
                ),
                // If с блоком
                Arguments.of(
                        "int x = 1; if x < 5 { int y = 2; x = 7; }",
                        List.of("JZ", "x")
                )
        );
    }

    // ========== Тесты для оператора if-else ==========

    @ParameterizedTest(name = "If-else statement: {0}")
    @MethodSource("ifElseTestCases")
    void testIfElseOperators(String source, List<String> expectedTokens) {
        parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        String actual = parser.getProgram().disassemble();

        assertNotNull(actual, "Disassembly should not be null");
        for (String token : expectedTokens) {
            assertTrue(actual.contains(token),
                    "Expected disassembly to contain: " + token +
                            "\nActual: " + actual);
        }
    }

    static Stream<Arguments> ifElseTestCases() {
        return Stream.of(
                // Простой if-else
                Arguments.of(
                        "int x = 1, y; if x < 5 { x = 7; } else { y = 10; }",
                        List.of("JZ", "JMP", "x", "y")
                ),
                // If-else с разными типами
                Arguments.of(
                        "int x = 1; float y; if x < 5 { x = 7; } else { y = 10.5; }",
                        List.of("JZ", "JMP")
                ),
                // Вложенный if-else
                Arguments.of(
                        "int x = 1, y; if x < 5 { x = 7; } else { if x > 10 { y = 10; } }",
                        List.of("JZ", "JMP", "JZ")
                ),
                // If-else if-else (цепочка)
                Arguments.of(
                        "int x = 1, y; if x < 5 { x = 7; } else { if x < 10 { y = 10; } else { y = 20; } }",
                        List.of("JZ", "JMP", "JZ", "JMP")
                )
        );
    }

    // ========== Тесты для оператора while ==========

    @ParameterizedTest(name = "While statement: {0}")
    @MethodSource("whileTestCases")
    void testWhileOperators(String source, List<String> expectedTokens) {
        parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        String actual = parser.getProgram().disassemble();

        assertNotNull(actual, "Disassembly should not be null");
        for (String token : expectedTokens) {
            assertTrue(actual.contains(token),
                    "Expected disassembly to contain: " + token +
                            "\nActual: " + actual);
        }
    }

    static Stream<Arguments> whileTestCases() {
        return Stream.of(
                // Простой while
                Arguments.of(
                        "int x = 1; while x < 5 { x = x + 1; }",
                        List.of("JMP", "x")
                ),
                // While с умножением в условии
                Arguments.of(
                        "int y = 10; float z; while z * 5 < y { z = y * 1.5e-1; }",
                        List.of("JMP", "z", "y")
                ),
                // While с блоком из нескольких операторов
                Arguments.of(
                        "int x = 1, y; while x < 5 { x = x + 1; y = x * 2; }",
                        List.of("JMP", "x", "y")
                ),
                // Вложенный while
                Arguments.of(
                        "int x = 1, y; while x < 5 { while y < 3 { y = y + 1; } x = x + 1; }",
                        List.of("JMP", "JMP")
                )
        );
    }

    // ========== Комплексный тест со всеми операторами ==========

    @ParameterizedTest(name = "Complex program: {0}")
    @MethodSource("complexTestCases")
    void testComplexPrograms(String source, List<String> expectedTokens) {
        parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        String actual = parser.getProgram().disassemble();

        assertNotNull(actual, "Disassembly should not be null");
        for (String token : expectedTokens) {
            assertTrue(actual.contains(token),
                    "Expected disassembly to contain: " + token +
                            "\nActual: " + actual);
        }
    }

    static Stream<Arguments> complexTestCases() {
        return Stream.of(
                // Полная программа из примера
                Arguments.of(
                        """
                        int x =1, y;
                        float z;
                        if x < 5 {
                            x = 7;
                        } else { y =10; }
                        while z * 5 < y {
                            z = y * 1.5e-1;
                        }
                        """,
                        List.of("JZ", "JMP", "x", "y", "z")
                ),
                // Программа с вложенными конструкциями
                Arguments.of(
                        """
                        int a = 1, b = 2, c;
                        if a < b {
                            while a < 10 {
                                a = a + 1;
                            }
                        } else {
                            c = a + b;
                        }
                        """,
                        List.of("JZ", "JMP", "a", "b", "c")
                )
        );
    }

    // ========== Негативные тесты ==========

    @ParameterizedTest(name = "Invalid syntax: {0}")
    @MethodSource("invalidSyntaxTestCases")
    void testInvalidSyntax(String source, Class<? extends Exception> expectedException) {
        parser = new RecursiveDescentParser(source);

        assertThrows(expectedException, () -> {
            parser.parseProgram();
        }, "Expected exception for invalid syntax: " + source);
    }

    static Stream<Arguments> invalidSyntaxTestCases() {
        return Stream.of(
                // Отсутствует точка с запятой
                Arguments.of(
                        "int x = 1",
                        RuntimeException.class
                ),
                // Неправильный синтаксис if
                Arguments.of(
                        "int x = 1; if x < 5 x = 7;",
                        RuntimeException.class
                ),
                // Неправильный синтаксис while
                Arguments.of(
                        "int x = 1; while x < 5 x = x + 1;",
                        RuntimeException.class
                ),
                // Незакрытая скобка
                Arguments.of(
                        "int x = 1; if x < 5 { x = 7;",
                        RuntimeException.class
                )
        );
    }
}
