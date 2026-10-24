package by.pranovich.testing.bytecode;

import java.util.ArrayList;
import java.util.List;

/** Builds stack-machine instructions while the source is being parsed. */
public final class CodeGenerator {

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

    public void emitArrayNew(String name) {
        emit(Opcode.ARRAY_NEW, name);
    }

    public void emitArrayInit(String name, int elementCount, boolean explicitSize) {
        emit(Opcode.ARRAY_INIT, new ArrayInitializer(name, elementCount, explicitSize));
    }

    public void emitArrayLoad(String name) {
        emit(Opcode.ARRAY_LOAD, name);
    }

    public void emitArrayStore(String name) {
        emit(Opcode.ARRAY_STORE, name);
    }

    public void emitCall(String name, int argumentCount) {
        emit(Opcode.CALL, new FunctionCall(name, argumentCount));
    }

    public void emitBinary(String operator) {
        emit(opcodeFor(operator));
    }

    public int emitJumpPlaceholder(Opcode opcode) {
        int index = instructions.size();
        instructions.add(Instruction.of(opcode, -1));
        return index;
    }

    public void patchJump(int index, int target) {
        Instruction instruction = instructions.get(index);
        instructions.set(index, new Instruction(instruction.opcode(), target));
    }

    public void reset() {
        instructions.clear();
    }

    public Program toProgram() {
        return new Program(instructions);
    }

    private Opcode opcodeFor(String operator) {
        return switch (operator) {
            case "+" -> Opcode.ADD;
            case "-" -> Opcode.SUB;
            case "*" -> Opcode.MUL;
            case "/" -> Opcode.DIV;
            case "^" -> Opcode.POW;
            case "<" -> Opcode.LT;
            case ">" -> Opcode.GT;
            case "<=" -> Opcode.LE;
            case ">=" -> Opcode.GE;
            case "==" -> Opcode.EQ;
            case "<>" -> Opcode.NE;
            case "&&" -> Opcode.AND;
            case "||" -> Opcode.OR;
            default -> throw new IllegalArgumentException(
                    "Неизвестный бинарный оператор: " + operator);
        };
    }
}
