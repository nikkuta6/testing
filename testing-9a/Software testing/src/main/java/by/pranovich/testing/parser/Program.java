package by.pranovich.testing.parser;

import java.util.List;

public final class Program {

    private final List<Instruction> instructions;

    public Program(List<Instruction> instructions) {
        this.instructions = List.copyOf(instructions);
    }

    public List<Instruction> instructions() {
        return instructions;
    }

    public String disassemble() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < instructions.size(); i++) {
            sb.append(String.format("%04d  %s%n", i, instructions.get(i)));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return disassemble();
    }
}