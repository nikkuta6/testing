package by.pranovich.testing.entity;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CompilerTest {
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCompile() {
        Compiler compiler = new Compiler("");
        boolean expected = true;
        boolean actual = compiler.compile();
        assertEquals(expected, actual);
    }
}
