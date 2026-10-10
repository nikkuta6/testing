package by.pranovich.testing.parser;

public record Instruction(Opcode opcode, Object operand) {

    public static Instruction of(Opcode opcode) {
        return new Instruction(opcode, null);
    }

    public static Instruction of(Opcode opcode, Object operand) {
        return new Instruction(opcode, operand);
    }

    @Override
    public String toString() {
        return operand == null ? opcode.name() : opcode.name() + " " + operand;
    }
}