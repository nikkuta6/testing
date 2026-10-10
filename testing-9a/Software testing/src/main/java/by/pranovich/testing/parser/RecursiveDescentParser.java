package by.pranovich.testing.parser;

import java.util.List;

public class RecursiveDescentParser {

    // Двухсимвольные операции проверяются раньше односимвольных
    private static final List<String> BINARY_OPERATORS = List.of(
            "<=", ">=", "==", "!=", "&&", "||",
            "+", "-", "*", "/", "<", ">"
    );

    private final String input;
    private int pos = 0;

    public RecursiveDescentParser(String input) {
        this.input = input;
    }

    /**
     * Разбирает всё выражение.
     *
     * @return true, если весь ввод был разобран без остатка,
     * false — если после выражения остались лишние символы
     */
    public boolean parse() {
        parseExpression();
        return isEndOfInput();
    }

    /**
     * Выражение := Унарное {бинарная операция Унарное}
     */
    private void parseExpression() {
        parseUnary();

        while (matchBinaryOperator() != null) {
            parseUnary();
        }
    }

    /**
     * Унарное := ["+" | "-" | "!"] операнд
     *
     * Префикс "!" допустим только перед именем или выражением в скобках,
     * поэтому "!2" и "!!a" недопустимы.
     */
    private void parseUnary() {
        skipWhitespace();

        if (isEndOfInput()) {
            throw error("Ожидался операнд, но достигнут конец ввода");
        }

        char current = input.charAt(pos);

        if (current == '+' || current == '-') {
            pos++;
            parseOperand();
        } else if (current == '!' && !isNotEqualsOperator()) {
            pos++;
            skipWhitespace();

            if (isEndOfInput() || !isNameOrGroupStart()) {
                throw error("После '!' ожидалось имя или выражение в скобках, но найдено: "
                        + currentSymbol() + " на позиции " + pos);
            }
            parseOperand();
        } else {
            parseOperand();
        }
    }

    /**
     * Операнд := имя | константа | "(" выражение ")"
     */
    private void parseOperand() {
        skipWhitespace();

        if (isEndOfInput()) {
            throw error("Ожидался операнд, но достигнут конец ввода");
        }

        char current = input.charAt(pos);

        if (current == '(') {
            pos++;
            parseExpression(); // рекурсивный вызов для вложенного выражения
            skipWhitespace();
            expectClosingBracket();
        } else if (Character.isLetter(current)) {
            parseName();
        } else if (Character.isDigit(current)) {
            parseConstant();
        } else {
            throw error("Ожидался операнд (имя, константа или скобка), но найдено: '"
                    + currentSymbol() + "' на позиции " + pos);
        }

        parseFactorial();
    }

    // имя := буква {буква}
    private void parseName() {
        // Внутри самого имени (между буквами) пробелов быть не должно
        while (!isEndOfInput() && Character.isLetter(input.charAt(pos))) {
            pos++;
        }
    }

    // константа := цифра {цифра}
    private void parseConstant() {
        // Внутри самого числа (между цифрами) пробелов быть не должно
        while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
    }

    // факториал := {"!"}, пробелы между скобками допустимы: "3 !"
    private void parseFactorial() {
        while (isFactorial()) {
            pos++;
        }
    }

    private void expectClosingBracket() {
        if (isEndOfInput() || input.charAt(pos) != ')') {
            throw error("Ожидалась закрывающая скобка ')', но найдено: '"
                    + currentSymbol() + "' на позиции " + pos);
        }
        pos++;
    }

    /**
     * Съедает бинарную операцию, если она есть в текущей позиции.
     *
     * @return текст операции или null, если операции нет
     */
    private String matchBinaryOperator() {
        skipWhitespace();

        for (String operator : BINARY_OPERATORS) {
            if (input.startsWith(operator, pos)) {
                pos += operator.length();
                return operator;
            }
        }
        return null;
    }

    // "!" перед операндом является факториалом, "!=" и "!==" — операциями
    private boolean isFactorial() {
        skipWhitespace();
        return !isEndOfInput() && input.charAt(pos) == '!' && !isNotEqualsOperator();
    }

    private boolean isNotEqualsOperator() {
        return input.startsWith("!=", pos) && !input.startsWith("!==", pos);
    }

    private boolean isNameOrGroupStart() {
        char current = input.charAt(pos);
        return current == '(' || Character.isLetter(current);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean isEndOfInput() {
        skipWhitespace();
        return pos >= input.length();
    }

    private String currentSymbol() {
        return isEndOfInput() ? "конец ввода" : input.charAt(pos) + "";
    }

    private RuntimeException error(String message) {
        return new RuntimeException(message + " (позиция " + pos + ")");
    }
}