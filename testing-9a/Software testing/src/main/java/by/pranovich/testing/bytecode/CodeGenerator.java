package by.pranovich.testing.bytecode;

import by.pranovich.testing.runtime.Complex;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds stack-machine instructions while the source is being parsed.
 */
public final class CodeGenerator {

    private final List<Instruction> instructions = new ArrayList<>();
    private final List<Integer> sourcePositions = new ArrayList<>();
    private int sourcePosition = -1;

    public int size() {
        return instructions.size();
    }

    /** Source offset of the statement being compiled; recorded for every emitted instruction (-1: none). */
    public int sourcePosition() {
        return sourcePosition;
    }

    public void setSourcePosition(int sourcePosition) {
        this.sourcePosition = sourcePosition;
    }

    public void emit(Opcode opcode) {
        emit(opcode, null);
    }

    public void emit(Opcode opcode, Object operand) {
        instructions.add(Instruction.of(opcode, operand));
        sourcePositions.add(sourcePosition);
    }

    public void emitPush(double value) {
        emit(Opcode.PUSH, value);
    }

    public void emitPushImaginary(double value) {
        emit(Opcode.PUSH, new Complex(0, value));
    }

    public void emitLoad(String name) {
        emit(Opcode.LOAD, name);
    }

    /** The target type travels with the store: the VM checks that int targets get exact integers. */
    public void emitStore(String name, String type) {
        emit(Opcode.STORE, List.of(name, type));
    }

    /** ARRAY_NEW is an initializer without values: the array is filled with zeros of its element type. */
    public void emitArrayNew(String name, String type) {
        emit(Opcode.ARRAY_NEW, new ArrayInitializer(name, 0, true, type));
    }

    public void emitArrayInit(String name, int elementCount, boolean explicitSize, String type) {
        emit(Opcode.ARRAY_INIT, new ArrayInitializer(name, elementCount, explicitSize, type));
    }

    public void emitArrayLoad(String name) {
        emit(Opcode.ARRAY_LOAD, name);
    }

    public void emitArrayStore(String name, String type) {
        emit(Opcode.ARRAY_STORE, List.of(name, type));
    }

    public void emitArrayEquals(String left, String right) {
        emit(Opcode.ARRAY_EQ, List.of(left, right));
    }

    public void emitArrayPrint(String name, String type) {
        emit(Opcode.ARRAY_PRINT, List.of(name, type));
    }

    public void emitCall(String name, int argumentCount) {
        emit(Opcode.CALL, new FunctionCall(name, argumentCount));
    }

    /** The operand is the entry address; -1 until the function is declared and the call is patched. */
    public int emitCallFunction(int address) {
        int index = instructions.size();
        emit(Opcode.CALL_FUN, address);
        return index;
    }

    public void emitBinary(String operator) {
        emit(opcodeFor(operator));
    }

    /** Arithmetic carries the result type, so the VM checks an intermediate int for overflow. */
    public void emitBinary(String operator, String type) {
        emit(opcodeFor(operator), type);
    }

    public int emitJumpPlaceholder(Opcode opcode) {
        int index = instructions.size();
        emit(opcode, -1);
        return index;
    }

    public void patchJump(int index, int target) {
        patchOperand(index, target);
    }

    public void patchOperand(int index, Object operand) {
        Instruction instruction = instructions.get(index);
        instructions.set(index, new Instruction(instruction.opcode(), operand));
    }

    /** Whether an instruction from {@code from} on reads or writes the variable or array {@code name}. */
    public boolean usesName(String name, int from) {
        for (Instruction instruction : instructions.subList(from, instructions.size())) {
            Object operand = instruction.operand();
            if (name.equals(operand)
                    || operand instanceof List<?> names && names.contains(name)
                    || operand instanceof ArrayInitializer array && array.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void reset() {
        instructions.clear();
        sourcePositions.clear();
        sourcePosition = -1;
    }

    public Program toProgram() {
        return new Program(instructions, sourcePositions);
    }

    private Opcode opcodeFor(String operator) {
        return switch (operator) {
            case "+" -> Opcode.ADD;
            case "-" -> Opcode.SUB;
            case "*" -> Opcode.MUL;
//            case "^" -> Opcode.POW;
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
