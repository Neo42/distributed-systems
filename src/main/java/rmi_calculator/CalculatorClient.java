package rmi_calculator;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CalculatorClient {
    private CalculatorClient() {
    }

    public static void main(String[] args) {
        String host = (args.length < 1) ? null : args[0];
        try {
            testSingleClient(host);
            testMultipleClients(host);

        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
        }
    }

    private static void testSingleClient(String host) throws Exception {
        System.out.println("\nTesting multiple client operations:");

        Thread client1 = new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(host);
                Calculator calculator = (Calculator) registry
                        .lookup("Calculator");
                calculator.pushValue(15);
                calculator.pushValue(25);
                calculator.pushOperation("min");
                System.out.println("Client 1 min: " + calculator.pop());

                calculator.pushValue(42);
                long startTime = System.currentTimeMillis();
                int result = calculator.delayPop(2000); // 2 seconds delay
                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;
                System.out.println("DelayPop result: " + result);
                System.out.println("Elapsed time: " + elapsedTime + " ms");
                if (elapsedTime >= 2000) {
                    System.out.println(
                            "DelayPop successful: delay was at least 2 seconds");
                } else {
                    System.out.println(
                            "DelayPop failed: delay was less than 2 seconds");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        client1.start();
        client1.join();
    }

    private static void testMultipleClients(String host) throws Exception {
        System.out.println("\nTesting multiple client operations:");
        Thread client1 = new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(host);
                Calculator calculator = (Calculator) registry
                        .lookup("Calculator");
                calculator.pushValue(15);
                calculator.pushValue(25);
                calculator.pushOperation("min");
                System.out.println("Client 1 min: " + calculator.pop());

                System.out.println("\nTesting client 1 delayPop operation:");
                calculator.pushValue(42);
                long startTime = System.currentTimeMillis();
                int result = calculator.delayPop(2000); // 2 seconds delay
                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;
                System.out.println("DelayPop result: " + result);
                System.out.println("Elapsed time: " + elapsedTime + " ms");
                if (elapsedTime >= 2000) {
                    System.out.println(
                            "DelayPop successful: delay was at least 2 seconds");
                } else {
                    System.out.println(
                            "DelayPop failed: delay was less than 2 seconds");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread client2 = new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(host);
                Calculator calculator = (Calculator) registry
                        .lookup("Calculator");
                calculator.pushValue(30);
                calculator.pushValue(40);
                calculator.pushOperation("lcm");
                System.out.println("Client 2 LCM: " + calculator.pop());

                System.out.println("\nTesting client 2 delayPop operation:");
                calculator.pushValue(0);
                long startTime = System.currentTimeMillis();
                int result = calculator.delayPop(2000); // 2 seconds delay
                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;
                System.out.println("DelayPop result: " + result);
                System.out.println("Elapsed time: " + elapsedTime + " ms");
                if (elapsedTime >= 2000) {
                    System.out.println(
                            "DelayPop successful: delay was at least 2 seconds");
                } else {
                    System.out.println(
                            "DelayPop failed: delay was less than 2 seconds");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread client3 = new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(host);
                Calculator calculator = (Calculator) registry
                        .lookup("Calculator");
                calculator.pushValue(50);
                calculator.pushValue(60);
                calculator.pushOperation("gcd");
                System.out.println("Client 3 GCD: " + calculator.pop());
                calculator.pushValue(12312);
                System.out.println(
                        "Client 3 DelayPop: " + calculator.delayPop(1500));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread client4 = new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(host);
                Calculator calculator = (Calculator) registry
                        .lookup("Calculator");
                calculator.pushValue(7);
                calculator.pushValue(11);
                calculator.pushOperation("lcm");
                System.out.println("Client 4 LCM: " + calculator.pop());
                calculator.pushValue(100);
                System.out.println(
                        "Client 4 DelayPop: " + calculator.delayPop(1000));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        client1.start();
        client2.start();
        client3.start();
        client4.start();
        client1.join();
        client2.join();
        client3.join();
        client4.join();
    }
}
