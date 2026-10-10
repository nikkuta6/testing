package by.pranovich.testing.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Накопитель инструкций учебного стекового байт-кода.
 * Парсер вызывает методы emit* по ходу разбора и патчит переходы через patch().
 */
public class CodeGenerator {

    private final List<Instruction> instructions = new ArrayList<>();

    public int size() {
        return instructions.size();
    }

    public void emit(Opcode opcode) {
        instructions.add(Instruction.of(opcode));
    }

    public void emit(Opcode opcode, Object operand) {
        instructions.add(Instruction.of(opcode, operand));
    }

    public void emitPush(double value) {
        emit(Opcode.PUSH, value);
    }

    public void emitLoad(String name) {
        emit(Opcode.LOAD, name);
    }

    public void emitStore(String name) {
        emit(Opcode.STORE, name);
    }

    public void emitBinary(String operator) {
        emit(opcodeFor(operator));
    }

    /**
     * Эмитит JMP/JZ с заглушкой в операнде, возвращает индекс для последующего patch().
     */
    public int emitJumpPlaceholder(Opcode opcode) {
        int index = instructions.size();
        instructions.add(Instruction.of(opcode, -1));
        return index;
    }

    /**
     * Заменяет операнд инструкции по индексу, не меняя её opcode.
     */
    public void patch(int index, int target) {
        Instruction old = instructions.get(index);
        instructions.set(index, new Instruction(old.opcode(), target));
    }

    public void reset() {
        instructions.clear();
    }

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