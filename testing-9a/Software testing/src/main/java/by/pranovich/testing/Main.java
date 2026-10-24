package by.pranovich.testing;

import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.runtime.VirtualMachine;

/** Example program: sin(pi/2) as the first ten terms of its Taylor series. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String source = """
                float x = 3.141592653589793 / 2, sum = 0;
                int i = 0;
                while (i < 10) {
                    sum = sum + (-1) ^ i * x ^ (2 * i + 1) / (2 * i + 1)!;
                    i = i + 1;
                    print(i, sum, sin(x), sin(x) - sum);
                }
                print(11111);
                print(sum);
                print(sin(x));
                
                
                """;

        Compiler compiler = new Compiler(source);
        if (compiler.compile()) {
            new VirtualMachine().run(compiler.getProgram());
        } else {
            System.err.println(compiler.getSyntaxError());
        }
    }
}
