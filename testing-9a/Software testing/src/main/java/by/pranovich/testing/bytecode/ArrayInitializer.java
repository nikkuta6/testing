package by.pranovich.testing.bytecode;

/** ARRAY_INIT operand: target name, value count, optional size on the stack and element type. */
public record ArrayInitializer(String name, int elementCount, boolean explicitSize, String type) {

    @Override
    public String toString() {
        return name + " " + elementCount + (explicitSize ? " sized" : "") + " " + type;
    }
}
