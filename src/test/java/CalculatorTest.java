import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rmi_calculator.Calculator;
import rmi_calculator.CalculatorImplementation;

import java.rmi.RemoteException;
import java.util.EmptyStackException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorTest {
    private Calculator calculator;

    @BeforeEach
    public void setUp() throws Exception {
        calculator = new CalculatorImplementation();
    }

    @Test
    public void testPushMultipleValuesAndPop() throws RemoteException {
        calculator.pushValue(5);
        calculator.pushValue(10);
        calculator.pushValue(15);
        assertEquals(15, calculator.pop());
        assertEquals(10, calculator.pop());
        assertEquals(5, calculator.pop());
    }

    @Test
    public void testIsEmpty() throws RemoteException {
        assertTrue(calculator.isEmpty());
        calculator.pushValue(5);
        assertFalse(calculator.isEmpty());
        calculator.pop();
        assertTrue(calculator.isEmpty());
    }

    @Test
    public void testPushOperation() throws RemoteException {
        calculator.pushOperation("min");
        assertEquals("min", calculator.pop());
    }

    @Test
    public void testPopEmptyCalculator() throws RemoteException {
        assertThrows(EmptyStackException.class, () -> calculator.pop());
    }

    @Test
    public void testDelayPop() throws RemoteException {
        calculator.pushValue(42);
        long startTime = System.currentTimeMillis();
        int result = (int) calculator.delayPop(1000);
        long endTime = System.currentTimeMillis();
        assertEquals(42, result);
        assertTrue(endTime - startTime >= 1000);
    }

    @Test
    public void testPushNegativeValue() throws RemoteException {
        calculator.pushValue(-10);
        assertEquals(-10, calculator.pop());
    }

    @Test
    public void testPushZero() throws RemoteException {
        calculator.pushValue(0);
        assertEquals(0, calculator.pop());
    }
}
