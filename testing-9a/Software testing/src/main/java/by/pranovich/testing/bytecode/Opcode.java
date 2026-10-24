package by.pranovich.testing.bytecode;

/** Operations supported by the educational stack machine. */
public enum Opcode {
    PUSH, LOAD, STORE,
    ARRAY_NEW, ARRAY_INIT, ARRAY_LOAD, ARRAY_STORE,
    ADD, SUB, MUL, DIV,
    LT, GT, LE, GE, EQ, NE,
    AND, OR,
    NEG, NOT, FACT,
    JMP, JZ,
    CALL, PRINT,
    HALT
}
