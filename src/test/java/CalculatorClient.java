import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import rmi_calculator.Calculator;

public class CalculatorClient {
    private static final int CLIENT_COUNT = 4;
    private static final int DELAY_MILLIS = 2000;
    private static final int TEST_VALUE_1 = 10;
    private static final int TEST_VALUE_2 = 15;
    private static final int TEST_VALUE_3 = 25;
    private static final int TEST_VALUE_4 = 30;
    private static final int DELAY_TEST_VALUE = 42;
    private static final String TEST_OPERATION = "min";

    @Test
    public void testPushValueSingleClient() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        calculator.pushValue(TEST_VALUE_1);
        assertEquals(TEST_VALUE_1, calculator.pop());
    }

    @Test
    @Disabled
    public void testPushValueMultipleClients() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        runMultiClientTest((clientIndex) -> {
            int testValue = getTestValue(clientIndex);
            calculator.pushValue(testValue);
            assertEquals(testValue, calculator.pop());
        });
    }

    @Test
    public void testPushOperationSingleClient() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        calculator.pushValue(TEST_VALUE_1);
        calculator.pushValue(TEST_VALUE_2);
        calculator.pushOperation(TEST_OPERATION);
        assertEquals(Math.min(TEST_VALUE_1, TEST_VALUE_2), calculator.pop());
    }

    @Test
    @Disabled
    public void testPushOperationMultipleClients() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        runMultiClientTest((clientIndex) -> {
            int testValue1 = getTestValue(clientIndex);
            int testValue2 = getTestValue((clientIndex + 1) % CLIENT_COUNT);
            calculator.pushValue(testValue1);
            calculator.pushValue(testValue2);
            calculator.pushOperation(TEST_OPERATION);
            assertEquals(Math.min(testValue1, testValue2), calculator.pop());
        });
    }

    @Test
    public void testPopSingleClient() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        calculator.pushValue(TEST_VALUE_1);
        assertEquals(TEST_VALUE_1, calculator.pop());
    }

    @Test
    @Disabled
    public void testPopMultipleClients() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        runMultiClientTest((clientIndex) -> {
            int testValue = getTestValue(clientIndex);
            calculator.pushValue(testValue);
            assertEquals(testValue, calculator.pop());
        });
    }

    @Test
    public void testIsEmpty() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        assertTrue(calculator.isEmpty());
        calculator.pushValue(TEST_VALUE_1);
        assertFalse(calculator.isEmpty());
    }

    @Test
    public void testDelayPopSingleClient() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        calculator.pushValue(DELAY_TEST_VALUE);
        long startTime = System.currentTimeMillis();
        int result = calculator.delayPop(DELAY_MILLIS);
        long endTime = System.currentTimeMillis();
        assertEquals(DELAY_TEST_VALUE, result);
        assertTrue(endTime - startTime >= DELAY_MILLIS);
    }

    @Test
    @Disabled
    public void testDelayPopMultipleClients() throws Exception {
        Registry registry = LocateRegistry.getRegistry();
        Calculator calculator = (Calculator) registry.lookup("Calculator");
        runMultiClientTest((clientIndex) -> {
            int testValue = getTestValue(clientIndex);
            calculator.pushValue(testValue);
            long startTime = System.currentTimeMillis();
            int result = calculator.delayPop(DELAY_MILLIS);
            long endTime = System.currentTimeMillis();
            assertEquals(testValue, result);
            assertTrue(endTime - startTime >= DELAY_MILLIS);
        });
    }

    private void runMultiClientTest(ClientTest test) throws Exception {
        CountDownLatch latch = new CountDownLatch(CLIENT_COUNT);
        for (int i = 0; i < CLIENT_COUNT; i++) {
            final int clientIndex = i;
            new Thread(() -> {
                try {
                    test.run(clientIndex);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
    }

    private int getTestValue(int clientIndex) {
        switch (clientIndex) {
            case 0:
                return TEST_VALUE_1;
            case 1:
                return TEST_VALUE_2;
            case 2:
                return TEST_VALUE_3;
            default:
                return TEST_VALUE_4;
        }
    }

    @FunctionalInterface
    private interface ClientTest {
        void run(int clientIndex) throws Exception;
    }
}
