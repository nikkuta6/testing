package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Opcode;
import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

@Timeout(10)
class GoroutineTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validPrograms")
    void runsGoroutines(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                // the run waits for the goroutine, so its output is there
                Arguments.of("fun void hi(int x) { print(x); } go hi(5);", "5\n"),
                // arguments are evaluated in the caller before the goroutine starts
                Arguments.of("int x = 1; fun void hi(int v) { print(v); } go hi(x); x = 2;", "1\n"),
                // the result of a non-void function is dropped
                Arguments.of("fun int f() { print(3); return 3; } go f();", "3\n"),
                // the goroutine has its own call frames
                Arguments.of("fun int fact(int n) { if n <= 1 { return 1; } return n * fact(n - 1); }"
                        + " fun void p(int n) { print(fact(n)); } go p(10);", "3628800\n"),
                // a goroutine may start a goroutine
                Arguments.of("fun void inner() { print(2); } fun void outer() { go inner(); } go outer();", "2\n"),
                // a function declared below the go statement
                Arguments.of("go later(1, 2); fun void later(int a, float b) { print(a, b); }", "1 2.0\n"),
                // globals are shared: main waits until every goroutine has written its own variable
                Arguments.of("int a = 0, b = 0, c = 0;"
                        + " fun void setA(int v) { a = v; } fun void setB(int v) { b = v; } fun void setC(int v) { c = v; }"
                        + " go setA(1); go setB(2); go setC(3);"
                        + " while a * b * c == 0 { } print(a, b, c);", "1 2 3\n"),
                // sync makes the read-modify-write of the shared counter atomic: no update is lost
                Arguments.of("int c = 0, done1 = 0, done2 = 0;"
                        + " fun void p(int id) { int i = 0; while i < 20000 { sync { c = c + 1; } i = i + 1; }"
                        + " if id == 1 { done1 = 1; } else { done2 = 1; } }"
                        + " go p(1); go p(2); while done1 + done2 < 2 { } print(c);", "40000\n"),
                // nested blocks take the same lock again
                Arguments.of("int x = 0; sync { x = 1; sync { x = x + 1; } } print(x);", "2\n"),
                Arguments.of("fun void f() { sync { print(1); } } sync { f(); }", "1\n")
        );
    }

    /** Would hang if goroutines ran after the main code or in the same thread: each waits for the other. */
    @Test
    void goroutineRunsConcurrentlyWithMainCode() {
        String source = "int ping = 0, pong = 0;"
                + " fun void partner() { while ping == 0 { } pong = 1; }"
                + " go partner(); ping = 1; while pong == 0 { } print(pong);";
        assertEquals("1\n", execute(source));
    }

    @Test
    void reportsGoroutineFailure() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> execute("fun void bad() { print(1 / 0); } go bad();"));
        assertTrue(error.getMessage().contains("Деление на ноль"), error.getMessage());
    }

    /** The failed goroutine releases the lock, so the other one still gets in and the run ends. */
    @Test
    void releasesLockOfFailedGoroutine() {
        RuntimeException error = assertThrows(RuntimeException.class, () -> execute(
                "fun void bad() { sync { print(1 / 0); } } fun void good() { sync { print(7); } } go bad(); go good();"));
        assertTrue(error.getMessage().contains("Деление на ноль"), error.getMessage());
    }

    @Test
    void emitsLockAroundSyncBlock() {
        Program program = compile("int x = 0; sync { x = 1; }");
        var opcodes = program.instructions().stream().map(i -> i.opcode()).toList();
        assertTrue(opcodes.indexOf(Opcode.LOCK) < opcodes.lastIndexOf(Opcode.STORE), program.disassemble());
        assertTrue(opcodes.lastIndexOf(Opcode.STORE) < opcodes.indexOf(Opcode.UNLOCK), program.disassemble());
    }

    @Test
    void emitsGoBeforeCall() {
        Program program = compile("fun void f(int a, int b) { } go f(1, 2);");
        int go = program.instructions().stream().map(i -> i.opcode()).toList().indexOf(Opcode.GO);
        assertEquals(2, program.instructions().get(go).operand());
        assertEquals(Opcode.CALL_FUN, program.instructions().get(go + 1).opcode());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("compileErrors")
    void reportsCompileErrors(String source, String expectedMessage) {
        ParseException error = assertThrows(ParseException.class, () -> compile(source));
        assertTrue(error.getMessage().contains(expectedMessage), error.getMessage());
    }

    static Stream<Arguments> compileErrors() {
        return Stream.of(
                Arguments.of("go 1;", "Ожидался идентификатор"),
                Arguments.of("int x = 1; go x;", "После 'go' ожидался вызов объявленной функции"),
                Arguments.of("go g();", "После 'go' ожидался вызов объявленной функции"),
                Arguments.of("fun void f() { } go f;", "После 'go' ожидался вызов объявленной функции"),
                Arguments.of("fun void f() { } go f()", "Ожидался символ ';'"),
                Arguments.of("fun void f(int a) { } go f();", "принимает аргументов: 1, передано: 0"),
                Arguments.of("int go = 1;", "Ожидался идентификатор"),
                Arguments.of("fun void f() { sync { return; } }", "'return' внутри блока sync запрещён"),
                Arguments.of("fun int f() { sync { if 1 { return 1; } } return 0; }",
                        "'return' внутри блока sync запрещён"),
                Arguments.of("int x = 0; sync x = 1;", "Ожидался символ '{'"),
                Arguments.of("int sync = 1;", "Ожидался идентификатор")
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
