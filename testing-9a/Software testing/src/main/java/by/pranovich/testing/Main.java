package by.pranovich.testing;

import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.runtime.VirtualMachine;

/** Minimal example of compiling and running a program written in the language. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String source = """
                int values = [1, 5, 2];
                print max(values[0], values[1], values[2]);
                """;

        Compiler compiler = new Compiler(source);
        if (compiler.compile()) {
            new VirtualMachine().run(compiler.getProgram());
        } else {
            System.err.println(compiler.getSyntaxError());
        }
    }
}
