package by.pranovich.testing.entity;


import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompilerTest {

    @Test
    public void testCompile() {
        Compiler compiler = new Compiler("");
        boolean expected = true;
        boolean actual = compiler.compile();
        assertEquals(expected, actual);
    }
}
