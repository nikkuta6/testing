package by.pranovich.testing.parser;

public class RecursiveDescentParser {
    private final String input;
    private int pos = 0;

    public RecursiveDescentParser(String input) {
        this.input = input; // Теперь строка сохраняется в исходном виде с пробелами
    }

    // Вспомогательный метод: пропускаем пробелы, табы и переносы строк
    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    // Вспомогательный метод: получаем текущий символ (предварительно пропустив пробелы)
    public char peek() {
        skipWhitespace();
        if (pos >= input.length()) {
            return '\0'; // Конец строки
        }
        return input.charAt(pos);
    }

    // Вспомогательный метод: сдвигаем указатель вперед
    private void consume() {
        pos++;
    }

    public boolean parse() {
        parseExpression();
        return peek() == '\0';
    }

    /**
     * Выражение := [+-] операнд {бинарная операция операнд}
     */
    public void parseExpression() {
        // [+-] — опциональный унарный знак
        char current = peek();
        if (current == '+' || current == '-') {
            consume();
        }

        // Обязательный первый операнд
        parseOperand();

        // После parseOperand() надо проверить факториал
        parseFactorial();

        // {бинарная операция операнд} — ноль или более повторений
        while (isBinaryOperator(peek())) {
            consume(); // считываем бинарную операцию
            parseOperand(); // считываем следующий операнд
        }
    }

    /**
     * Операнд := имя | константа | "(" выражение ")"
     */
    private void parseOperand() {
        char current = peek();

        if (current == '(') {
            consume(); // считываем '('
            parseExpression(); // рекурсивный вызов для вложенного выражения

            if (peek() != ')') {
                throw new RuntimeException("Ожидалась закрывающая скобка ')', но найдено: '" + peek() + "' на позиции " + pos);
            }
            consume(); // считываем ')'
        } else if (Character.isLetter(current)) {
            parseName();
        } else if (Character.isDigit(current)) {
            parseConstant();
        } else {
            throw new RuntimeException("Ожидался операнд (имя, константа или скобка), но найдено: '" + current + "' на позиции " + pos);
        }
    }

    // имя := буква {буква}
    private void parseName() {
        // Метод peek() уже пропустил пробелы, проверяем первый символ имени
        if (!Character.isLetter(peek())) {
            throw new RuntimeException("Ожидалось имя на позиции " + pos);
        }
        // Внутри самого имени (между буквами) пробелов быть не должно,
        // поэтому здесь проверяем напрямую через input.charAt, не вызывая peek() с пропуском пробелов
        while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            consume();
        }
    }

    // константа := цифра {цифра}
    private void parseConstant() {
        if (!Character.isDigit(peek())) {
            throw new RuntimeException("Ожидалась константа на позиции " + pos);
        }
        // Внутри числа пробелов быть не должно
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            consume();
        }
    }

    private void parseFactorial() {
        while (peek() == '!') {
            consume();
        }
    }

    // Проверка, является ли символ бинарной операцией
    private boolean isBinaryOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }
}

