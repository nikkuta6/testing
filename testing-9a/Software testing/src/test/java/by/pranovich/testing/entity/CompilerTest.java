package by.pranovich.testing.entity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CompilerTest {
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCompile() {
        Compiler compiler = new Compiler("");
        boolean result = compiler.compile();
        Assert.assertEquals(true, result);
    }
}
