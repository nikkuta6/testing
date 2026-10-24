package by.pranovich.testing;

import by.pranovich.testing.compiler.Compiler;
import by.pranovich.testing.runtime.VirtualMachine;

/** Example program: sin(pi/2) as the first ten terms of its Taylor series. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String source = """
                float x = _pi() / 2 + _pi() * 1, s = sin(x), pi = _pi(), r = x;
                while (r > pi) { r = r - 2 * pi; }
                while (r < -pi) { r = r + 2 * pi; }
                if (r > pi / 2) { r = pi - r; }
                if (r < -pi / 2) { r = -pi - r; }
                float x2 = r * r, term = r, sum = 0;
                int i = 0;
                while (i < 20) {
                    sum = sum + term;
                    i = i + 1;
                    term = -term * x2 / (2 * i * (2 * i + 1));
                    print(i, sum, s, s - sum);
                }
                print(11111);
                print(sum);
                print(s);
                """;

        Compiler compiler = new Compiler(source);
        if (compiler.compile()) {
            new VirtualMachine().run(compiler.getProgram());
        } else {
            System.err.println(compiler.getSyntaxError());
        }
    }
}
