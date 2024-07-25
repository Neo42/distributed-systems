package rmi_calculator;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Implementation of the Calculator interface for RMI.
 * This class provides the actual implementation of the remote calculator
 * operations.
 * It manages individual stacks for each client, allowing concurrent access and
 * operations.
 */
public class CalculatorImplementation extends UnicastRemoteObject
        implements Calculator {

    private final Map<String, Stack<Integer>> clientStacks;

    /**
     * Constructor for CalculatorImplementation.
     * Initializes the clientStacks map.
     *
     * @throws RemoteException
     *             if the remote object cannot be created
     */
    public CalculatorImplementation() throws RemoteException {
        clientStacks = new HashMap<>();
    }

    /**
     * Creates a new stack for a client.
     *
     * @throws RemoteException
     *             if the remote method call fails
     */
    @Override
    public String createClientStack() throws RemoteException {
        String clientId = getClientId();
        clientStacks.put(clientId, new Stack<Integer>());
        return clientId;
    }

    /**
     * Pushes a value onto the client's stack.
     *
     * @param val
     *            The value to push
     * @param clientId
     *            The ID of the client
     * @throws RemoteException
     *             if the remote method call fails
     */
    @Override
    public void pushValue(int val, String clientId) throws RemoteException {
        Stack<Integer> stack = getClientStack(clientId);
        stack.push(val);
    }

    /**
     * Performs an operation on the client's stack.
     * Supported operations: min, max, lcm, gcd
     *
     * @param operator
     *            The operation to perform
     * @param clientId
     *            The ID of the client
     * @throws RemoteException
     *             if the remote method call fails or if the operation is
     *             unsupported
     */
    @Override
    public void pushOperation(String operator, String clientId)
            throws RemoteException {
        Stack<Integer> stack = getClientStack(clientId);
        int result;

        switch (operator.toLowerCase()) {
            case "min":
                result = getMin(stack);
                break;
            case "max":
                result = getMax(stack);
                break;
            case "lcm":
                result = getLCM(stack);
                break;
            case "gcd":
                result = getGCD(stack);
                break;
            default:
                throw new RemoteException("Unsupported Operation.");
        }

        stack.clear();
        stack.push(result);
    }

    /**
     * Pops and returns the top value from the client's stack.
     *
     * @param clientId
     *            The ID of the client
     * @return The top value from the stack
     * @throws RemoteException
     *             if the remote method call fails
     */
    @Override
    public int pop(String clientId) throws RemoteException {
        Stack<Integer> stack = getClientStack(clientId);
        return stack.pop();
    }

    /**
     * Checks if the client's stack is empty.
     *
     * @param clientId
     *            The ID of the client
     * @return true if the stack is empty, false otherwise
     * @throws RemoteException
     *             if the remote method call fails
     */
    @Override
    public boolean isEmpty(String clientId) throws RemoteException {
        Stack<Integer> stack = getClientStack(clientId);
        return stack.isEmpty();
    }

    /**
     * Pops a value from the client's stack after a specified delay.
     *
     * @param millis
     *            The delay in milliseconds
     * @param clientId
     *            The ID of the client
     * @return The popped value
     * @throws RemoteException
     *             if the remote method call fails or if the delay is
     *             interrupted
     */
    @Override
    public int delayPop(int millis, String clientId) throws RemoteException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Delay interrupted", e);
        }
        return pop(clientId);
    }

    /**
     * Retrieves the stack for a given client.
     *
     * @param clientId
     *            The ID of the client
     * @return The client's stack
     * @throws RemoteException
     *             if the client stack is not found
     */
    private Stack<Integer> getClientStack(String clientId)
            throws RemoteException {
        Stack<Integer> stack = clientStacks.get(clientId);
        if (stack == null) {
            throw new RemoteException(
                    "Client stack not found. Please create a stack first.");
        }
        return stack;
    }

    /**
     * Generates a unique client ID.
     *
     * @return A new unique client ID
     */
    private String getClientId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Finds the minimum value in the stack.
     *
     * @param stack
     *            The stack to search
     * @return The minimum value in the stack
     */
    private int getMin(Stack<Integer> stack) {
        return stack.stream().min(Integer::compare).get();
    }

    /**
     * Finds the maximum value in the stack.
     *
     * @param stack
     *            The stack to search
     * @return The maximum value in the stack
     */
    private int getMax(Stack<Integer> stack) {
        return stack.stream().max(Integer::compare).get();
    }

    /**
     * Calculates the least common multiple of all values in the stack.
     *
     * @param stack
     *            The stack containing the values
     * @return The least common multiple
     */
    private int getLCM(Stack<Integer> stack) {
        return stack.stream().reduce(1, (a, b) -> Math.abs(a * b) / gcd(a, b));
    }

    /**
     * Calculates the greatest common divisor of all values in the stack.
     *
     * @param stack
     *            The stack containing the values
     * @return The greatest common divisor
     * @throws EmptyStackException
     *             if the stack is empty
     */
    private int getGCD(Stack<Integer> stack) {
        return stack.stream().reduce(this::gcd)
                .orElseThrow(() -> new EmptyStackException());
    }

    /**
     * Calculates the greatest common divisor of two numbers.
     *
     * @param a
     *            The first number
     * @param b
     *            The second number
     * @return The greatest common divisor of a and b
     */
    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
