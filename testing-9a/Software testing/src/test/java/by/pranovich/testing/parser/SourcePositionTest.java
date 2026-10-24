package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.Opcode;
import by.pranovich.testing.bytecode.Program;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every instruction remembers the source offset of its statement — the IDE highlights it while stepping. */
class SourcePositionTest {

    @Test
    void mapsInstructionsToStatements() {
        String source = "int i = 0;\nwhile (i < 2) {\n  i = i + 1;\n}\nprint(i);";
        int declaration = 0;
        int loop = source.indexOf("while");
        int body = source.indexOf("i = i");
        int print = source.indexOf("print");

        RecursiveDescentParser parser = new RecursiveDescentParser(source);
        parser.parseProgram();
        Program program = parser.getProgram();

        List<Integer> positions = new ArrayList<>();
        for (int index = 0; index < program.instructions().size(); index++) {
            positions.add(program.sourcePosition(index));
        }
        // PUSH STORE | LOAD PUSH LT JZ | LOAD PUSH ADD STORE | JMP (back to while) | LOAD PRINT PRINT_NEWLINE | HALT
        assertEquals(List.of(declaration, declaration, loop, loop, loop, loop, body, body, body, body, loop,
                print, print, print, -1), positions);
        assertEquals(Opcode.JMP, program.instructions().get(10).opcode());
    }
}
