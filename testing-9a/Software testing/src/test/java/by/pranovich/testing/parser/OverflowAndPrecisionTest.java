package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverflowAndPrecisionTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("runtimeErrors")
    void reportsOverflowAndPrecisionLoss(String source, String expectedMessage) {
        Program program = compile(source);
        ArithmeticException error = assertThrows(ArithmeticException.class,
                () -> new VirtualMachine(new PrintStream(new ByteArrayOutputStream())).run(program));
        assertTrue(error.getMessage().contains(expectedMessage), error.getMessage());
    }

    static Stream<Arguments> runtimeErrors() {
        return Stream.of(
                Arguments.of("int x = 2 ^ 100; print(x);", "Переполнение int"),
                Arguments.of("int x = 2 ^ 53 + 2;", "Переполнение int"),
                Arguments.of("int a[1]; a[0] = 2 ^ 60;", "Переполнение int"),
                Arguments.of("int a = [1, 2 ^ 60];", "Переполнение int"),
                Arguments.of("print(2 ^ 60);", "Переполнение int"),
                Arguments.of("int x = 9007199254740991; x = x + 1; print(x);", "Переполнение int"),
                Arguments.of("int x = 2 ^ 53;", "Переполнение int"),
                Arguments.of("print((2 ^ 60 + 1) - 2 ^ 60);", "Переполнение int"),
                Arguments.of("int y = (2 ^ 53 + 1) - 2 ^ 53;", "Переполнение int"),
                Arguments.of("float f = 2 ^ 100;", "Переполнение int"),
                Arguments.of("int x = 9007199254740991; print(-x - 1);", "Переполнение int"),
                Arguments.of("float f = 19!;", "Переполнение int"),
                Arguments.of("float f = 0 ^ -1;", "Деление на ноль"),
                Arguments.of("float f = 0.0 ^ -0.5;", "Деление на ноль"),
                Arguments.of("float f = 10 ^ 400;", "Переполнение"),
                Arguments.of("float f = 1e300 * 1e300;", "Переполнение"),
                Arguments.of("float f = -1e308 - 1e308;", "Переполнение"),
                Arguments.of("float f = 1e308 + 1e308;", "Переполнение"),
                Arguments.of("float f = 171!;", "Переполнение"),
                Arguments.of("int n = 1000; float f = n!;", "Переполнение"),
                Arguments.of("float f = 1e300 / 1e-300;", "Переполнение"),
                Arguments.of("float f = (-8) ^ 0.5;", "Недопустимая операция"),
                Arguments.of("int x = 7 / 0;", "Деление на ноль"),
                Arguments.of("float f = 0 / 0;", "Деление на ноль"),
                Arguments.of("float f = ctg(0);", "Деление на ноль"),
                Arguments.of("int a = 2.5;", "Потеря точности"),
                Arguments.of("int a = 7 / 2;", "Потеря точности"),
                Arguments.of("int a; a = 1 / 3;", "Потеря точности"),
                Arguments.of("int a[2]; a[1] = 0.5;", "Потеря точности"),
                Arguments.of("int a[] = [1, 1.5];", "Потеря точности"),
                Arguments.of("print(2 ^ -1);", "Потеря точности"),
                Arguments.of("print(2.5!);", "Факториал определён только для целых неотрицательных"),
                Arguments.of("print((-3)!);", "Факториал определён только для целых неотрицательных"),
                Arguments.of("int n = -1; float f = n!;", "Факториал определён только для целых неотрицательных")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validPrograms")
    void keepsExactValues(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("int x = 9007199254740991; print(x);", "9007199254740991\n"),
                Arguments.of("int x = -9007199254740991; print(x);", "-9007199254740991\n"),
                Arguments.of("int x = 9007199254740990; x = x + 1; print(x);", "9007199254740991\n"),
                Arguments.of("print(18!);", "6402373705728000\n"),
                Arguments.of("float f = 7 / 2 * 2; print(f);", "7.0\n"),
                Arguments.of("print(0 == -0, -0 == 0i, -0.0, 0 * -1.5);", "1 1 0.0 0.0\n"),
                Arguments.of("float f = -0.0; print(f, f == 0);", "0.0 1\n"),
                Arguments.of("int a = 6 / 2; print(a);", "3\n"),
                Arguments.of("int a = 2.0; print(a);", "2\n"),
                Arguments.of("float f = 7 / 2; print(f);", "3.5\n"),
                Arguments.of("float f = 1 / 3; print(f);", String.valueOf(1.0 / 3) + "\n"),
                Arguments.of("float f = 2.0 ^ 100; print(f);", Math.pow(2, 100) + "\n"),
                Arguments.of("int a = [2 ^ 10, 4 / 2]; print(a);", "[1024, 2]\n"),
                Arguments.of("print(0!, 1!, 5!, 3.0!);", "1 1 120 6.0\n"),
                Arguments.of("float f = 19.0!; print(f);", "1.21645100408832E17\n"),
                Arguments.of("int i = 9; print((2 * i + 1.0)!);", "1.21645100408832E17\n")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("tooLargeLiterals")
    void rejectsInfiniteLiteralsAtCompileTime(String source) {
        ParseException error = assertThrows(ParseException.class, () -> compile(source));
        assertTrue(error.getMessage().contains("Переполнение"), error.getMessage());
    }

    static Stream<String> tooLargeLiterals() {
        return Stream.of("float f = 1e400;", "print(2e999);",
                "print(9007199254740992);", "print(9007199254740993);", "int x = -9007199254740993;");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("malformedNumbers")
    void rejectsMalformedNumbersAtTheirPosition(String source, String message, int position) {
        ParseException error = assertThrows(ParseException.class, () -> compile(source));
        assertTrue(error.getMessage().contains(message), error.getMessage());
        assertEquals(position, error.position());
    }

    static Stream<Arguments> malformedNumbers() {
        return Stream.of(
                Arguments.of("print(2e);", "Некорректное число", 6),
                Arguments.of("print(4ei);", "Некорректное число", 6),
                Arguments.of("print(1 + 2E+);", "Некорректное число", 10),
                Arguments.of("print(1 + 9007199254740993);", "Переполнение int", 10)
        );
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
