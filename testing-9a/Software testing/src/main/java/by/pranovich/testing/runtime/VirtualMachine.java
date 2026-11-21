package by.pranovich.testing.runtime;

import by.pranovich.testing.bytecode.ArrayInitializer;
import by.pranovich.testing.bytecode.FunctionCall;
import by.pranovich.testing.bytecode.Instruction;
import by.pranovich.testing.bytecode.Opcode;
import by.pranovich.testing.bytecode.Program;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.IntConsumer;

/**
 * Executes the educational stack byte-code.
 */
public final class VirtualMachine {

    /** Largest int magnitude: every integer up to it is exact in a double, 2^53 + 1 already is not. */
    public static final double MAX_EXACT_INT = 0x1p53 - 1;
    private static final int MAX_CALL_DEPTH = 10_000;

    /** A call of a user function: names in {@code locals} live in this frame, all others are global. */
    private record Frame(int returnAddress, List<?> locals,
                         Map<String, Object> variables, Map<String, Object[]> arrays) {
        Frame(int returnAddress, List<?> locals) {
            this(returnAddress, locals, new HashMap<>(), new HashMap<>());
        }
    }

    // Values are Double (int, float) or Complex; the parser guarantees which operations they meet.
    private final Deque<Object> stack = new ArrayDeque<>();
    // Global (top-level) memory; locals of the running function are in frames.peek().
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object[]> arrays = new HashMap<>();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final PrintStream out;

    public VirtualMachine() {
        this(System.out);
    }

    public VirtualMachine(PrintStream out) {
        this.out = out;
    }

    public void run(Program program) {
        run(program, programCounter -> { });
    }

    /** Runs the program, calling {@code beforeStep} with the program counter before every instruction. */
    public void run(Program program, IntConsumer beforeStep) {
        resetMemory();
        List<Instruction> code = program.instructions();
        int programCounter = 0;

        while (programCounter < code.size()) {
            beforeStep.accept(programCounter);
            Instruction instruction = code.get(programCounter);
            switch (instruction.opcode()) {
                case PUSH -> stack.push(instruction.operand());
                case LOAD -> loadVariable((String) instruction.operand());
                case STORE -> storeVariable((List<?>) instruction.operand());

                case ARRAY_NEW, ARRAY_INIT -> initializeArray((ArrayInitializer) instruction.operand());
                case ARRAY_LOAD -> loadArrayElement((String) instruction.operand());
                case ARRAY_STORE -> storeArrayElement((List<?>) instruction.operand());
                case ARRAY_EQ -> compareArrays((List<?>) instruction.operand());
                case ARRAY_PRINT -> printArray((List<?>) instruction.operand());

                case ADD, SUB, MUL, DIV, POW -> arithmetic(instruction.opcode(), instruction.operand());

                case LT -> pushBoolean(compareTopValues() < 0);
                case GT -> pushBoolean(compareTopValues() > 0);
                case LE -> pushBoolean(compareTopValues() <= 0);
                case GE -> pushBoolean(compareTopValues() >= 0);
                case EQ -> pushBoolean(valuesEqual(stack.pop(), stack.pop()));
                case NE -> pushBoolean(!valuesEqual(stack.pop(), stack.pop()));
                case AND -> logicalAnd();
                case OR -> logicalOr();

                case NEG -> stack.push(checked(instruction.operand(), negate(stack.pop())));
                case NOT -> pushBoolean(popReal() == 0.0);
                case FACT -> stack.push(checked(instruction.operand(), factorial(popReal())));
                case CALL -> call((FunctionCall) instruction.operand());

                case JMP -> programCounter = (Integer) instruction.operand() - 1;
                case JZ -> {
                    if (popReal() == 0.0) {
                        programCounter = (Integer) instruction.operand() - 1;
                    }
                }
                case PRINT -> out.print(format(instruction.operand(), stack.pop()));
                case PRINT_SPACE -> out.print(" ");
                case PRINT_NEWLINE -> out.print('\n');
                case CALL_FUN -> programCounter = callFunction(code, programCounter) - 1;
                case ENTER -> { }
                case RET -> programCounter = returnFromFunction(instruction.operand()) - 1;
                case NO_RETURN -> throw new IllegalStateException(
                        "Функция '" + instruction.operand() + "' завершилась без return");
                case POP -> stack.pop();
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
        frames.clear();
    }

    /** CALL_FUN points at the ENTER of the function, whose operand lists the locals of the new frame. */
    private int callFunction(List<Instruction> code, int programCounter) {
        if (frames.size() >= MAX_CALL_DEPTH) {
            throw new IllegalStateException("Переполнение стека вызовов: глубина больше " + MAX_CALL_DEPTH);
        }
        int entry = (Integer) code.get(programCounter).operand();
        frames.push(new Frame(programCounter + 1, (List<?>) code.get(entry).operand()));
        return entry;
    }

    private int returnFromFunction(Object type) {
        Frame frame = frames.pop();
        if (!"void".equals(type)) {
            stack.push(checkType(type, stack.pop()));
        }
        return frame.returnAddress();
    }

    /** Variables of the current call frame; empty at the top level, where everything is in {@code variables}. */
    public Map<String, Object> localVariables() {
        return frames.isEmpty() ? Map.of() : frames.peek().variables();
    }

    /** Arrays of the current call frame; empty at the top level, where everything is in {@code arrays}. */
    public Map<String, Object[]> localArrays() {
        return frames.isEmpty() ? Map.of() : frames.peek().arrays();
    }

    private Map<String, Object> variablesOf(String name) {
        Frame frame = frames.peek();
        return frame != null && frame.locals().contains(name) ? frame.variables() : variables;
    }

    private Map<String, Object[]> arraysOf(String name) {
        Frame frame = frames.peek();
        return frame != null && frame.locals().contains(name) ? frame.arrays() : arrays;
    }

    /** A function may run before the top level has reached a declaration it reads, so that is an error, not 0. */
    private void loadVariable(String name) {
        Object value = variablesOf(name).get(name);
        if (value == null && !frames.isEmpty()) {
            throw new IllegalStateException("Переменная '" + name + "' ещё не инициализирована");
        }
        stack.push(value == null ? 0.0 : value);
    }

    private void storeVariable(List<?> target) {
        String name = (String) target.get(0);
        variablesOf(name).put(name, checkType(target.get(1), stack.pop()));
    }

    private void initializeArray(ArrayInitializer initializer) {
        Object[] values = new Object[initializer.elementCount()];
        for (int index = values.length - 1; index >= 0; index--) {
            values[index] = checkType(initializer.type(), stack.pop());
        }

        int size = initializer.explicitSize() ? toSize(popReal()) : values.length;
        if (values.length > size) {
            throw new IllegalArgumentException("Слишком много значений для массива '"
                    + initializer.name() + "': " + values.length + ", размер " + size);
        }
        Object[] array = Arrays.copyOf(values, size);
        Arrays.fill(array, values.length, size, checkType(initializer.type(), 0.0));
        arraysOf(initializer.name()).put(initializer.name(), array);
    }

    private void loadArrayElement(String name) {
        Object[] array = requireArray(name);
        stack.push(array[toIndex(name, popReal(), array.length)]);
    }

    private void storeArrayElement(List<?> target) {
        String name = (String) target.get(0);
        Object value = checkType(target.get(1), stack.pop());
        Object[] array = requireArray(name);
        int index = toIndex(name, popReal(), array.length);
        array[index] = value;
    }

    /** Same element semantics as scalar {@code ==}. */
    private void compareArrays(List<?> names) {
        Object[] left = requireArray((String) names.get(0));
        Object[] right = requireArray((String) names.get(1));
        boolean equal = left.length == right.length;
        for (int index = 0; equal && index < left.length; index++) {
            equal = valuesEqual(right[index], left[index]);
        }
        pushBoolean(equal);
    }

    private void printArray(List<?> operand) {
        StringJoiner result = new StringJoiner(", ", "[", "]");
        for (Object value : requireArray((String) operand.get(0))) {
            result.add(format(operand.get(1), value));
        }
        out.print(result);
    }

    /** Two real operands keep real semantics ((-8) ^ 0.5 is an error); otherwise both become complex. */
    private void arithmetic(Opcode operation, Object type) {
        Object right = stack.pop();
        Object left = stack.pop();
        if (left instanceof Double a && right instanceof Double b) {
            if (operation == Opcode.DIV && b == 0.0 || operation == Opcode.POW && a == 0.0 && b < 0) {
                throw new ArithmeticException("Деление на ноль");
            }
            stack.push(checked(type, switch (operation) {
                case ADD -> a + b;
                case SUB -> a - b;
                case MUL -> a * b;
                case DIV -> a / b;
                default -> Math.pow(a, b);
            }));
        } else {
            Complex a = toComplex(left);
            Complex b = toComplex(right);
            stack.push(checked(type, switch (operation) {
                case ADD -> a.plus(b);
                case SUB -> a.minus(b);
                case MUL -> a.times(b);
                case DIV -> a.dividedBy(b);
                default -> a.pow(b);
            }));
        }
    }

    /** Reals compare with Double.compare; a complex value equals a real one when its imaginary part is zero. */
    private boolean valuesEqual(Object right, Object left) {
        if (left instanceof Double a && right instanceof Double b) {
            return Double.compare(a, b) == 0;
        }
        return toComplex(left).equals(toComplex(right));
    }

    /**
     * Compares the two top values while preserving their source-code order.
     */
    private int compareTopValues() {
        double right = popReal();
        double left = popReal();
        return Double.compare(left, right);
    }

    private void logicalAnd() {
        double right = popReal();
        double left = popReal();
        pushBoolean(left != 0.0 && right != 0.0);
    }

    private void logicalOr() {
        double right = popReal();
        double left = popReal();
        pushBoolean(left != 0.0 || right != 0.0);
    }

    private void pushBoolean(boolean value) {
        stack.push(value ? 1.0 : 0.0);
    }

    private double factorial(double value) {
        if (value < 0 || value != Math.rint(value)) {
            throw new ArithmeticException(
                    "Факториал определён только для целых неотрицательных чисел: " + value);
        }
        double result = 1.0;
        for (long factor = 2; factor <= (long) value; factor++) {
            result = checked(result * factor);
        }
        return result;
    }

    private void call(FunctionCall function) {
        Object[] values = popArguments(function.argumentCount());
        Object result = switch (function.name()) {
            case "re" -> toComplex(values[0]).re();
            case "im" -> toComplex(values[0]).im();
            case "conj" -> toComplex(values[0]).conj();
            case "arg" -> toComplex(values[0]).arg();
            case "abs" -> values[0] instanceof Complex z ? z.abs() : Math.abs((Double) values[0]);
            default -> realFunction(function.name(),
                    Arrays.stream(values).mapToDouble(Double.class::cast).toArray());
        };
        stack.push(checked(result));
    }

    private double realFunction(String name, double[] arguments) {
        return switch (name) {
            case "sin" -> Math.sin(arguments[0]);
            case "cos", "cod" -> Math.cos(arguments[0]);
            case "tg" -> Math.tan(arguments[0]);
            case "ctg" -> {
                double tangent = Math.tan(arguments[0]);
                if (tangent == 0.0) {
                    throw new ArithmeticException("Деление на ноль");
                }
                yield 1.0 / tangent;
            }
            case "max" -> Arrays.stream(arguments).max().orElseThrow();
            case "min" -> Arrays.stream(arguments).min().orElseThrow();
            case "_pi" -> Math.PI;
            case "_e" -> Math.E;
            default -> throw new IllegalArgumentException(
                    "Неизвестная встроенная функция '" + name + "'");
        };
    }

    private Object[] popArguments(int count) {
        Object[] arguments = new Object[count];
        for (int index = count - 1; index >= 0; index--) {
            arguments[index] = stack.pop();
        }
        return arguments;
    }

    private Object[] requireArray(String name) {
        Object[] array = arraysOf(name).get(name);
        if (array == null) {
            throw new IllegalStateException("Массив '" + name + "' ещё не создан");
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

    private double popReal() {
        return (Double) stack.pop();
    }

    private static Object negate(Object value) {
        return value instanceof Complex z ? z.negate() : -(Double) value;
    }

    private static Complex toComplex(Object value) {
        return value instanceof Complex z ? z : new Complex((Double) value, 0);
    }

    /** A complex result is valid when both of its parts are; Infinity wins, since inf * 0 turns the other part to NaN. */
    private Object checked(Object result) {
        if (result instanceof Complex z) {
            checked(Double.isInfinite(z.im()) ? z.im() : z.re());
            checked(z.im());
            return z;
        }
        return checked((double) (Double) result);
    }

    /** An intermediate int result is checked only for range: 7 / 2 may still go on into a float. */
    private Object checked(Object type, Object result) {
        Object value = checked(result);
        if ("int".equals(type) && value instanceof Double number) {
            requireIntRange(number);
        }
        return value;
    }

    /** Operands are always finite, so an infinite result is an overflow, not an input; -0.0 becomes 0.0. */
    private double checked(double result) {
        if (Double.isNaN(result)) {
            throw new ArithmeticException("Недопустимая операция: результат не является числом");
        }
        if (Double.isInfinite(result)) {
            throw new ArithmeticException("Переполнение: результат выходит за пределы float");
        }
        return result + 0.0;
    }

    /** An int is an integer not larger than 2^53 - 1 in magnitude, so that every int is exact in a double. */
    private Object checkType(Object type, Object stored) {
        if ("complex".equals(type)) {
            return toComplex(stored);
        }
        if (!"int".equals(type)) {
            return stored;
        }
        double value = (Double) stored;
        if (value != Math.rint(value)) {
            throw new ArithmeticException("Потеря точности: значение " + value + " нельзя сохранить в int");
        }
        requireIntRange(value);
        return value;
    }

    private static void requireIntRange(double value) {
        if (Math.abs(value) > MAX_EXACT_INT) {
            throw new ArithmeticException("Переполнение int: значение " + value
                    + " по модулю больше 2^53 - 1");
        }
    }

    private String format(Object type, Object value) {
        if ("int".equals(type)) {
            return String.valueOf((long) (double) (Double) checkType(type, value));
        }
        return "complex".equals(type) ? toComplex(value).toString() : String.valueOf(value);
    }

    /** Text of a variable, array or stack value as the debugger shows it: 2.0, 3.0+4.0i, [1.0, 0.0-2.0i]. */
    public static String formatValue(Object value) {
        return value instanceof Object[] array ? Arrays.toString(array) : String.valueOf(value);
    }
}
