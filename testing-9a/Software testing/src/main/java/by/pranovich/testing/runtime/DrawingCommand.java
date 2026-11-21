package by.pranovich.testing.runtime;

import java.util.List;

/** A validated canvas operation, replayed by the IDE in execution order. */
public record DrawingCommand(String name, List<Double> arguments) {
    public DrawingCommand {
        arguments = List.copyOf(arguments);
    }
}
