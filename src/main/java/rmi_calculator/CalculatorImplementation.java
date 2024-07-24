package rmi_calculator;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * Implementation of the Calculator interface for remote operations.
 * Provides stack-based calculator functionality with support for basic
 * arithmetic
 * and advanced operations like min, max, lcm, and gcd.
 */
public class CalculatorImplementation implements Calculator {
    private final ThreadLocal<Stack<Integer>> clientStack = ThreadLocal
            .withInitial(Stack::new);

    /**
     * Constructor for CalculatorImplementation.
     * Initializes the stack used for storing integer values.
     */
    public CalculatorImplementation() throws RemoteException {
        super();
    }

    /**
     * Pushes an integer value onto the stack.
     *
     * @param val
     *            The integer value to be pushed.
     */
    public void pushValue(int val) throws RemoteException {
        clientStack.get().push(val);
    }

    /**
     * Pushes an operation (min, max, lcm, gcd) onto the stack.
     * Pops all values from the stack, performs the operation, and pushes the
     * result.
     *
     * @param operator
     *            The operation to be performed (min, max, lcm, gcd).
     */
    // Special case: Throws RemoteException if there are not enough operands on
    // the stack
    public void pushOperation(String operator) throws RemoteException {
        if (isEmpty()) {
            throw new RemoteException("Not enough operands on the stack");
        }

        List<Integer> values = new ArrayList<>();
        while (!isEmpty() && clientStack.get().peek() instanceof Integer) {
            values.add(pop());
        }

        int result = switch (operator.toLowerCase()) {
            case "min" -> Collections.min(values);
            case "max" -> Collections.max(values);
            case "lcm" -> values.stream().reduce(1,
                    (a, b) -> Math.abs(a * b) / gcd(a, b));
            case "gcd" -> values.stream().reduce(0, this::gcd);
            default -> throw new RemoteException(
                    "Invalid operator: " + operator);
        };

        clientStack.get().push(result);
    }

    /**
     * Calculates the greatest common divisor of two integers.
     *
     * @param a
     *            First integer.
     * @param b
     *            Second integer.
     * @return The greatest common divisor of a and b.
     */
    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /**
     * Removes and returns the top integer from the stack.
     *
     * @return The top integer of the stack.
     * @throws RemoteException
     *             if the stack is empty.
     */
    // Special case: May throw EmptyStackException if the stack is empty .
    public int pop() throws RemoteException {
        return clientStack.get().pop();
    }

    /**
     * Checks if the stack is empty.
     * 
     * @return true if the stack is empty, false otherwise.
     */
    public boolean isEmpty() throws RemoteException {
        return clientStack.get().empty();
    }

    /**
     * Removes and returns the top integer from the stack after a specified
     * delay.
     *
     * @param millis
     *            The delay in milliseconds before popping the element.
     * @return The top integer of the stack after the delay.
     * @throws RemoteException
     *             if the stack is empty or if interrupted during sleep.
     */
    // Special case: If interrupted during sleep, throws RemoteException and
    // preserves the interrupt status
    public int delayPop(int millis) throws RemoteException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return pop();
    }
}
