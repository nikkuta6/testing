package by.pranovich.testing.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Накопитель инструкций учебного стекового байт-кода.
 * Парсер вызывает методы emit* по ходу разбора.
 */
public class CodeGenerator {

    private final List<Instruction> instructions = new ArrayList<>();

    public void emit(Opcode opcode) {
        instructions.add(Instruction.of(opcode));
    }

    public void emit(Opcode opcode, Object operand) {
        instructions.add(Instruction.of(opcode, operand));
    }

    public void emitPush(long value) {
        emit(Opcode.PUSH, value);
    }

    public void emitLoad(String name) {
        emit(Opcode.LOAD, name);
    }

    public void emitBinary(String operator) {
        emit(opcodeFor(operator));
    }

    /**
     * Очистить буфер перед повторным разбором.
     */
    public void reset() {
        instructions.clear();
    }

    /**
     * Снимок текущего буфера в виде неизменяемой программы.
     */
    public Program toProgram() {
        return new Program(instructions);
    }

    private Opcode opcodeFor(String op) {
        return switch (op) {
            case "+" -> Opcode.ADD;
            case "-" -> Opcode.SUB;
            case "*" -> Opcode.MUL;
            case "/" -> Opcode.DIV;
            case "<" -> Opcode.LT;
            case ">" -> Opcode.GT;
            case "<=" -> Opcode.LE;
            case ">=" -> Opcode.GE;
            case "==" -> Opcode.EQ;
            case "<>" -> Opcode.NE;
            case "&&" -> Opcode.AND;
            case "||" -> Opcode.OR;
            default -> throw new IllegalArgumentException("Неизвестный бинарный оператор: " + op);
        };
    }
}