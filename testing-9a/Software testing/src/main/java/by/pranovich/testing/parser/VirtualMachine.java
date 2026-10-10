package by.pranovich.testing.parser;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Стековая виртуальная машина для учебного байт-кода, порождаемого
 * {@link RecursiveDescentParser}. Исполняет {@link Program} и выводит
 * результаты инструкций PRINT в консоль (или переданный поток).
 */
public class VirtualMachine {

    private final Deque<Double> stack = new ArrayDeque<>();
    private final Map<String, Double> variables = new HashMap<>();
    private final PrintStream out;

    public VirtualMachine() {
        this(System.out);
    }

    public VirtualMachine(PrintStream out) {
        this.out = out;
    }

    public void run(Program program) {
        List<Instruction> code = program.instructions();
        int pc = 0;

        while (pc < code.size()) {
            Instruction instruction = code.get(pc);
            switch (instruction.opcode()) {
                case PUSH -> stack.push((Double) instruction.operand());
                case LOAD -> stack.push(variables.getOrDefault((String) instruction.operand(), 0.0));
                case STORE -> variables.put((String) instruction.operand(), stack.pop());
                case ADD -> stack.push(stack.pop() + stack.pop());
                case SUB -> {
                    double b = stack.pop();
                    double a = stack.pop();
                    stack.push(a - b);
                }
                case MUL -> stack.push(stack.pop() * stack.pop());
                case DIV -> {
                    double b = stack.pop();
                    double a = stack.pop();
                    stack.push(a / b);
                }
                case LT -> stack.push(compare(stack.pop(), stack.pop()) < 0 ? 1.0 : 0.0);
                case GT -> stack.push(compare(stack.pop(), stack.pop()) > 0 ? 1.0 : 0.0);
                case LE -> stack.push(compare(stack.pop(), stack.pop()) <= 0 ? 1.0 : 0.0);
                case GE -> stack.push(compare(stack.pop(), stack.pop()) >= 0 ? 1.0 : 0.0);
                case EQ -> stack.push(compare(stack.pop(), stack.pop()) == 0 ? 1.0 : 0.0);
                case NE -> stack.push(compare(stack.pop(), stack.pop()) != 0 ? 1.0 : 0.0);
                case AND -> {
                    double b = stack.pop();
                    double a = stack.pop();
                    stack.push(a != 0.0 && b != 0.0 ? 1.0 : 0.0);
                }
                case OR -> {
                    double b = stack.pop();
                    double a = stack.pop();
                    stack.push(a != 0.0 || b != 0.0 ? 1.0 : 0.0);
                }
                case NEG -> stack.push(-stack.pop());
                case NOT -> stack.push(stack.pop() == 0.0 ? 1.0 : 0.0);
                case FACT -> stack.push(factorial(stack.pop()));
                case JMP -> pc = (Integer) instruction.operand() - 1;
                case JZ -> {
                    if (stack.pop() == 0.0) {
                        pc = (Integer) instruction.operand() - 1;
                    }
                }
                case PRINT -> print(instruction.operand(), stack.pop());
                case HALT -> {
                    return;
                }
            }
            pc++;
        }
    }

    /** Сравнение двух чисел с учётом порядка извлечения из стека. */
    private int compare(double right, double left) {
        return Double.compare(left, right);
    }

    private double factorial(double value) {
        double result = 1.0;
        for (long i = 2; i <= (long) value; i++) {
            result *= i;
        }
        return result;
    }

    private void print(Object type, double value) {
        if ("int".equals(type)) {
            out.println((long) value);
        } else {
            out.println(value);
        }
    }
}
