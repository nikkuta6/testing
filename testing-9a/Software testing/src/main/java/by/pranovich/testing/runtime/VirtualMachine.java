package by.pranovich.testing.runtime;

import by.pranovich.testing.bytecode.ArrayInitializer;
import by.pranovich.testing.bytecode.FunctionCall;
import by.pranovich.testing.bytecode.Instruction;
import by.pranovich.testing.bytecode.Program;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the educational stack byte-code.
 */
public final class VirtualMachine {

    private final Deque<Double> stack = new ArrayDeque<>();
    private final Map<String, Double> variables = new HashMap<>();
    private final Map<String, double[]> arrays = new HashMap<>();
    private final PrintStream out;

    public VirtualMachine() {
        this(System.out);
    }

    public VirtualMachine(PrintStream out) {
        this.out = out;
    }

    public void run(Program program) {
        resetMemory();
        List<Instruction> code = program.instructions();
        int programCounter = 0;

        while (programCounter < code.size()) {
            Instruction instruction = code.get(programCounter);
            switch (instruction.opcode()) {
                case PUSH -> stack.push((Double) instruction.operand());
                case LOAD -> loadVariable((String) instruction.operand());
                case STORE -> variables.put((String) instruction.operand(), stack.pop());

                case ARRAY_NEW -> createArray((String) instruction.operand());
                case ARRAY_INIT -> initializeArray((ArrayInitializer) instruction.operand());
                case ARRAY_LOAD -> loadArrayElement((String) instruction.operand());
                case ARRAY_STORE -> storeArrayElement((String) instruction.operand());

                case ADD -> stack.push(stack.pop() + stack.pop());
                case SUB -> subtract();
                case MUL -> stack.push(stack.pop() * stack.pop());
                case DIV -> divide();
                case POW -> power();

                case LT -> pushBoolean(compareTopValues() < 0);
                case GT -> pushBoolean(compareTopValues() > 0);
                case LE -> pushBoolean(compareTopValues() <= 0);
                case GE -> pushBoolean(compareTopValues() >= 0);
                case EQ -> pushBoolean(compareTopValues() == 0);
                case NE -> pushBoolean(compareTopValues() != 0);
                case AND -> logicalAnd();
                case OR -> logicalOr();

                case NEG -> stack.push(-stack.pop());
                case NOT -> pushBoolean(stack.pop() == 0.0);
                case FACT -> stack.push(factorial(stack.pop()));
                case CALL -> call((FunctionCall) instruction.operand());

                case JMP -> programCounter = (Integer) instruction.operand() - 1;
                case JZ -> {
                    if (stack.pop() == 0.0) {
                        programCounter = (Integer) instruction.operand() - 1;
                    }
                }
                case PRINT -> print(instruction.operand(), stack.pop());
                case HALT -> {
                    return;
                }
            }
            programCounter++;
        }
    }

    private void resetMemory() {
        stack.clear();
        variables.clear();
        arrays.clear();
    }

    private void loadVariable(String name) {
        stack.push(variables.getOrDefault(name, 0.0));
    }

    private void createArray(String name) {
        arrays.put(name, new double[toSize(stack.pop())]);
    }

    private void initializeArray(ArrayInitializer initializer) {
        double[] values = new double[initializer.elementCount()];
        for (int index = values.length - 1; index >= 0; index--) {
            values[index] = stack.pop();
        }

        int size = initializer.explicitSize() ? toSize(stack.pop()) : values.length;
        if (values.length > size) {
            throw new IllegalArgumentException("Слишком много значений для массива '"
                    + initializer.name() + "': " + values.length + ", размер " + size);
        }
        arrays.put(initializer.name(), Arrays.copyOf(values, size));
    }

    private void loadArrayElement(String name) {
        double[] array = requireArray(name);
        stack.push(array[toIndex(name, stack.pop(), array.length)]);
    }

    private void storeArrayElement(String name) {
        double value = stack.pop();
        double[] array = requireArray(name);
        int index = toIndex(name, stack.pop(), array.length);
        array[index] = value;
    }

    private void subtract() {
        double right = stack.pop();
        double left = stack.pop();
        stack.push(left - right);
    }

    private void divide() {
        double right = stack.pop();
        double left = stack.pop();
        stack.push(left / right);
    }

    private void power() {
        double right = stack.pop();
        double left = stack.pop();
        stack.push(Math.pow(left, right));
    }

    /**
     * Compares the two top values while preserving their source-code order.
     */
    private int compareTopValues() {
        double right = stack.pop();
        double left = stack.pop();
        return Double.compare(left, right);
    }

    private void logicalAnd() {
        double right = stack.pop();
        double left = stack.pop();
        pushBoolean(left != 0.0 && right != 0.0);
    }

    private void logicalOr() {
        double right = stack.pop();
        double left = stack.pop();
        pushBoolean(left != 0.0 || right != 0.0);
    }

    private void pushBoolean(boolean value) {
        stack.push(value ? 1.0 : 0.0);
    }

    private double factorial(double value) {
        double result = 1.0;
        for (long factor = 2; factor <= (long) value; factor++) {
            result *= factor;
        }
        return result;
    }

    private void call(FunctionCall function) {
        double[] arguments = popArguments(function.argumentCount());
        double result = switch (function.name()) {
            case "sin" -> Math.sin(arguments[0]);
            case "cos", "cod" -> Math.cos(arguments[0]);
            case "tg" -> Math.tan(arguments[0]);
            case "ctg" -> 1.0 / Math.tan(arguments[0]);
            case "abs" -> Math.abs(arguments[0]);
            case "max" -> Arrays.stream(arguments).max().orElseThrow();
            case "min" -> Arrays.stream(arguments).min().orElseThrow();
            default -> throw new IllegalArgumentException(
                    "Неизвестная встроенная функция '" + function.name() + "'");
        };
        stack.push(result);
    }

    private double[] popArguments(int count) {
        double[] arguments = new double[count];
        for (int index = count - 1; index >= 0; index--) {
            arguments[index] = stack.pop();
        }
        return arguments;
    }

    private double[] requireArray(String name) {
        double[] array = arrays.get(name);
        if (array == null) {
            throw new IllegalStateException("Массив '" + name + "' не создан");
        }
        return array;
    }

    private int toSize(double value) {
        if (!Double.isFinite(value) || value < 0 || value != Math.rint(value)
                || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Размер массива должен быть целым неотрицательным числом: " + value);
        }
        return (int) value;
    }

    private int toIndex(String name, double value, int length) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new IndexOutOfBoundsException(
                    "Индекс массива '" + name + "' должен быть целым: " + value);
        }
        int index = (int) value;
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Индекс " + index + " вне границ массива '"
                    + name + "' длины " + length);
        }
        return index;
    }

    private void print(Object type, double value) {
        if ("int".equals(type)) {
            out.println((long) value);
        } else {
            out.println(value);
        }
    }
}
