package by.pranovich.testing.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

class RecursiveDescentParserTest {

    // Вспомогательный метод для проверки, что строка разобрана полностью (до конца)
    private void assertFullyParsed(RecursiveDescentParser parser, String expr) throws Exception {
        parser.peek(); // Пропускаем возможные пробелы в конце

        // Получаем приватное поле pos для контроля корректности завершения
        Field posField = RecursiveDescentParser.class.getDeclaredField("pos");
        posField.setAccessible(true);
        int finalPos = (int) posField.get(parser);

        Assertions.assertEquals(expr.length(), finalPos,
                "Выражение не было разобрано до конца: " + expr);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4!",
            "2!!",
            "3 !",
            "2!  !",
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
    void parse_ShouldPassForValidInput(String expression) throws Exception {
        RecursiveDescentParser parser = new RecursiveDescentParser(expression);

        // Проверяем, что метод выполняется без исключений
        Assertions.assertDoesNotThrow(() -> parser.parse());

        // Проверяем, что парсер дошел до конца строки
        assertFullyParsed(parser, expression);
    }

    @ParameterizedTest
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
            "a)",
            "(a + b",
            "a @ b",
            "12a34"
    })
    void parse_ShouldThrowForInvalidInput(String expression) {
        RecursiveDescentParser parser = new RecursiveDescentParser(expression);

        // Проверяем, что синтаксическая ошибка гарантированно вызывает RuntimeException
        Assertions.assertThrows(RuntimeException.class, () -> {
            parser.parse();

            // Если метод не упал, но и до конца не дошел (застрял на ошибке) —
            // имитируем логику проверки конца строки, чтобы выбросить исключение здесь
            parser.peek();
            Field posField = RecursiveDescentParser.class.getDeclaredField("pos");
            posField.setAccessible(true);
            int finalPos = (int) posField.get(parser);

            if (finalPos < expression.length()) {
                throw new RuntimeException("Синтаксический мусор в конце выражения");
            }
        });
    }
}
