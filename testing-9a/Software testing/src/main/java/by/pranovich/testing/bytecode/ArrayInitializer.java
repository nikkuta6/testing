package by.pranovich.testing.bytecode;

/** ARRAY_INIT operand: target name, value count and optional size on the stack. */
public record ArrayInitializer(String name, int elementCount, boolean explicitSize) {

    @Override
    public String toString() {
        return name + " " + elementCount + (explicitSize ? " sized" : "");
    }
}
