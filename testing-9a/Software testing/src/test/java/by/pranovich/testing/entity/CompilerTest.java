package by.pranovich.testing.entity;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompilerTest {

    @Test
    public void positiveCompileTest() {
        var compiler = new Compiler("");
        boolean expected = true;
        boolean actual = compiler.compile();
        assertEquals(expected, actual);
    }

    @Test
    public void negativeCompileTest() {
        var compiler = new Compiler("");
        compiler.setSyntaxError(new SyntaxError("something went wrong", 7, 9));
        boolean expected = false;
        boolean actual = compiler.compile();
        assertEquals(expected, actual);
    }
}
