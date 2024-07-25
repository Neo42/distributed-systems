import org.junit.Before;
import org.junit.Test;
import rmi_calculator.Calculator;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CalculatorClientTest {
    private Calculator calculator;

    private static final int CLIENT_COUNT = 4;
    private static final int DELAY_MILLIS = 2000;
    private static final int TEST_VALUE_1 = 10;
    private static final int TEST_VALUE_2 = 15;
    private static final int TEST_VALUE_3 = 25;
    private static final int TEST_VALUE_4 = 30;
    private static final int DELAY_TEST_VALUE = 42;
    private static final String TEST_OPERATION_1 = "min";
    private static final String TEST_OPERATION_2 = "max";

    /**
     * Sets up the test environment before each test method.
     * Connects to the RMI registry and creates a client stack.
     */
    @Before
    public void setUp() throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        calculator = (Calculator) registry.lookup("Calculator");
    }

    /**
     * Tests the pushValue operation for a single client.
     * Pushes two values and verifies they can be popped in the correct order.
     */
    @Test
    public void testPushValueSingleClient() throws Exception {
        String clientId = calculator.createClientStack();
        calculator.pushValue(TEST_VALUE_1, clientId);
        calculator.pushValue(TEST_VALUE_2, clientId);
        assertEquals(TEST_VALUE_2, calculator.pop(clientId));
        assertEquals(TEST_VALUE_1, calculator.pop(clientId));
        assertTrue(calculator.isEmpty(clientId));
    }

    /**
     * Tests the pushValue operation for multiple clients concurrently.
     * Creates multiple threads, each pushing and popping values on their own
     * stack.
     */
    @Test
    public void testPushValueMultipleClients() throws Exception {
        CountDownLatch latch = new CountDownLatch(CLIENT_COUNT);

        for (int clientIndex = 0; clientIndex < CLIENT_COUNT; clientIndex++) {
            new Thread(() -> {
                try {
                    String clientId = calculator.createClientStack();
                    calculator.pushValue(TEST_VALUE_4, clientId);
                    calculator.pushValue(TEST_VALUE_3, clientId);
                    assertEquals(TEST_VALUE_3, calculator.pop(clientId));
                    assertEquals(TEST_VALUE_4, calculator.pop(clientId));
                    assertTrue(calculator.isEmpty(clientId));
                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        latch.await();
    }

    /**
     * Tests the pushOperation method for a single client.
     * Pushes two values, performs a min operation, and verifies the result.
     */
    @Test
    public void testPushOperationSingleClient() throws Exception {
        String clientId = calculator.createClientStack();
        calculator.pushValue(TEST_VALUE_1, clientId);
        calculator.pushValue(TEST_VALUE_2, clientId);
        calculator.pushOperation(TEST_OPERATION_1, clientId);
        assertEquals(Math.min(TEST_VALUE_1, TEST_VALUE_2),
                calculator.pop(clientId));
        assertTrue(calculator.isEmpty(clientId));
    }

    /**
     * Tests the pushOperation method for multiple clients concurrently.
     * Each thread pushes values, performs a max operation, and verifies the
     * result.
     */
    @Test
    public void testPushOperationMultipleClients() throws Exception {
        CountDownLatch latch = new CountDownLatch(CLIENT_COUNT);

        for (int clientIndex = 0; clientIndex < CLIENT_COUNT; clientIndex++) {
            new Thread(() -> {
                try {
                    String clientId = calculator.createClientStack();
                    calculator.pushValue(TEST_VALUE_3,
                            clientId);
                    calculator.pushValue(TEST_VALUE_4,
                            clientId);
                    calculator.pushOperation(TEST_OPERATION_2, clientId);
                    assertEquals(Math.max(TEST_VALUE_3, TEST_VALUE_4),
                            calculator.pop(clientId));
                    assertTrue(calculator.isEmpty(clientId));
                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        latch.await();
    }

    /**
     * Tests the pop operation for a single client.
     * Pushes two values and verifies they can be popped in the correct order.
     */
    @Test
    public void testPopSingleClient() throws Exception {
        String clientId = calculator.createClientStack();
        calculator.pushValue(TEST_VALUE_1, clientId);
        calculator.pushValue(TEST_VALUE_2, clientId);
        assertEquals(TEST_VALUE_2, calculator.pop(clientId));
        assertEquals(TEST_VALUE_1, calculator.pop(clientId));
        assertTrue(calculator.isEmpty(clientId));
    }

    /**
     * Tests the pop operation for multiple clients concurrently.
     * Each thread pushes values and verifies they can be popped correctly.
     */
    @Test
    public void testPopMultipleClients() throws Exception {
        CountDownLatch latch = new CountDownLatch(CLIENT_COUNT);

        for (int clientIndex = 0; clientIndex < CLIENT_COUNT; clientIndex++) {
            new Thread(() -> {
                try {
                    String clientId = calculator.createClientStack();
                    calculator.pushValue(TEST_VALUE_3, clientId);
                    calculator.pushValue(TEST_VALUE_4, clientId);
                    assertEquals(TEST_VALUE_4, calculator.pop(clientId));
                    assertEquals(TEST_VALUE_3, calculator.pop(clientId));
                    assertTrue(calculator.isEmpty(clientId));
                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        latch.await();
    }

    /**
     * Tests the delayPop operation for a single client.
     * Verifies that the operation takes at least the specified delay time.
     */
    @Test
    public void testDelayPopSingleClient() throws Exception {
        String clientId = calculator.createClientStack();
        calculator.pushValue(DELAY_TEST_VALUE, clientId);
        long startTime = System.currentTimeMillis();
        assertEquals(DELAY_TEST_VALUE,
                calculator.delayPop(DELAY_MILLIS, clientId));
        long endTime = System.currentTimeMillis();
        assertTrue(endTime - startTime >= DELAY_MILLIS);
        assertTrue(calculator.isEmpty(clientId));
    }

    /**
     * Tests the delayPop operation for multiple clients concurrently.
     * Each thread performs a delayPop and verifies the timing and result.
     */
    @Test
    public void testDelayPopMultipleClients() throws Exception {
        CountDownLatch latch = new CountDownLatch(CLIENT_COUNT);

        for (int clientIndex = 0; clientIndex < CLIENT_COUNT; clientIndex++) {
            new Thread(() -> {
                try {
                    String clientId = calculator.createClientStack();
                    calculator.pushValue(DELAY_TEST_VALUE, clientId);
                    long startTime = System.currentTimeMillis();
                    assertEquals(DELAY_TEST_VALUE,
                            calculator.delayPop(DELAY_MILLIS, clientId));
                    long endTime = System.currentTimeMillis();
                    assertTrue(endTime - startTime >= DELAY_MILLIS);
                    assertTrue(calculator.isEmpty(clientId));
                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        latch.await();
    }
}
