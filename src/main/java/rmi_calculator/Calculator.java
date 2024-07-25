package rmi_calculator;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for the RMI Calculator service.
 * Defines the operations that can be performed remotely.
 */
public interface Calculator extends Remote {
    /** Create a calculator stack for the current client. */
    String createClientStack() throws RemoteException;

    /** Pushes an integer value onto the calculator's stack. */
    void pushValue(int val, String clientId) throws RemoteException;

    /** Performs the specified operation on the values in the stack. */
    void pushOperation(String operator, String clientId) throws RemoteException;

    /** Removes and returns the top value from the stack. */
    int pop(String clientId) throws RemoteException;

    /** Checks if the calculator's stack is empty. */
    boolean isEmpty(String clientId) throws RemoteException;

    /** Removes and returns the top value after a specified delay. */
    int delayPop(int millis, String clientId) throws RemoteException;
}
