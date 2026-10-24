package by.pranovich.testing.parser;

import by.pranovich.testing.runtime.VirtualMachine;
import org.junit.jupiter.api.Test;
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

/** Line ({@code //}) and block ({@code /* *\/}) comments are skipped like whitespace. */
class CommentTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("programs")
    void ignoresComments(String source, String expectedOutput) {
        assertEquals(expectedOutput, execute(source));
    }

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("// только комментарий", ""),
                Arguments.of("int x = 1; // конец строки\nprint(x);", "1\n"),
                Arguments.of("int x = 1; print(x); // без перевода строки в конце", "1\n"),
                Arguments.of("int /* тип */ x /* имя */ = /* значение */ 2; print(x);", "2\n"),
                Arguments.of("/* много\nстрок\n*/ int x = 3; print(x);", "3\n"),
                Arguments.of("int x = 8 / /* делитель */ 2; print(x); // 4", "4\n"),
                Arguments.of("int x = 6 /2; print(x);", "3\n"),
                Arguments.of("int a = [1, /* второй */ 2]; if a[1] == 2 { // ветка\n print(a); }", "[1, 2]\n"),
                Arguments.of("/* // внутри блока */ int x = 5; print(x);", "5\n"),
                Arguments.of("// /* внутри строки\nint x = 7; print(x);", "7\n")
        );
    }

    @Test
    void reportsUnclosedBlockComment() {
        ParseException e = assertThrows(ParseException.class,
                () -> new RecursiveDescentParser("int x = 1; /* не закрыт").parseProgram());
        assertTrue(e.getMessage().contains("Незакрытый комментарий"));
    }

    @Test
    void expressionModeSkipsComments() {
        assertTrue(new RecursiveDescentParser("1 + /* два */ 2 // хвост").parse());
    }

    private String execute(String source) {
        RecursiveDescentParser parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            new VirtualMachine(out).run(parser.getProgram());
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
