package by.pranovich.testing.bytecode;

/** CALL operand: built-in function name and number of arguments on the stack. */
public record FunctionCall(String name, int argumentCount) {

    @Override
    public String toString() {
        return name + "/" + argumentCount;
    }
}
