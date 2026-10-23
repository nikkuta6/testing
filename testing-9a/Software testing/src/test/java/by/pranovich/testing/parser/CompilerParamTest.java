package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Параметризованные тесты однопроходного компилятора с учебным байт-кодом
 * (без токенайзера и AST).
 *
 * <p>Вход — исходный текст программы, выход — консольный вывод, полученный
 * после компиляции ({@link RecursiveDescentParser}) и исполнения
 * ({@link VirtualMachine}) байт-кода.</p>
 */
class CompilerParamTest {

    private ByteArrayOutputStream console;
    private PrintStream originalOut;

    @BeforeEach
    void redirectStandardOut() {
        originalOut = System.out;
        console = new ByteArrayOutputStream();
        System.setOut(new PrintStream(console, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStandardOut() {
        System.setOut(originalOut);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("programs")
    @DisplayName("Исходный текст компилируется и даёт ожидаемый консольный вывод")
    void compilesAndPrintsExpectedOutput(String source, String expectedOutput) {
        new VirtualMachine().run(compile(source));

        assertEquals(expectedOutput, actualConsoleOutput(),
                () -> "Несовпадение консольного вывода для исходника:\n" + source);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidPrograms")
    @DisplayName("Некорректный исходный текст приводит к ошибке компиляции")
    void reportsErrorForInvalidSource(String source) {
        assertThrows(RuntimeException.class, () -> compile(source),
                () -> "Ожидалась ошибка компиляции для исходника:\n" + source);
    }

    private Program compile(String source) {
        RecursiveDescentParser parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        return parser.getProgram();
    }

    private String actualConsoleOutput() {
        System.out.flush();
        return console.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /**
     * Тестовые данные: исходный текст и ожидаемый консольный вывод.
     */
    static Stream<Arguments> programs() {
        return Stream.of(
                // Пример из задания: ветка if выполняется, while не выполняется
                // (z = 0, y = 0 -> 0 * 5 < 0 ложно).
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
                        print x;
                        print y;
                        print z;
                        """,
                        "7\n0\n0.0\n"
                ),

                // Выполняется then-ветка if
                Arguments.of(
                        """
                        int x = 1;
                        if x < 5 { x = 7; }
                        print x;
                        """,
                        "7\n"
                ),

                // Выполняется else-ветка if
                Arguments.of(
                        """
                        int x = 10;
                        int y = 0;
                        if x < 5 { x = 7; } else { y = 10; }
                        print x;
                        print y;
                        """,
                        "10\n10\n"
                ),

                // Цикл while выполняется три раза
                Arguments.of(
                        """
                        int i = 0;
                        int sum = 0;
                        while i < 3 {
                            sum = sum + i;
                            i = i + 1;
                        }
                        print sum;
                        print i;
                        """,
                        "3\n3\n"
                ),

                // Вложенный if
                Arguments.of(
                        """
                        int x = 1;
                        int y = 0;
                        if x < 5 {
                            if x < 2 { y = 100; } else { y = 200; }
                        } else { y = 300; }
                        print y;
                        """,
                        "100\n"
                ),

                // Цепочка else-if
                Arguments.of(
                        """
                        int x = 7;
                        int y = 0;
                        if x < 5 { y = 1; } else if x < 10 { y = 2; } else { y = 3; }
                        print y;
                        """,
                        "2\n"
                ),

                // Значения по умолчанию для неинициализированных переменных
                Arguments.of(
                        """
                        int x;
                        float z;
                        print x;
                        print z;
                        """,
                        "0\n0.0\n"
                ),

                // Множественное объявление с инициализаторами и без
                Arguments.of(
                        """
                        int x = 1, y = 2, z;
                        print x;
                        print y;
                        print z;
                        """,
                        "1\n2\n0\n"
                ),

                // Вещественный литерал с экспонентой
                Arguments.of(
                        """
                        float f = 1.5e-1;
                        print f;
                        """,
                        "0.15\n"
                ),

                // while с вещественным результатом
                Arguments.of(
                        """
                        int y = 10;
                        float z;
                        while z * 5 < y {
                            z = y / 5;
                        }
                        print z;
                        """,
                        "2.0\n"
                ),

                // Арифметика, факториал и результат сравнения
                Arguments.of(
                        """
                        int a = 10 - 2 - 3;
                        print a;
                        print 3!;
                        print 3 < 5;
                        """,
                        "5\n6\n1\n"
                ),

                // Операторы сравнения
                Arguments.of(
                        """
                        int a = 5;
                        int b = 5;
                        print a == b;
                        print a <> b;
                        print a >= b;
                        print a <= b;
                        print a > b;
                        print a < b;
                        """,
                        "1\n0\n1\n1\n0\n0\n"
                ),

                // Логические операторы и унарный минус
                Arguments.of(
                        """
                        int a = 5;
                        int b = 0;
                        print a && b;
                        print a || b;
                        print !b;
                        print -a;
                        print +a;
                        """,
                        "0\n1\n1\n-5\n5\n"
                ),

                // Пустая программа не выводит ничего
                Arguments.of("", "")
        );
    }

    /**
     * Некорректные программы: компиляция должна бросать RuntimeException.
     */
    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                // Пропущена ';'
                Arguments.of("int x = 1"),
                // Нет блока после условия if
                Arguments.of("int x = 1; if x < 5 x = 7;"),
                // Нет блока после условия while
                Arguments.of("int x = 1; while x < 5 x = x + 1;"),
                // Незакрытый блок
                Arguments.of("int x = 1; if x < 5 { x = 7;"),
                // Присваивание необъявленной переменной
                Arguments.of("x = 5;"),
                // Печать необъявленной переменной
                Arguments.of("print y;"),
                // Повторное объявление переменной
                Arguments.of("int x = 1; int x = 2;"),
                // 'else' без 'if'
                Arguments.of("else { x = 1; }")
        );
    }
}
