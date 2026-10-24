package by.pranovich.testing.bytecode;

import java.util.List;

/** USER_CALL operand: function entry point and names of its numeric parameters. */
public record UserFunctionCall(String name, int entryPoint, List<String> parameters) {

    public UserFunctionCall {
        parameters = List.copyOf(parameters);
    }

    @Override
    public String toString() {
        return name + "/" + parameters.size() + " -> " + entryPoint;
    }
}
