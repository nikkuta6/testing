package by.pranovich.testing.parser;

public enum Opcode {
    PUSH, LOAD, STORE,
    ADD, SUB, MUL, DIV,
    LT, GT, LE, GE, EQ, NE,
    AND, OR,
    NEG, NOT, FACT,
    JMP, JZ,
    PRINT,
    HALT
}