package rmi_calculator;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class CalculatorServer {
    public static void main(String[] args) {
        try {
            CalculatorImplementation obj = new CalculatorImplementation();
            Calculator stub = (Calculator) UnicastRemoteObject.exportObject(obj,
                    0);

            // Bind the remote object's stub in the registry, 1099 by default
            Registry registry = LocateRegistry.getRegistry();
            registry.bind("Calculator", stub);

            System.out.println("Calculator Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
