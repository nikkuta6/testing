package by.pranovich.testing.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerTest {

    @Test
    void positiveCompileTest() {
        Compiler compiler = new Compiler("");

        assertTrue(compiler.compile());
        assertNotNull(compiler.getProgram());
    }

    @Test
    void negativeCompileTest() {
        Compiler compiler = new Compiler("");
        compiler.setSyntaxError(new SyntaxError("something went wrong", 7, 9));

        assertFalse(compiler.compile());
    }

    @Test
    void capturesParserErrorWithSourceLocation() {
        Compiler compiler = new Compiler("int x = 1;\nprint(missing);");

        assertFalse(compiler.compile());
        assertNotNull(compiler.getSyntaxError());
        assertEquals(2, compiler.getSyntaxError().getLineNumber());
    }
}
