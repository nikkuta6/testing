package by.pranovich.testing.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecursiveDescentParser {

    private static final List<String> BINARY_OPERATORS = List.of(
            "<=", ">=", "==", "<>", "&&", "||",
            "+", "-", "*", "/", "<", ">"
    );

    private final String input;
    private final CodeGenerator codegen = new CodeGenerator();
    private final Map<String, String> variables = new HashMap<>(); // имя -> тип
    private boolean strictMode = false;
    private int pos = 0;

    public RecursiveDescentParser(String input) {
        this.input = input;
    }

    // ==================== Публичные точки входа ====================

    /**
     * Разбор программы: последовательность инструкций, в конце HALT.
     */
    public boolean parseProgram() {
        codegen.reset();
        variables.clear();
        strictMode = true;

        while (!isEndOfInput()) {
            parseStatement();
        }
        codegen.emit(Opcode.HALT);
        return true;
    }

    /**
     * Разбор одиночного выражения — старый контракт для тестов.
     */
    public boolean parse() {
        codegen.reset();
        variables.clear();
        strictMode = false;

        parseExpression();
        if (!isEndOfInput()) return false;
        codegen.emit(Opcode.HALT);
        return true;
    }

    public Program getProgram() {
        return codegen.toProgram();
    }

    // ==================== Инструкции ====================

    private void parseStatement() {
        skipWhitespace();
        if (isEndOfInput()) return;

        String word = peekWord();
        switch (word) {
            case "int", "float" -> parseDeclaration();
            case "if" -> parseIf();
            case "while" -> parseWhile();
            case "print" -> parsePrint();
            case "else" -> throw error("'else' без предшествующего 'if'");
            default -> {
                char c = input.charAt(pos);
                if (c == '{') {
                    parseBlock();
                } else if (isIdentifierStart(c)) {
                    parseAssignment();
                } else {
                    throw error("Ожидалось начало инструкции, но найдено: '"
                            + currentSymbol() + "'");
                }
            }
        }
    }

    private void parseDeclaration() {
        String type = readWord(); // "int" или "float"
        do {
            skipWhitespace();
            String name = expectIdentifier();
            if (variables.containsKey(name)) {
                throw error("Переменная '" + name + "' уже объявлена");
            }
            variables.put(name, type);

            if (peekAssignmentEquals()) {
                pos++; // съедаем '='
                parseExpression();
                codegen.emitStore(name);
            } else {
                // без инициализатора — по умолчанию 0
                codegen.emitPush(0.0);
                codegen.emitStore(name);
            }
            skipWhitespace();
        } while (matchSymbol(","));
        expectSymbol(";");
    }

    private void parseAssignment() {
        String name = expectIdentifier();
        if (strictMode && !variables.containsKey(name)) {
            throw error("Переменная '" + name + "' не объявлена");
        }
        if (!peekAssignmentEquals()) {
            throw error("Ожидался оператор '=' после '" + name + "'");
        }
        pos++; // съедаем '='
        parseExpression();
        codegen.emitStore(name);
        expectSymbol(";");
    }

    private void parseIf() {
        readWord(); // "if"
        parseExpression();

        int jzIdx = codegen.emitJumpPlaceholder(Opcode.JZ);
        parseBlock(); // then-ветка

        skipWhitespace();
        if (matchKeyword("else")) {
            int jmpIdx = codegen.emitJumpPlaceholder(Opcode.JMP);
            codegen.patch(jzIdx, codegen.size()); // JZ -> начало else

            skipWhitespace();
            if (!isEndOfInput() && input.charAt(pos) == '{') {
                parseBlock();
            } else if (peekWord().equals("if")) {
                parseIf(); // else-if цепочка
            } else {
                throw error("После 'else' ожидался блок '{' или 'if'");
            }
            codegen.patch(jmpIdx, codegen.size()); // JMP -> после else
        } else {
            codegen.patch(jzIdx, codegen.size());  // JZ -> после then
        }
    }

    private void parseWhile() {
        readWord(); // "while"
        int loopStart = codegen.size();

        parseExpression();
        int jzIdx = codegen.emitJumpPlaceholder(Opcode.JZ);

        parseBlock();

        codegen.emit(Opcode.JMP, loopStart);
        codegen.patch(jzIdx, codegen.size());
    }

    private void parsePrint() {
        readWord(); // "print"
        String type = parseExpression();
        codegen.emit(Opcode.PRINT, type == null ? "float" : type);
        expectSymbol(";");
    }

    private void parseBlock() {
        expectSymbol("{");
        while (true) {
            skipWhitespace();
            if (isEndOfInput()) {
                throw error("Ожидалась '}'");
            }
            if (input.charAt(pos) == '}') {
                pos++;
                return;
            }
            parseStatement();
        }
    }

    // ==================== Выражения ====================

    private String parseExpression() {
        String type = parseUnary();
        String op;
        while ((op = matchBinaryOperator()) != null) {
            String right = parseUnary();
            codegen.emitBinary(op);
            type = resultType(op, type, right);
        }
        return type;
    }

    private String parseUnary() {
        skipWhitespace();
        if (isEndOfInput()) {
            throw error("Ожидался операнд, но достигнут конец ввода");
        }
        char current = input.charAt(pos);

        if (current == '+') {
            pos++;
            return parseOperand();
        } else if (current == '-') {
            pos++;
            String type = parseOperand();
            codegen.emit(Opcode.NEG);
            return type;
        } else if (current == '!' && !isNotEqualsOperator()) {
            pos++;
            skipWhitespace();
            if (isEndOfInput() || !isNameOrGroupStart()) {
                throw error("После '!' ожидалось имя или выражение в скобках, но найдено: '"
                        + currentSymbol() + "'");
            }
            parseOperand();
            codegen.emit(Opcode.NOT);
            return "int";
        } else {
            return parseOperand();
        }
    }

    private String parseOperand() {
        skipWhitespace();
        if (isEndOfInput()) {
            throw error("Ожидался операнд, но достигнут конец ввода");
        }
        char current = input.charAt(pos);
        String type = null;

        if (current == '(') {
            pos++;
            type = parseExpression();
            skipWhitespace();
            expectSymbol(")");
        } else if (isIdentifierStart(current)) {
            String name = readWord();
            skipWhitespace();
            if (current == '(') {
                parseFunctionCall();
            } else {
                if (strictMode && !variables.containsKey(name)) {
                    throw error("Переменная '" + name + "' не объявлена");
                }
                type = variables.get(name);
                codegen.emitLoad(name);
            }
        } else if (isNumberStart()) {
            int start = pos;
            double value = parseNumber();
            type = isFloatLiteral(input.substring(start, pos)) ? "float" : "int";
            codegen.emitPush(value);
        } else {
            throw error("Ожидался операнд (имя, число или скобка), но найдено: '"
                    + currentSymbol() + "'");
        }

        // постфиксные факториалы
        while (isFactorial()) {
            pos++;
            codegen.emit(Opcode.FACT);
            type = "int";
        }
        return type;
    }

public void parseFunctionCall() {
        expectSymbol("(");
        skipWhitespace();
        if (peekSymbol(")")) {
            pos++;
            return;
        }
        do {
            parseExpression();
            skipWhitespace();
        } while (matchSymbol(","));
        expectSymbol(")");
    }

/**
 * Статический тип результата бинарной операции. Сравнения и логические
 * операции дают int, остальные — float, если хотя бы один операнд float.
 * Значение null трактуется как int (режим разбора одиночного выражения).
 */
private String resultType(String operator, String left, String right) {
    return switch (operator) {
        case "<", ">", "<=", ">=", "==", "<>", "&&", "||" -> "int";
        default -> "float".equals(left) || "float".equals(right) ? "float" : "int";
    };
}

private boolean isFloatLiteral(String literal) {
    return literal.indexOf('.') >= 0
            || literal.indexOf('e') >= 0
            || literal.indexOf('E') >= 0;
}

private double parseNumber() {
    int start = pos;

    // целая часть (может отсутствовать, если начинается с '.')
    while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) pos++;

    // дробная часть
    if (!isEndOfInput() && input.charAt(pos) == '.') {
        pos++;
        while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) pos++;
    }

    // экспонента: 1.5e-1, 2E5, 7e+2
    if (!isEndOfInput() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
        pos++;
        if (!isEndOfInput() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
        while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) pos++;
    }

    return Double.parseDouble(input.substring(start, pos));
}

// ==================== Служебное ====================

private String readWord() {
    skipWhitespace();
    int start = pos;
    if (pos < input.length() && isIdentifierStart(input.charAt(pos))) {
        pos++;
        while (pos < input.length() && isIdentifierPart(input.charAt(pos))) pos++;
    }
    return input.substring(start, pos);
}

private String peekWord() {
    skipWhitespace();
    int start = pos;
    int end = start;
    if (end < input.length() && isIdentifierStart(input.charAt(end))) {
        end++;
        while (end < input.length() && isIdentifierPart(input.charAt(end))) end++;
    }
    return input.substring(start, end);
}

private String expectIdentifier() {
    String word = readWord();
    if (word.isEmpty() || isKeyword(word)) {
        throw error("Ожидался идентификатор, но найдено: '" + word + "'");
    }
    return word;
}

private boolean isKeyword(String w) {
    return switch (w) {
        case "int", "float", "if", "else", "while", "print" -> true;
        default -> false;
    };
}

private boolean isIdentifierStart(char c) {
    return Character.isLetter(c) || c == '_';
}

private boolean isIdentifierPart(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
}

private boolean isNumberStart() {
    if (isEndOfInput()) return false;
    char c = input.charAt(pos);
    if (Character.isDigit(c)) return true;
    return c == '.' && pos + 1 < input.length()
            && Character.isDigit(input.charAt(pos + 1));
}

/**
 * '=' — но не '=='.
 */
private boolean peekAssignmentEquals() {
    skipWhitespace();
    if (pos >= input.length() || input.charAt(pos) != '=') return false;
    return pos + 1 >= input.length() || input.charAt(pos + 1) != '=';
}

private boolean peekSymbol(String s) {
    skipWhitespace();
    return input.startsWith(s, pos);
}

private boolean matchSymbol(String s) {
    if (peekSymbol(s)) {
        pos += s.length();
        return true;
    }
    return false;
}

private void expectSymbol(String s) {
    if (!matchSymbol(s)) {
        throw error("Ожидался символ '" + s + "', но найдено: '"
                + currentSymbol() + "'");
    }
}

private boolean matchKeyword(String keyword) {
    skipWhitespace();
    if (!input.startsWith(keyword, pos)) return false;
    int end = pos + keyword.length();
    if (end < input.length() && isIdentifierPart(input.charAt(end))) return false;
    pos = end;
    return true;
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
    char c = input.charAt(pos);
    return c == '(' || isIdentifierStart(c);
}

private void skipWhitespace() {
    while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
        pos++;
    }
}

private boolean isEndOfInput() {
//  !!!!!!!!!!!      skipWhitespace();
    return pos >= input.length();
}

private String currentSymbol() {
    return isEndOfInput() ? "конец ввода" : input.charAt(pos) + "";
}

private RuntimeException error(String message) {
    return new RuntimeException(message + " (позиция " + pos + ")");
}
}