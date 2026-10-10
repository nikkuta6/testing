package by.pranovich.testing.parser;

import java.util.List;

public class RecursiveDescentParser {

    private static final List<String> BINARY_OPERATORS = List.of(
            "<>", "<=", ">=", "==", "&&", "||",
            "+", "-", "*", "/", "<", ">"
    );

    private final String input;
    private final CodeGenerator codegen = new CodeGenerator();
    private int pos = 0;

    public RecursiveDescentParser(String input) {
        this.input = input;
    }

    /**
     * Разбирает выражение и заодно генерирует байт-код.
     *
     * @return true, если весь ввод был разобран без остатка
     */
    public boolean parse() {
        codegen.reset();
        parseExpression();
        if (!isEndOfInput()) {
            return false;
        }
        codegen.emit(Opcode.HALT);
        return true;
    }

    /**
     * Байт-код, сгенерированный последним успешным parse().
     */
    public Program getProgram() {
        return codegen.toProgram();
    }

    // expression := unary { binaryOp unary }
    private void parseExpression() {
        parseUnary();

        String op;
        while ((op = matchBinaryOperator()) != null) {
            parseUnary();
            codegen.emitBinary(op);   // порядок: левый, правый, операция
        }
    }

    // unary := ("+" | "-") operand | "!" (name | "(" expression ")") | operand
    private void parseUnary() {
        skipWhitespace();

        if (isEndOfInput()) {
            throw error("Ожидался операнд, но достигнут конец ввода");
        }

        char current = input.charAt(pos);

        if (current == '+') {
            pos++;
            parseOperand();                 // унарный плюс — no-op
        } else if (current == '-') {
            pos++;
            parseOperand();
            codegen.emit(Opcode.NEG);
        } else if (current == '!' && !isNotEqualsOperator()) {
            pos++;
            skipWhitespace();

            if (isEndOfInput() || !isNameOrGroupStart()) {
                throw error("После '!' ожидалось имя или выражение в скобках, но найдено: "
                        + currentSymbol() + " на позиции " + pos);
            }
            parseOperand();
            codegen.emit(Opcode.NOT);
        } else {
            parseOperand();
        }
    }

    // operand := name | constant | "(" expression ")" { "!" }
    private void parseOperand() {
        skipWhitespace();

        if (isEndOfInput()) {
            throw error("Ожидался операнд, но достигнут конец ввода");
        }

        char current = input.charAt(pos);

        if (current == '(') {
            pos++;
            parseExpression();
            skipWhitespace();
            expectClosingBracket();
        } else if (Character.isLetter(current)) {
            codegen.emitLoad(parseName());
        } else if (Character.isDigit(current)) {
            codegen.emitPush(parseConstant());
        } else {
            throw error("Ожидался операнд (имя, константа или скобка), но найдено: '"
                    + currentSymbol() + "' на позиции " + pos);
        }

        // Постфиксные факториалы: "3!", "3 !", "2!!"
        while (isFactorial()) {
            pos++;
            codegen.emit(Opcode.FACT);
        }
    }

    private String parseName() {
        int start = pos;
        while (!isEndOfInput() && Character.isLetter(input.charAt(pos))) {
            pos++;
        }
        return input.substring(start, pos);
    }

    private long parseConstant() {
        int start = pos;
        while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        return Long.parseLong(input.substring(start, pos));
    }

    private void expectClosingBracket() {
        if (isEndOfInput() || input.charAt(pos) != ')') {
            throw error("Ожидалась закрывающая скобка ')', но найдено: '"
                    + currentSymbol() + "' на позиции " + pos);
        }
        pos++;
    }

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
        return pos >= input.length();
    }

    private String currentSymbol() {
        return isEndOfInput() ? "конец ввода" : input.charAt(pos) + "";
    }

    private RuntimeException error(String message) {
        return new RuntimeException(message + " (позиция " + pos + ")");
    }
}