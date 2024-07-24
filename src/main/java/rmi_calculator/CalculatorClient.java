package rmi_calculator;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.CountDownLatch;

/**
 * Client application for testing the RMI Calculator service.
 * Performs single and multi-client tests on various calculator operations.
 */
public class CalculatorClient {
    private static final int CLIENT_COUNT = 4;
    private static final int DELAY_MILLIS = 2000;
    private static final int TEST_VALUE_1 = 10;
    private static final int TEST_VALUE_2 = 15;
    private static final int TEST_VALUE_3 = 25;
    private static final int DELAY_TEST_VALUE = 42;
    private static final String MIN_OPERATION = "min";

    public static void main(String[] args) {
        String host = (args.length < 1) ? null : args[0];
        try {
            testSingleClient(host);
            testMultipleClients(host);
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    /** Tests calculator operations with a single client. */
    private static void testSingleClient(String host) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host);
        Calculator calculator = (Calculator) registry.lookup("Calculator");

        testPushValue(calculator);
        testPushOperation(calculator);
        testPop(calculator);
        testDelayPop(calculator);
    }

    /** Tests calculator operations with multiple concurrent clients. */
    private static void testMultipleClients(String host) throws Exception {
        CountDownLatch latch = new CountDownLatch(CLIENT_COUNT);
        for (int i = 0; i < CLIENT_COUNT; i++) {
            new Thread(() -> {
                try {
                    Registry registry = LocateRegistry.getRegistry(host);
                    Calculator calculator = (Calculator) registry
                            .lookup("Calculator");
                    testPushValue(calculator);
                    testPushOperation(calculator);
                    testPop(calculator);
                    testDelayPop(calculator);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
    }

    /** Tests the pushValue operation. */
    private static void testPushValue(Calculator calculator) throws Exception {
        calculator.pushValue(TEST_VALUE_1);
        System.out.println("PushValue test completed");
    }

    /** Tests the pushOperation with the min operation. */
    private static void testPushOperation(Calculator calculator)
            throws Exception {
        calculator.pushValue(TEST_VALUE_2);
        calculator.pushValue(TEST_VALUE_3);
        calculator.pushOperation(MIN_OPERATION);
        System.out.println("PushOperation test completed");
    }

    /** Tests the pop operation. */
    private static void testPop(Calculator calculator) throws Exception {
        int result = calculator.pop();
        System.out.println("Pop test completed, result: " + result);
    }

    /** Tests the delayPop operation. */
    private static void testDelayPop(Calculator calculator) throws Exception {
        calculator.pushValue(DELAY_TEST_VALUE);
        long startTime = System.currentTimeMillis();
        int result = calculator.delayPop(DELAY_MILLIS);
        long endTime = System.currentTimeMillis();
        System.out.println("DelayPop test completed, result: " + result +
                ", delay: " + (endTime - startTime) + "ms");
    }
}
