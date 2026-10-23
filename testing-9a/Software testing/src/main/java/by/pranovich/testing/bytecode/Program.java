package by.pranovich.testing.bytecode;

import java.util.List;

/** Immutable sequence of instructions ready for execution. */
public final class Program {

    private final List<Instruction> instructions;

    public Program(List<Instruction> instructions) {
        this.instructions = List.copyOf(instructions);
    }

    public List<Instruction> instructions() {
        return instructions;
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
