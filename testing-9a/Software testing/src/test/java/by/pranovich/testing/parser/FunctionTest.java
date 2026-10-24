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

class FunctionTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validPrograms")
    void runsFunctions(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("fun int add(int a, int b) { return a + b; } print(add(2, 3));", "5\n"),
                Arguments.of("print(add(2, 3)); fun int add(int a, int b) { return a + b; }", "5\n"),
                Arguments.of("fun int fact(int n) { if n <= 1 { return 1; } return n * fact(n - 1); }"
                        + " print(fact(10));", "3628800\n"),
                Arguments.of("fun int fib(int n) { if n < 2 { return n; } return fib(n - 1) + fib(n - 2); }"
                        + " print(fib(15));", "610\n"),
                // mutual recursion, the second function is called before its declaration
                Arguments.of("fun int even(int n) { if n == 0 { return 1; } return odd(n - 1); }"
                        + " fun int odd(int n) { if n == 0 { return 0; } return even(n - 1); }"
                        + " print(even(10), odd(7));", "1 1\n"),
                Arguments.of("fun complex sq(complex z) { return z * z; } print(sq(1i), sq(2));",
                        "-1.0+0.0i 4.0+0.0i\n"),
                Arguments.of("fun float half(float x) { return x / 2; } print(half(3));", "1.5\n"),
                Arguments.of("fun void show(float x) { print(x); return; } show(1.5); show(2);", "1.5\n2.0\n"),
                Arguments.of("fun void hello() { print(1); } hello();", "1\n"),
                Arguments.of("fun int f(int a) { print(a); return a; } f(7);", "7\n"),
                // locals are per call, globals declared above are visible and writable
                Arguments.of("int x = 10; fun int f() { int x = 1; return x; } print(f(), x);", "1 10\n"),
                Arguments.of("int x = 10; fun int f(int x) { return x * 2; } print(f(3), x);", "6 10\n"),
                Arguments.of("int count = 0; fun void inc() { count = count + 1; } inc(); inc(); print(count);",
                        "2\n"),
                Arguments.of("int g[2]; fun void put(int i, int v) { g[i] = v; } put(1, 5); print(g);",
                        "[0, 5]\n"),
                Arguments.of("int a[] = [1, 2]; fun int f() { int a[3]; a[2] = 9; return a[2]; } print(f(), a);",
                        "9 [1, 2]\n"),
                Arguments.of("fun int depth(int n) { int local = n; if n > 0 { depth(n - 1); } return local; }"
                        + " print(depth(5));", "5\n"),
                Arguments.of("fun int sum(int n) { if n == 0 { return 0; } return n + sum(n - 1); }"
                        + " print(sum(5000));", "12502500\n"),
                Arguments.of("fun int f() { int i = 0; while i < 5 { i = i + 1; if i == 3 { return i; } }"
                        + " return -1; } print(f());", "3\n"),
                Arguments.of("fun int one() { return 1; } print(one() + one() * 2);", "3\n"),
                Arguments.of("int g = 5; fun int f() { return g; } print(f()); g = 6; print(f());", "5\n6\n"),
                Arguments.of("fun int id(int x) { return x; } int r = id(2.0); print(r);", "2\n"),
                Arguments.of("/* fun int ghost() */ fun int f() { return 4; } // fun\nprint(f());", "4\n")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("compileErrors")
    void rejectsInvalidFunctions(String source, String expectedMessage) {
        ParseException error = assertThrows(ParseException.class, () -> compile(source));
        assertTrue(error.getMessage().contains(expectedMessage), error.getMessage());
    }

    static Stream<Arguments> compileErrors() {
        return Stream.of(
                Arguments.of("return 1;", "'return' вне функции"),
                Arguments.of("if 1 { fun int f() { return 1; } }", "только на верхнем уровне"),
                Arguments.of("fun int f() { fun int g() { return 1; } return 1; }", "только на верхнем уровне"),
                Arguments.of("fun int f() { return 1; } fun int f() { return 2; }", "уже объявлена"),
                Arguments.of("fun int sin(int x) { return x; }", "Ожидался идентификатор"),
                Arguments.of("fun int while() { return 1; }", "Ожидался идентификатор"),
                Arguments.of("int fun = 1;", "Ожидался идентификатор"),
                Arguments.of("fun int f() { return 1; } int f = 2;", "уже объявлено как функция"),
                Arguments.of("fun string f() { return 1; }", "Ожидался тип результата функции"),
                Arguments.of("fun int f(int a[]) { return 1; }", "Ожидался символ ')'"),
                Arguments.of("fun int f(void a) { return 1; }", "Ожидался тип параметра"),
                Arguments.of("fun int f(int a, float a) { return 1; }", "Параметр 'a' уже объявлен"),
                Arguments.of("fun int f(int a) { int a = 1; return a; }", "уже объявлено"),
                Arguments.of("fun void f() { return 1; }", "объявлена как void"),
                Arguments.of("fun int f() { return; }", "должна вернуть значение int"),
                Arguments.of("fun int f() { return 1i; }", "Значение complex нельзя сохранить в int"),
                Arguments.of("fun void f() { } print(f());", "не может быть частью выражения"),
                Arguments.of("fun void f() { } int x = f() + 1;", "не может быть частью выражения"),
                Arguments.of("fun int f(int a) { return a; } print(f());", "принимает аргументов: 1, передано: 0"),
                Arguments.of("fun int f(int a) { return a; } print(f(1, 2));", "принимает аргументов: 1, передано: 2"),
                Arguments.of("fun float f(float a) { return a; } print(f(1i));", "Значение complex нельзя сохранить в float"),
                Arguments.of("fun complex f(complex z) { return z; } int x = f(1);", "Значение complex нельзя сохранить в int"),
                Arguments.of("fun int f() { int x = 1; return x; } print(x);", "Переменная 'x' не объявлена"),
                Arguments.of("fun int f() { return y; } int y = 1;", "Переменная 'y' не объявлена"),
                Arguments.of("int x = 1; fun int f() { print(x); int x = 2; return x; }",
                        "уже использовано в функции как глобальное"),
                Arguments.of("print(g(1));", "Неизвестная встроенная функция 'g'"),
                Arguments.of("g();", "Функция 'g' не объявлена"),
                Arguments.of("int g = 1; g(2);", "Функция 'g' не объявлена"),
                Arguments.of("fun int f() { return 1; ", "Ожидалась '}'")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("runtimeErrors")
    void reportsRuntimeErrors(String source, String expectedMessage) {
        Program program = compile(source);
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new VirtualMachine(new PrintStream(new ByteArrayOutputStream())).run(program));
        assertTrue(error.getMessage().contains(expectedMessage), error.getMessage());
    }

    static Stream<Arguments> runtimeErrors() {
        return Stream.of(
                Arguments.of("fun int f(int n) { if n > 0 { return 1; } } print(f(0));",
                        "Функция 'f' завершилась без return"),
                Arguments.of("fun int f(int n) { return f(n + 1); } print(f(0));", "Переполнение стека вызовов"),
                Arguments.of("fun void f() { f(); } f();", "Переполнение стека вызовов"),
                Arguments.of("fun int f(int x) { return x; } print(f(2.5));", "Потеря точности"),
                Arguments.of("fun int f(float x) { return x; } print(f(2.5));", "Потеря точности"),
                Arguments.of("fun int big(int n) { return 2 ^ n; } print(big(60));", "Переполнение int"),
                Arguments.of("fun float f(float x) { return x * 1e300; } print(f(1e300));", "Переполнение"),
                Arguments.of("fun int f(int n) { if n == 0 { return 7 / 0; } return f(n - 1); } print(f(3));",
                        "Деление на ноль"),
                Arguments.of("fun int f(int i) { int a[2]; return a[i]; } print(f(2));", "вне границ массива"),
                // the global is above the function in the text, but the top level has not reached it yet
                Arguments.of("print(f()); int g = 5; fun int f() { return g; }",
                        "Переменная 'g' ещё не инициализирована"),
                Arguments.of("print(f()); int a = [5]; fun int f() { return a[0]; }", "Массив 'a' ещё не создан"),
                Arguments.of("fun int f(int n) { if n > 0 { int x = 1; } return x; } print(f(0));",
                        "Переменная 'x' ещё не инициализирована")
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
