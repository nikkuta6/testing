package by.pranovich.testing.bytecode;

import java.util.List;

/** Immutable sequence of instructions ready for execution. */
public final class Program {

    private final List<Instruction> instructions;
    private final List<Integer> sourcePositions;

    public Program(List<Instruction> instructions) {
        this(instructions, List.of());
    }

    public Program(List<Instruction> instructions, List<Integer> sourcePositions) {
        this.instructions = List.copyOf(instructions);
        this.sourcePositions = List.copyOf(sourcePositions);
    }

    public List<Instruction> instructions() {
        return instructions;
    }

    /** Offset in the source of the statement that produced the instruction, or -1 if unknown. */
    public int sourcePosition(int index) {
        return index < sourcePositions.size() ? sourcePositions.get(index) : -1;
    }

    public String disassemble() {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < instructions.size(); index++) {
            result.append(String.format("%04d  %s%n", index, instructions.get(index)));
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return disassemble();
    }
}
