package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.CodeGenerator;
import by.pranovich.testing.bytecode.Opcode;
import by.pranovich.testing.bytecode.Program;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One-pass recursive-descent parser. It validates source code and emits
 * stack-machine instructions directly, without building an AST.
 */
public final class RecursiveDescentParser {

    private final String input;
    private final CodeGenerator codegen = new CodeGenerator();
    private final Map<String, String> variables = new HashMap<>();
    private final Map<String, String> arrays = new HashMap<>();
    private boolean declarationsRequired = false;
    private int pos = 0;

    public RecursiveDescentParser(String input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    /**
     * Parse a sequence of statements and append HALT.
     */
    public boolean parseProgram() {
        codegen.reset();
        variables.clear();
        arrays.clear();
        declarationsRequired = true;
        pos = 0;

        while (!isEndOfInput()) {
            parseStatement();
        }
        codegen.emit(Opcode.HALT);
        return true;
    }

    /**
     * Parse one expression. Kept as the original expression-test contract.
     */
    public boolean parse() {
        codegen.reset();
        variables.clear();
        arrays.clear();
        declarationsRequired = false;
        pos = 0;

        parseExpression();
        if (!isEndOfInput()) return false;
        codegen.emit(Opcode.HALT);
        return true;
    }

    public Program getProgram() {
        return codegen.toProgram();
    }

    // ==================== Statements ====================

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
        String type = readWord();
        do {
            String name = expectIdentifier();
            ensureNameIsFree(name);
            skipWhitespace();

            if (matchSymbol("[")) {
                arrays.put(name, type);
                boolean explicitSize = !peekSymbol("]");
                if (explicitSize) {
                    parseExpression();
                }
                expectSymbol("]");

                if (peekAssignmentEquals()) {
                    pos++;
                    int count = parseArrayInitializer();
                    codegen.emitArrayInit(name, count, explicitSize);
                } else if (explicitSize) {
                    codegen.emitArrayNew(name);
                } else {
                    throw error("Для массива без размера ожидался инициализатор '[...]'");
                }
            } else if (peekAssignmentEquals()) {
                pos++;
                if (peekSymbol("[")) {
                    arrays.put(name, type);
                    int count = parseArrayInitializer();
                    codegen.emitArrayInit(name, count, false);
                } else {
                    variables.put(name, type);
                    parseExpression();
                    codegen.emitStore(name);
                }
            } else {
                variables.put(name, type);
                codegen.emitPush(0.0);
                codegen.emitStore(name);
            }
            skipWhitespace();
        } while (matchSymbol(","));
        expectSymbol(";");
    }

    private int parseArrayInitializer() {
        expectSymbol("[");
        int count = 0;
        if (!peekSymbol("]")) {
            do {
                parseExpression();
                count++;
            } while (matchSymbol(","));
        }
        expectSymbol("]");
        return count;
    }

    private void parseAssignment() {
        String name = expectIdentifier();
        skipWhitespace();

        if (matchSymbol("[")) {
            if (declarationsRequired && !arrays.containsKey(name)) {
                throw error("Массив '" + name + "' не объявлен");
            }
            parseExpression();
            expectSymbol("]");
            if (!peekAssignmentEquals()) {
                throw error("Ожидался оператор '=' после элемента массива '" + name + "'");
            }
            pos++;
            parseExpression();
            codegen.emitArrayStore(name);
        } else {
            if (declarationsRequired && !variables.containsKey(name)) {
                if (arrays.containsKey(name)) {
                    throw error("Для присваивания элементу массива '" + name + "' нужен индекс");
                }
                throw error("Переменная '" + name + "' не объявлена");
            }
            if (!peekAssignmentEquals()) {
                throw error("Ожидался оператор '=' после '" + name + "'");
            }
            pos++;
            parseExpression();
            codegen.emitStore(name);
        }
        expectSymbol(";");
    }

    private void parseIf() {
        readWord();
        parseExpression();

        int jzIdx = codegen.emitJumpPlaceholder(Opcode.JZ);
        parseBlock();

        skipWhitespace();
        if (matchKeyword("else")) {
            int jmpIdx = codegen.emitJumpPlaceholder(Opcode.JMP);
            codegen.patchJump(jzIdx, codegen.size());

            skipWhitespace();
            if (!isEndOfInput() && input.charAt(pos) == '{') {
                parseBlock();
            } else if (peekWord().equals("if")) {
                parseIf();
            } else {
                throw error("После 'else' ожидался блок '{' или 'if'");
            }
            codegen.patchJump(jmpIdx, codegen.size());
        } else {
            codegen.patchJump(jzIdx, codegen.size());
        }
    }

    private void parseWhile() {
        readWord();
        int loopStart = codegen.size();

        parseExpression();
        int jzIdx = codegen.emitJumpPlaceholder(Opcode.JZ);
        parseBlock();

        codegen.emit(Opcode.JMP, loopStart);
        codegen.patchJump(jzIdx, codegen.size());
    }

    private void parsePrint() {
        readWord();
        if (!parseArrayPrint()) {
            String type = parseExpression();
            codegen.emit(Opcode.PRINT, type == null ? "float" : type);
        }
        expectSymbol(";");
    }

    /** {@code print a;} is shorthand for {@code print a.toString();}. */
    private boolean parseArrayPrint() {
        int start = pos;
        String name = readWord();
        if (!arrays.containsKey(name)) {
            pos = start;
            return false;
        }

        if (matchSymbol(".")) {
            String method = readWord();
            if (!method.equals("toString")) {
                throw error("Неизвестный метод массива '" + method + "'");
            }
            expectSymbol("(");
            expectSymbol(")");
        } else if (!peekSymbol(";")) {
            pos = start;
            return false;
        }

        codegen.emitArrayPrint(name, arrays.get(name));
        return true;
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

    // ==================== Expressions ====================

    private String parseExpression() {
        return parseOr();
    }

    private String parseOr() {
        String type = parseAnd();
        while (matchSymbol("||")) {
            parseAnd();
            codegen.emitBinary("||");
            type = "int";
        }
        return type;
    }

    private String parseAnd() {
        String type = parseEquality();
        while (matchSymbol("&&")) {
            parseEquality();
            codegen.emitBinary("&&");
            type = "int";
        }
        return type;
    }

    private String parseEquality() {
        String type = parseArrayComparison() ? "int" : parseComparison();
        while (true) {
            String operator;
            if (matchSymbol("==")) {
                operator = "==";
            } else if (matchSymbol("<>")) {
                operator = "<>";
            } else {
                break;
            }
            parseComparison();
            codegen.emitBinary(operator);
            type = "int";
        }
        return type;
    }

    /** Whole-array {@code a == b} / {@code a <> b}: equal lengths and equal elements. */
    private boolean parseArrayComparison() {
        int start = pos;
        String left = readWord();
        String operator = !arrays.containsKey(left) ? null
                : matchSymbol("==") ? "=="
                : matchSymbol("<>") ? "<>"
                : null;
        if (operator == null) {
            pos = start;
            return false;
        }

        String right = readWord();
        if (!arrays.containsKey(right) || peekSymbol("[")) {
            throw error("Массив '" + left + "' можно сравнивать только с массивом");
        }
        codegen.emitArrayEquals(left, right);
        if (operator.equals("<>")) {
            codegen.emit(Opcode.NOT);
        }
        return true;
    }

    private String parseComparison() {
        String type = parseAdditive();
        while (true) {
            String operator;
            if (matchSymbol("<=")) {
                operator = "<=";
            } else if (matchSymbol(">=")) {
                operator = ">=";
            } else if (peekSymbol("<>")) {
                break;
            } else if (matchSymbol("<")) {
                operator = "<";
            } else if (matchSymbol(">")) {
                operator = ">";
            } else {
                break;
            }
            parseAdditive();
            codegen.emitBinary(operator);
            type = "int";
        }
        return type;
    }

    private String parseAdditive() {
        String type = parseMultiplicative();
        while (true) {
            String operator;
            if (matchSymbol("+")) {
                operator = "+";
            } else if (matchSymbol("-")) {
                operator = "-";
            } else {
                break;
            }
            String right = parseMultiplicative();
            codegen.emitBinary(operator);
            type = numericResultType(type, right);
        }
        return type;
    }

    private String parseMultiplicative() {
        String type = parseUnary();
        while (true) {
            String operator;
            if (matchSymbol("*")) {
                operator = "*";
            } else if (matchSymbol("/")) {
                operator = "/";
            } else {
                break;
            }
            String right = parseUnary();
            codegen.emitBinary(operator);
            type = numericResultType(type, right);
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
            return parsePower();
        } else if (current == '-') {
            pos++;
            String type = parsePower();
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
            return parsePowerTail("int");
        }
        return parsePower();
    }

    /**
     * Exponentiation is right-associative and binds tighter than unary +/-.
     * The exponent may itself be a unary expression, so {@code 2 ^ -1} is valid.
     */
    private String parsePower() {
        return parsePowerTail(parseOperand());
    }

    private String parsePowerTail(String baseType) {
        if (!matchSymbol("^")) {
            return baseType;
        }
        String exponentType = parseUnary();
        codegen.emitBinary("^");
        return numericResultType(baseType, exponentType);
    }

    private String parseOperand() {
        skipWhitespace();
        if (isEndOfInput()) {
            throw error("Ожидался операнд, но достигнут конец ввода");
        }
        char current = input.charAt(pos);
        String type;

        if (current == '(') {
            pos++;
            type = parseExpression();
            expectSymbol(")");
        } else if (isIdentifierStart(current)) {
            String name = readWord();
            if (peekSymbol("(")) {
                type = parseFunctionCall(name);
            } else if (matchSymbol("[")) {
                if (declarationsRequired && !arrays.containsKey(name)) {
                    throw error("Массив '" + name + "' не объявлен");
                }
                type = arrays.get(name);
                parseExpression();
                expectSymbol("]");
                codegen.emitArrayLoad(name);
            } else {
                if (declarationsRequired && !variables.containsKey(name)) {
                    if (arrays.containsKey(name)) {
                        throw error("Для чтения элемента массива '" + name + "' нужен индекс");
                    }
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

        while (isFactorial()) {
            pos++;
            codegen.emit(Opcode.FACT);
            type = "int";
        }
        return type;
    }

    private String parseFunctionCall(String name) {
        expectSymbol("(");
        List<String> argumentTypes = new ArrayList<>();
        if (!peekSymbol(")")) {
            do {
                argumentTypes.add(parseExpression());
            } while (matchSymbol(","));
        }
        expectSymbol(")");

        validateFunction(name, argumentTypes.size());
        codegen.emitCall(name, argumentTypes.size());

        if (name.equals("max") || name.equals("min")) {
            return argumentTypes.stream().anyMatch("float"::equals) ? "float" : "int";
        }
        if (name.equals("abs")) {
            return argumentTypes.getFirst();
        }
        return "float";
    }

    private void validateFunction(String name, int argumentCount) {
        switch (name) {
            case "sin", "cos", "cod", "tg", "ctg", "abs" -> {
                if (argumentCount != 1) {
                    throw error("Функция '" + name + "' принимает ровно один аргумент");
                }
            }
            case "max", "min" -> {
                if (argumentCount < 2) {
                    throw error("Функция '" + name + "' принимает не менее двух аргументов");
                }
            }
            default -> throw error("Неизвестная встроенная функция '" + name + "'");
        }
    }

    private String numericResultType(String left, String right) {
        return "float".equals(left) || "float".equals(right) ? "float" : "int";
    }

    private boolean isFloatLiteral(String literal) {
        return literal.indexOf('.') >= 0
                || literal.indexOf('e') >= 0
                || literal.indexOf('E') >= 0;
    }

    private double parseNumber() {
        int start = pos;
        while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) pos++;

        if (!isEndOfInput() && input.charAt(pos) == '.') {
            pos++;
            while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) pos++;
        }

        if (!isEndOfInput() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            pos++;
            if (!isEndOfInput() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
            while (!isEndOfInput() && Character.isDigit(input.charAt(pos))) pos++;
        }

        return Double.parseDouble(input.substring(start, pos));
    }

    // ==================== Helpers ====================

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

    private void ensureNameIsFree(String name) {
        if (variables.containsKey(name) || arrays.containsKey(name)) {
            throw error("Имя '" + name + "' уже объявлено");
        }
    }

    private boolean isKeyword(String word) {
        return switch (word) {
            case "int", "float", "if", "else", "while", "print",
                 "sin", "cos", "cod", "tg", "ctg", "abs", "max", "min" -> true;
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

    private boolean peekAssignmentEquals() {
        skipWhitespace();
        if (pos >= input.length() || input.charAt(pos) != '=') return false;
        return pos + 1 >= input.length() || input.charAt(pos + 1) != '=';
    }

    private boolean peekSymbol(String symbol) {
        skipWhitespace();
        return input.startsWith(symbol, pos);
    }

    private boolean matchSymbol(String symbol) {
        if (peekSymbol(symbol)) {
            pos += symbol.length();
            return true;
        }
        return false;
    }

    private void expectSymbol(String symbol) {
        if (!matchSymbol(symbol)) {
            throw error("Ожидался символ '" + symbol + "', но найдено: '"
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
        return pos >= input.length();
    }

    private String currentSymbol() {
        return isEndOfInput() ? "конец ввода" : input.charAt(pos) + "";
    }

    private ParseException error(String message) {
        return new ParseException(message, pos);
    }
}
