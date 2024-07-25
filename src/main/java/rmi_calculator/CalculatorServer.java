package rmi_calculator;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Server application for the RMI Calculator service.
 * Sets up and publishes the Calculator service in the RMI registry.
 */
public class CalculatorServer {
    public static void main(String[] args) {
        try {
            CalculatorImplementation obj = new CalculatorImplementation();
            Calculator stub = obj;

            Registry registry = LocateRegistry.getRegistry();
            registry.bind("Calculator", stub);

            System.out.println("Calculator Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
