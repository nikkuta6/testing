package by.pranovich.testing.parser;

public class RecursiveDescentParser {

    private final String input;
    private int pos = 0;

    public RecursiveDescentParser(String input) {
        this.input = input;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            consume();
        }
    }

    private char peek() {
        skipWhitespace();
        if (pos >= input.length()) {
            return '\0'; // конец строки
        }
        return input.charAt(pos);
    }

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
    private void parseExpression() {
        char current = peek();
        if (current == '+' || current == '-') {
            consume();
        }

        parseOperand();

        // {бинарная операция операнд} — ноль или более повторений
        while (isBinaryOperator(peek())) {
            consume();
            parseOperand();
        }
    }

    /**
     * Операнд := имя | константа | "(" выражение ")" {"!"}
     */
    private void parseOperand() {
        char current = peek();

        if (current == '(') {
            consume();
            parseExpression(); // рекурсивный вызов для вложенного выражения

            if (peek() != ')') {
                throw new RuntimeException("Ожидалась закрывающая скобка ')', но найдено: '" + peek() + "' на позиции " + pos);
            }
            consume();
        } else if (Character.isLetter(current)) {
            parseName();
        } else if (Character.isDigit(current)) {
            parseConstant();
        } else {
            throw new RuntimeException("Ожидался операнд (имя, константа или скобка), но найдено: '" + current + "' на позиции " + pos);
        }

        parseFactorial();
    }

    // имя := буква {буква}
    private void parseName() {
        if (!Character.isLetter(peek())) {
            throw new RuntimeException("Ожидалось имя на позиции " + pos);
        }
        // Внутри самого имени (между буквами) пробелов быть не должно
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