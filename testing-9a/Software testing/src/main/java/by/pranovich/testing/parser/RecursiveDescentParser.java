package by.pranovich.testing.parser;

import by.pranovich.testing.bytecode.CodeGenerator;
import by.pranovich.testing.bytecode.Opcode;
import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.runtime.VirtualMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    // Signatures are collected before the main pass, so a function can be called above its declaration.
    private final Map<String, Signature> functions = new HashMap<>();
    private final Map<String, Integer> functionAddresses = new HashMap<>();
    private final Map<String, List<Integer>> pendingCalls = new HashMap<>();
    private final Set<String> locals = new LinkedHashSet<>();
    private Signature currentFunction;
    private int functionStart;
    private int nesting;

    private record Signature(String name, String returnType, List<String> parameterNames,
                            List<String> parameterTypes) {
    }

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
        resetFunctions();
        collectFunctionSignatures();

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
        resetFunctions();
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

        // The enclosing statement's position is restored so that a loop's closing JMP maps back to it.
        int enclosingPosition = codegen.sourcePosition();
        codegen.setSourcePosition(pos);
        String word = peekWord();
        switch (word) {
            case "int", "float", "complex" -> parseDeclaration();
            case "if" -> parseIf();
            case "while" -> parseWhile();
            case "print" -> parsePrint();
            case "fun" -> parseFunction();
            case "return" -> parseReturn();
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
        codegen.setSourcePosition(enclosingPosition);
    }

    private void parseDeclaration() {
        String type = readWord();
        do {
            String name = expectIdentifier();
            ensureNameIsFree(name);
            skipWhitespace();

            if (matchSymbol("[")) {
                declare(arrays, name, type);
                boolean explicitSize = !peekSymbol("]");
                if (explicitSize) {
                    requireReal(parseExpression(), "Размер массива");
                }
                expectSymbol("]");

                if (peekAssignmentEquals()) {
                    pos++;
                    int count = parseArrayInitializer(type);
                    codegen.emitArrayInit(name, count, explicitSize, type);
                } else if (explicitSize) {
                    codegen.emitArrayNew(name, type);
                } else {
                    throw error("Для массива без размера ожидался инициализатор '[...]'");
                }
            } else if (peekAssignmentEquals()) {
                pos++;
                if (peekSymbol("[")) {
                    declare(arrays, name, type);
                    int count = parseArrayInitializer(type);
                    codegen.emitArrayInit(name, count, false, type);
                } else {
                    declare(variables, name, type);
                    requireAssignable(type, parseExpression());
                    codegen.emitStore(name, type);
                }
            } else {
                declare(variables, name, type);
                codegen.emitPush(0.0);
                codegen.emitStore(name, type);
            }
            skipWhitespace();
        } while (matchSymbol(","));
        expectSymbol(";");
    }

    private int parseArrayInitializer(String type) {
        expectSymbol("[");
        int count = 0;
        if (!peekSymbol("]")) {
            do {
                requireAssignable(type, parseExpression());
                count++;
            } while (matchSymbol(","));
        }
        expectSymbol("]");
        return count;
    }

    private void parseAssignment() {
        String name = expectIdentifier();
        skipWhitespace();

        if (peekSymbol("(") && !functions.containsKey(name)) {
            throw error("Функция '" + name + "' не объявлена");
        }
        if (functions.containsKey(name) && peekSymbol("(")) {
            // A call statement discards the result.
            if (!parseUserCall(name).equals("void")) {
                codegen.emit(Opcode.POP);
            }
        } else if (matchSymbol("[")) {
            if (declarationsRequired && !arrays.containsKey(name)) {
                throw error("Массив '" + name + "' не объявлен");
            }
            requireReal(parseExpression(), "Индекс массива");
            expectSymbol("]");
            if (!peekAssignmentEquals()) {
                throw error("Ожидался оператор '=' после элемента массива '" + name + "'");
            }
            pos++;
            requireAssignable(arrays.get(name), parseExpression());
            codegen.emitArrayStore(name, arrays.get(name));
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
            requireAssignable(variables.get(name), parseExpression());
            codegen.emitStore(name, variables.get(name));
        }
        expectSymbol(";");
    }

    private void parseIf() {
        readWord();
        requireReal(parseExpression(), "Условие 'if'");

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

        requireReal(parseExpression(), "Условие 'while'");
        int jzIdx = codegen.emitJumpPlaceholder(Opcode.JZ);
        parseBlock();

        codegen.emit(Opcode.JMP, loopStart);
        codegen.patchJump(jzIdx, codegen.size());
    }

    private void parsePrint() {
        readWord();
        expectSymbol("(");
        if (!peekSymbol(")")) {
            do {
                if (!parseArrayPrint()) {
                    String type = parseExpression();
                    codegen.emit(Opcode.PRINT, type == null ? "float" : type);
                }
                if (peekSymbol(",")) {
                    codegen.emit(Opcode.PRINT_SPACE);
                }
            } while (matchSymbol(","));
        }
        expectSymbol(")");
        codegen.emit(Opcode.PRINT_NEWLINE);
        expectSymbol(";");
    }

    /** {@code print(a)} prints the whole array — strings exist only here, the VM stack holds numbers. */
    private boolean parseArrayPrint() {
        int start = pos;
        String name = readWord();
        if (arrays.containsKey(name) && (peekSymbol(",") || peekSymbol(")"))) {
            codegen.emitArrayPrint(name, arrays.get(name));
            return true;
        }
        pos = start;
        return false;
    }

    private void parseBlock() {
        expectSymbol("{");
        nesting++;
        while (true) {
            skipWhitespace();
            if (isEndOfInput()) {
                throw error("Ожидалась '}'");
            }
            if (input.charAt(pos) == '}') {
                pos++;
                nesting--;
                return;
            }
            parseStatement();
        }
    }

    // ==================== Functions ====================

    private void resetFunctions() {
        functions.clear();
        functionAddresses.clear();
        pendingCalls.clear();
        locals.clear();
        currentFunction = null;
        nesting = 0;
    }

    /** Pre-pass over the text: only {@code fun} headers are read, their errors are left to the main pass. */
    private void collectFunctionSignatures() {
        pos = 0;
        try {
            while (true) {
                skipWhitespace();
                if (isEndOfInput()) {
                    break;
                }
                if (!isIdentifierStart(input.charAt(pos))) {
                    pos++;
                    continue;
                }
                int start = pos;
                if (readWord().equals("fun")) {
                    pos = start;
                    try {
                        Signature signature = parseFunctionHeader();
                        functions.putIfAbsent(signature.name(), signature);
                    } catch (ParseException ignored) {
                        // reported by the main pass
                    }
                }
            }
        } catch (ParseException ignored) {
            // an unclosed comment is reported by the main pass
        }
        pos = 0;
    }

    private Signature parseFunctionHeader() {
        readWord();
        String returnType = readWord();
        if (!List.of("int", "float", "complex", "void").contains(returnType)) {
            throw error("Ожидался тип результата функции (int, float, complex или void), но найдено: '"
                    + returnType + "'");
        }
        String name = expectIdentifier();
        expectSymbol("(");
        List<String> names = new ArrayList<>();
        List<String> types = new ArrayList<>();
        if (!peekSymbol(")")) {
            do {
                String type = readWord();
                if (!List.of("int", "float", "complex").contains(type)) {
                    throw error("Ожидался тип параметра (int, float или complex), но найдено: '" + type + "'");
                }
                String parameter = expectIdentifier();
                if (names.contains(parameter)) {
                    throw error("Параметр '" + parameter + "' уже объявлен");
                }
                names.add(parameter);
                types.add(type);
            } while (matchSymbol(","));
        }
        expectSymbol(")");
        return new Signature(name, returnType, List.copyOf(names), List.copyOf(types));
    }

    /**
     * Top-level code jumps over the body. ENTER lists the locals of the call frame; then the
     * arguments are stored into the parameters, the last one being on top of the stack.
     */
    private void parseFunction() {
        if (nesting > 0) {
            throw error("Функцию можно объявить только на верхнем уровне программы");
        }
        int start = pos;
        Signature function = parseFunctionHeader();
        if (functionAddresses.containsKey(function.name())) {
            pos = start;
            throw error("Функция '" + function.name() + "' уже объявлена");
        }

        int skip = codegen.emitJumpPlaceholder(Opcode.JMP);
        int entry = codegen.emitJumpPlaceholder(Opcode.ENTER);
        functionAddresses.put(function.name(), entry);
        for (int call : pendingCalls.getOrDefault(function.name(), List.of())) {
            codegen.patchJump(call, entry);
        }
        pendingCalls.remove(function.name());

        Map<String, String> globalVariables = new HashMap<>(variables);
        Map<String, String> globalArrays = new HashMap<>(arrays);
        currentFunction = function;
        functionStart = entry + 1;
        for (int index = 0; index < function.parameterNames().size(); index++) {
            declare(variables, function.parameterNames().get(index), function.parameterTypes().get(index));
        }
        for (int index = function.parameterNames().size() - 1; index >= 0; index--) {
            codegen.emitStore(function.parameterNames().get(index), function.parameterTypes().get(index));
        }

        parseBlock();
        if (function.returnType().equals("void")) {
            codegen.emit(Opcode.RET, "void");
        } else {
            codegen.emit(Opcode.NO_RETURN, function.name());
        }
        codegen.patchOperand(entry, List.copyOf(locals));
        codegen.patchJump(skip, codegen.size());

        currentFunction = null;
        locals.clear();
        variables.clear();
        variables.putAll(globalVariables);
        arrays.clear();
        arrays.putAll(globalArrays);
    }

    private void parseReturn() {
        readWord();
        if (currentFunction == null) {
            throw error("'return' вне функции");
        }
        String returnType = currentFunction.returnType();
        if (returnType.equals("void") != peekSymbol(";")) {
            throw error(returnType.equals("void")
                    ? "Функция '" + currentFunction.name() + "' объявлена как void и не возвращает значение"
                    : "Функция '" + currentFunction.name() + "' должна вернуть значение " + returnType);
        }
        if (!returnType.equals("void")) {
            requireAssignable(returnType, parseExpression());
        }
        codegen.emit(Opcode.RET, returnType);
        expectSymbol(";");
    }

    /** Arguments follow the assignment rules: int and float widen to complex, int parameters check precision at run time. */
    private String parseUserCall(String name) {
        Signature function = functions.get(name);
        expectSymbol("(");
        int count = 0;
        if (!peekSymbol(")")) {
            do {
                String type = parseExpression();
                if (count < function.parameterTypes().size()) {
                    requireAssignable(function.parameterTypes().get(count), type);
                }
                count++;
            } while (matchSymbol(","));
        }
        if (count != function.parameterTypes().size()) {
            throw error("Функция '" + name + "' принимает аргументов: " + function.parameterTypes().size()
                    + ", передано: " + count);
        }
        expectSymbol(")");

        Integer address = functionAddresses.get(name);
        int call = codegen.emitCallFunction(address == null ? -1 : address);
        if (address == null) {
            pendingCalls.computeIfAbsent(name, key -> new ArrayList<>()).add(call);
        }
        return function.returnType();
    }

    // ==================== Expressions ====================

    private String parseExpression() {
        return parseOr();
    }

    private String parseOr() {
        String type = parseAnd();
        while (matchSymbol("||")) {
            requireReal(type, "Оператор '||'");
            requireReal(parseAnd(), "Оператор '||'");
            codegen.emitBinary("||");
            type = "int";
        }
        return type;
    }

    private String parseAnd() {
        String type = parseEquality();
        while (matchSymbol("&&")) {
            requireReal(type, "Оператор '&&'");
            requireReal(parseEquality(), "Оператор '&&'");
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
            requireReal(type, "Оператор '" + operator + "'");
            requireReal(parseAdditive(), "Оператор '" + operator + "'");
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
            type = numericResultType(type, parseMultiplicative());
            codegen.emitBinary(operator, type);
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
            type = numericResultType(type, parseUnary());
            codegen.emitBinary(operator, type);
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
            codegen.emit(Opcode.NEG, type);
            return type;
        } else if (current == '!' && !isNotEqualsOperator()) {
            pos++;
            skipWhitespace();
            if (isEndOfInput() || !isNameOrGroupStart()) {
                throw error("После '!' ожидалось имя или выражение в скобках, но найдено: '"
                        + currentSymbol() + "'");
            }
            requireReal(parseOperand(), "Оператор '!'");
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
        String type = numericResultType(baseType, parseUnary());
        codegen.emitBinary("^", type);
        return type;
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
                requireReal(parseExpression(), "Индекс массива");
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
            if (isImaginarySuffix()) {
                pos++;
                type = "complex";
                codegen.emitPushImaginary(value);
            } else {
                type = isFloatLiteral(input.substring(start, pos)) ? "float" : "int";
                if (type.equals("int") && value > VirtualMachine.MAX_EXACT_INT) {
                    pos = start;
                    throw error("Переполнение int: литерал больше 2^53 - 1");
                }
                codegen.emitPush(value);
            }
        } else {
            throw error("Ожидался операнд (имя, число или скобка), но найдено: '"
                    + currentSymbol() + "'");
        }

        while (isFactorial()) {
            requireReal(type, "Факториал");
            pos++;
            // int! stays int (range-checked), float! stays float so 19.0! does not overflow int
            codegen.emit(Opcode.FACT, type);
        }
        return type;
    }

    private String parseFunctionCall(String name) {
        if (functions.containsKey(name)) {
            String type = parseUserCall(name);
            if (type.equals("void")) {
                throw error("Функция '" + name + "' объявлена как void и не может быть частью выражения");
            }
            return type;
        }
        expectSymbol("(");
        List<String> argumentTypes = new ArrayList<>();
        if (!peekSymbol(")")) {
            do {
                argumentTypes.add(parseExpression());
            } while (matchSymbol(","));
        }
        expectSymbol(")");

        validateFunction(name, argumentTypes.size());
        if (!List.of("re", "im", "abs", "conj", "arg").contains(name)) {
            argumentTypes.forEach(type -> requireReal(type, "Функция '" + name + "'"));
        }
        codegen.emitCall(name, argumentTypes.size());

        if (name.equals("max") || name.equals("min")) {
            return argumentTypes.stream().anyMatch("float"::equals) ? "float" : "int";
        }
        if (name.equals("abs") && !"complex".equals(argumentTypes.getFirst())) {
            return argumentTypes.getFirst();
        }
        return name.equals("conj") ? "complex" : "float";
    }

    private void validateFunction(String name, int argumentCount) {
        switch (name) {
            case "sin", "cos", "cod", "tg", "ctg", "abs", "re", "im", "conj", "arg" -> {
                if (argumentCount != 1) {
                    throw error("Функция '" + name + "' принимает ровно один аргумент");
                }
            }
            case "max", "min" -> {
                if (argumentCount < 2) {
                    throw error("Функция '" + name + "' принимает не менее двух аргументов");
                }
            }
            case "_pi", "_e" -> {
                if (argumentCount != 0) {
                    throw error("Функция '" + name + "' не принимает аргументов");
                }
            }
            default -> throw error("Неизвестная встроенная функция '" + name + "'");
        }
    }

    private String numericResultType(String left, String right) {
        if ("complex".equals(left) || "complex".equals(right)) {
            return "complex";
        }
        return "float".equals(left) || "float".equals(right) ? "float" : "int";
    }

    /** complex has no order and no truth value, so it is rejected where a real number is required. */
    private void requireReal(String type, String context) {
        if ("complex".equals(type)) {
            throw error(context + ": значение complex недопустимо");
        }
    }

    /** int and float widen to complex, but complex never narrows to int or float. */
    private void requireAssignable(String target, String valueType) {
        if ("complex".equals(valueType) && !"complex".equals(target) && target != null) {
            throw error("Значение complex нельзя сохранить в " + target);
        }
    }

    /** {@code 4i} is an imaginary literal; {@code 4 i} or {@code 4if} are not, so a variable {@code i} stays usable. */
    private boolean isImaginarySuffix() {
        return pos < input.length() && input.charAt(pos) == 'i'
                && (pos + 1 >= input.length() || !isIdentifierPart(input.charAt(pos + 1)));
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

        String literal = input.substring(start, pos);
        double value;
        try {
            value = Double.parseDouble(literal);
        } catch (NumberFormatException e) {
            pos = start;
            throw error("Некорректное число: " + literal);
        }
        if (Double.isInfinite(value)) {
            pos = start;
            throw error("Переполнение: число " + literal + " слишком велико");
        }
        return value;
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

    /** Inside a function only locals count: a local may shadow a global of the same name. */
    private void ensureNameIsFree(String name) {
        if (functions.containsKey(name)) {
            throw error("Имя '" + name + "' уже объявлено как функция");
        }
        if (currentFunction == null ? variables.containsKey(name) || arrays.containsKey(name)
                : locals.contains(name)) {
            throw error("Имя '" + name + "' уже объявлено");
        }
        // The VM resolves a name in a frame by the list of locals, so an earlier global use would change meaning.
        if (currentFunction != null && codegen.usesName(name, functionStart)) {
            throw error("Имя '" + name + "' уже использовано в функции как глобальное");
        }
    }

    private void declare(Map<String, String> names, String name, String type) {
        if (currentFunction != null) {
            locals.add(name);
            variables.remove(name);
            arrays.remove(name);
        }
        names.put(name, type);
    }

    private boolean isKeyword(String word) {
        return switch (word) {
            case "int", "float", "complex", "void", "if", "else", "while", "print", "fun", "return",
                    "sin", "cos", "cod", "tg", "ctg", "abs", "max", "min", "_pi", "_e",
                    "re", "im", "conj", "arg" -> true;
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

    // Комментарии (// до конца строки и /* ... */) пропускаются как пробелы.
    private void skipWhitespace() {
        while (pos < input.length()) {
            if (Character.isWhitespace(input.charAt(pos))) {
                pos++;
            } else if (input.startsWith("//", pos)) {
                int end = input.indexOf('\n', pos);
                pos = end < 0 ? input.length() : end + 1;
            } else if (input.startsWith("/*", pos)) {
                int end = input.indexOf("*/", pos + 2);
                if (end < 0) throw error("Незакрытый комментарий '/*'");
                pos = end + 2;
            } else {
                return;
            }
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
