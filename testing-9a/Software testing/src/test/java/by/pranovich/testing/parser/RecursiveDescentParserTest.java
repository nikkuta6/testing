package by.pranovich.testing.parser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecursiveDescentParserTest {

    @ParameterizedTest(name = "Parsing of {0} should return true")
    @ValueSource(strings = {
            "4!",
            "2!!",
            "3 !",
            "2!  !",
            "2! + 3!",
            "1! *  4!",
            "(1+3)!",
            "abc",
            "123",
            "-123",
            "+abc",
            "  -  xyz",
            "x + y",
            "10 - 5 * 2 / a",
            "-a + b - c",
            "(a)",
            "-(10 + abc)",
            "((x))",
            "(a + b) * (c - d)",
            "   -   42   ",
            "  (  a  +  b  )  *  2  "
    })
    void parseShouldPassForValidInput(String expression) throws Exception {
        var parser = new RecursiveDescentParser(expression);

        assertTrue(parser.parse());
    }


    @ParameterizedTest(name = "Parsing {0} should return false")
    @ValueSource(strings = {
            "a)",
            "a @ b",
            "12a34"
    })
    void parseShouldReturnFalseForInvalidInput(String expression) {
        var parser = new RecursiveDescentParser(expression);

        assertFalse(parser.parse());
    }


    @ParameterizedTest(name = "Parsing {0} should throw RuntimeException")
    @ValueSource(strings = {
            "-!3",
            "!",
            "!2",
            "!!1",
            "1 + !2",
            "+",
            "a +",
            "a + * b",
            "(a",
            "(a + b"
    })
    void parseShouldThrowRuntimeExceptionForInvalidInput(String expression) {
        var parser = new RecursiveDescentParser(expression);

        assertThrows(RuntimeException.class, parser::parse);
    }
}
