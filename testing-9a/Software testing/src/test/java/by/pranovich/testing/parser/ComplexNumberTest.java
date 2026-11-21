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

class ComplexNumberTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validPrograms")
    void computesWithComplexNumbers(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("print(3 + 4i);", "3.0+4.0i\n"),
                Arguments.of("print(1 - 2i);", "1.0-2.0i\n"),
                Arguments.of("print(conj(2i));", "0.0-2.0i\n"),
                Arguments.of("print(2.5i, 1i);", "0.0+2.5i 0.0+1.0i\n"),
                Arguments.of("complex z = 3 + 4i; print(z, abs(z), re(z), im(z));", "3.0+4.0i 5.0 3.0 4.0\n"),
                Arguments.of("complex z; print(z);", "0.0+0.0i\n"),
                Arguments.of("complex z = 5; print(z);", "5.0+0.0i\n"),
                Arguments.of("complex z = 2.5; z = 1; print(z);", "1.0+0.0i\n"),
                Arguments.of("print(-(1 + 2i));", "-1.0-2.0i\n"),
                Arguments.of("print((1 + 2i) * (3 - 1i));", "5.0+5.0i\n"),
                Arguments.of("print((5 + 5i) / (3 - 1i));", "1.0+2.0i\n"),
                Arguments.of("print(4i / 2);", "0.0+2.0i\n"),
                Arguments.of("complex z = 1e200 + 1e200i; print(z / z);", "1.0+0.0i\n"),
                Arguments.of("print(1 / (1e-200 + 1e-200i));", "5.0E199-5.0E199i\n"),
                Arguments.of("print((1e-300 + 2e-300i) / (1e-300 + 2e-300i), 1e300i / 1e10i);", "1.0+0.0i 1.0E290+0.0i\n"),
                Arguments.of("print((2 + 0i) ^ -2000, (0.5 + 0i) ^ -3);", "0.0+0.0i 8.0+0.0i\n"),
                Arguments.of("print(1i ^ 2, 1i ^ 3, 1i ^ -1);", "-1.0+0.0i 0.0-1.0i 0.0-1.0i\n"),
                Arguments.of("print(abs((-4 + 0i) ^ 0.5 - 2i) < 1e-12);", "1\n"),
                Arguments.of("print(abs(2 ^ 1i - (cos(0.6931471805599453) + 1i * sin(0.6931471805599453))) < 1e-12);", "1\n"),
                Arguments.of("print(arg(1i) == _pi() / 2, arg(-1));", "1 " + Math.PI + "\n"),
                Arguments.of("print(3 + 0i == 3, 1i == 1, 1i <> 1, conj(3) == 3);", "1 0 1 1\n"),
                Arguments.of("float r = re(1 + 2i) + im(1 + 2i); print(r);", "3.0\n"),
                Arguments.of("print(abs(-3), abs(-2.5));", "3 2.5\n"),
                Arguments.of("complex a[2]; a[1] = 2i; print(a);", "[0.0+0.0i, 0.0+2.0i]\n"),
                Arguments.of("complex b = [1i, 2 + 0i]; print(b, b[0] * b[0]);",
                        "[0.0+1.0i, 2.0+0.0i] -1.0+0.0i\n"),
                Arguments.of("complex b[3] = [1]; print(b);", "[1.0+0.0i, 0.0+0.0i, 0.0+0.0i]\n"),
                Arguments.of("complex a = [1, 2]; int b = [1, 2]; print(a == b, a <> b);", "1 0\n"),
                // i stays an ordinary variable when it does not touch a number
                Arguments.of("int i = 0; complex s = 0; while i < 3 { s = s + i * 1i; i = i + 1; } print(s, i);",
                        "0.0+3.0i 3\n"),
                Arguments.of("int i = 2; print(3 * i, 3*i, 2i);", "6 6 0.0+2.0i\n")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("compileErrors")
    void rejectsInvalidComplexUsage(String source, String expectedMessage) {
        ParseException error = assertThrows(ParseException.class, () -> compile(source));
        assertTrue(error.getMessage().contains(expectedMessage), error.getMessage());
    }

    static Stream<Arguments> compileErrors() {
        return Stream.of(
                Arguments.of("int x = 1i;", "Значение complex нельзя сохранить в int"),
                Arguments.of("float x; x = 1 + 1i;", "Значение complex нельзя сохранить в float"),
                Arguments.of("float a[2]; a[0] = 1i;", "нельзя сохранить в float"),
                Arguments.of("int a = [1, 2i];", "нельзя сохранить в int"),
                Arguments.of("print(1i < 2);", "Оператор '<'"),
                Arguments.of("print(1 >= 2i);", "Оператор '>='"),
                Arguments.of("print(1i && 1);", "Оператор '&&'"),
                Arguments.of("print(1 || 1i);", "Оператор '||'"),
                Arguments.of("print(!(1i));", "Оператор '!'"),
                Arguments.of("if 1i { print(1); }", "Условие 'if'"),
                Arguments.of("complex z = 1; while z { }", "Условие 'while'"),
                Arguments.of("complex z = 3; print(z!);", "Факториал"),
                Arguments.of("print(sin(1i));", "Функция 'sin'"),
                Arguments.of("print(max(1, 2i));", "Функция 'max'"),
                Arguments.of("int a[3]; print(a[1i]);", "Индекс массива"),
                Arguments.of("complex a[2i];", "Размер массива"),
                Arguments.of("int re = 1;", "Ожидался идентификатор"),
                Arguments.of("int x = 3if;", "Ожидался символ ';'")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("runtimeErrors")
    void reportsComplexArithmeticErrors(String source, String expectedMessage) {
        Program program = compile(source);
        ArithmeticException error = assertThrows(ArithmeticException.class,
                () -> new VirtualMachine(new PrintStream(new ByteArrayOutputStream())).run(program));
        assertTrue(error.getMessage().contains(expectedMessage), error.getMessage());
    }

    static Stream<Arguments> runtimeErrors() {
        return Stream.of(
                Arguments.of("print(1i / 0);", "Деление на ноль"),
                Arguments.of("print((1 + 1i) / (0 + 0i));", "Деление на ноль"),
                Arguments.of("print((0 + 0i) ^ -1);", "Деление на ноль"),
                Arguments.of("print(1e308i * 10);", "Переполнение"),
                Arguments.of("print(1e308 + 1e308i + 1e308);", "Переполнение"),
                Arguments.of("print((2 + 0i) ^ 2000);", "Переполнение"),
                Arguments.of("print(1e300i / 1e-10i);", "Переполнение"),
                Arguments.of("print((0.5 + 0i) ^ -2000);", "Переполнение"),
                Arguments.of("print(abs(1.5e308 + 1.5e308i));", "Переполнение")
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
