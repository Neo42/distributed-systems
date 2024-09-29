package com.restful_weather_aggregation;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final int DATA_EXPIRATION_SECONDS = 30;
    private static final int MAX_STORED_UPDATES = 20;
    private static final String STORAGE_FILE = "aggregation_server_data.txt";
    private static final String TEMP_FILE = "aggregation_server_data_temp.txt";
    private static final String LOG_FILE = "aggregation_server_data.log";

    private final int port;
    private final Map<String, WeatherData> weatherDataMap;
    private final Map<String, Long> lastUpdateTime;
    private final PriorityQueue<WeatherData> recentUpdates;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private int lamportClock;
    private final Logger logger;
    private final CountDownLatch serverStartLatch = new CountDownLatch(1);
    private ExecutorService threadPool;

    /**
     * Constructor for AggregationServer.
     * Initializes the server with the specified port and sets up data structures
     * and logger.
     *
     * @param port The port number on which the server will listen.
     * @throws JsonParseException If there is an error parsing the JSON data.
     */
    public AggregationServer(int port) throws JsonParseException {
        this.port = port;
        this.weatherDataMap = new ConcurrentHashMap<>();
        this.lastUpdateTime = new ConcurrentHashMap<>();
        this.recentUpdates = new PriorityQueue<>(Comparator.comparingInt(WeatherData::getLamportClock));
        this.running = false;
        this.lamportClock = 0;
        this.logger = Logger.getLogger(AggregationServer.class.getName());
        setupLogger();
        loadDataFromFile();
    }

    /**
     * Sets up the logger for the server.
     * Configures the logger to use a file handler with specified file size and
     * number of log files.
     */
    private void setupLogger() {
        try {
            int numLogFiles = 5;
            int fileSizeLimit = 1_000_000; // 1 MB

            FileHandler fileHandler = new FileHandler(LOG_FILE, fileSizeLimit, numLogFiles, true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);

            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Failed to set up logger: " + e.getMessage());
        }
    }

    /**
     * Loads weather data from the storage file into memory.
     * If the file does not exist, starts with an empty data set.
     */
    private void loadDataFromFile() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(STORAGE_FILE));
            for (String line : lines) {
                try {
                    WeatherData data = WeatherData.fromStorageFormat(line);
                    weatherDataMap.put(data.getId(), data);
                    lastUpdateTime.put(data.getId(), System.currentTimeMillis());
                    recentUpdates.offer(data);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid data in storage file: " + e.getMessage());
                }
            }
            logger.info("Data loaded from file. Weather data map size: " + weatherDataMap.size());
        } catch (IOException e) {
            logger.info("No existing data file found. Starting with empty data.");
        }
    }

    /**
     * Saves the current weather data to the storage file.
     * Uses a temporary file to ensure atomicity of the save operation.
     */
    private synchronized void saveDataToFile() {
        try {
            Path tempFile = Paths.get(TEMP_FILE);
            Path storageFile = Paths.get(STORAGE_FILE);

            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                for (WeatherData data : weatherDataMap.values()) {
                    writer.write(data.toStorageFormat());
                    writer.newLine();
                }
            }

            Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Data successfully saved to file. Weather data map size: " + weatherDataMap.size());
        } catch (IOException e) {
            logger.severe("Error saving data to file: " + e.getMessage());
        }
    }

    /**
     * Starts the server and begins accepting client connections.
     * Schedules a task to remove expired data at regular intervals.
     *
     * @throws IOException If there is an error starting the server.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(5000); // 5 second timeout for accepting connections
        running = true;
        threadPool = Executors.newFixedThreadPool(10); // Adjust the pool size as needed
        serverStartLatch.countDown();
        logger.info("Aggregation Server started on port " + port);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::removeExpiredData, 0, 1, TimeUnit.SECONDS);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(5000); // 5 second timeout for client operations
                threadPool.submit(() -> handleClient(clientSocket));
            } catch (SocketTimeoutException e) {
                // Timeout for accepting connections, continue to next iteration
            } catch (IOException e) {
                if (running) {
                    logger.warning("Error accepting client connection: " + e.getMessage());
                }
            }
        }

        threadPool.shutdown();
        scheduler.shutdown();
    }

    /**
     * Waits for the server to start within the specified timeout.
     *
     * @param timeout The maximum time to wait.
     * @param unit    The time unit of the timeout argument.
     * @return true if the server started within the timeout, false otherwise.
     * @throws InterruptedException If the current thread is interrupted while
     *                              waiting.
     */
    public boolean waitForServerStart(long timeout, TimeUnit unit) throws InterruptedException {
        return serverStartLatch.await(timeout, unit);
    }

    /**
     * Stops the server and shuts down all resources.
     * Saves the current weather data to the storage file.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warning("Error closing server socket: " + e.getMessage());
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        saveDataToFile();
        logger.info("Aggregation Server stopped.");
    }

    /**
     * Handles client connections and processes their requests.
     *
     * @param clientSocket The socket connected to the client.
     */
    private void handleClient(Socket clientSocket) {
        try (clientSocket;
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            if (requestLine != null) {
                String[] requestParts = requestLine.split(" ");
                logger.info("Received request: " + requestLine);
                if (requestParts.length == 3) {
                    String method = requestParts[0];
                    String path = requestParts[1];

                    if ("GET".equals(method) && path.startsWith("/weather.json")) {
                        handleGetRequest(out, path);
                    } else if ("PUT".equals(method)) {
                        handlePutRequest(in, out);
                    } else {
                        sendResponse(out, "400 Bad Request", "Invalid request");
                    }
                } else {
                    sendResponse(out, "400 Bad Request", "Invalid request format");
                }
            }
        } catch (IOException e) {
            logger.warning("Error handling client: " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Unexpected error handling client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles GET requests from clients.
     * If a station ID is provided in the path, returns the weather data for that
     * station.
     * Otherwise, returns a list of all weather data.
     *
     * @param out  The PrintWriter to send the response to the client.
     * @param path The request path.
     */
    private void handleGetRequest(PrintWriter out, String path) {
        synchronized (this) {
            lamportClock++; // Increment clock for local event
        }
        removeExpiredData();
        logger.info("Handling GET request. Path: " + path);

        String stationId = null;
        if (path.contains("?id=")) {
            stationId = path.split("\\?id=")[1];
        }

        if (stationId != null) {
            WeatherData data = weatherDataMap.get(stationId);
            if (data != null) {
                sendResponse(out, "200 OK", data.toJson());
                logger.info("Weather data available for station: " + stationId + ". Sending 200 OK with data.");
            } else {
                sendResponse(out, "404 Not Found", "No weather data available for station: " + stationId);
                logger.info("No weather data available for station: " + stationId + ". Sending 404 Not Found.");
            }
        } else {
            logger.info("Weather data map size: " + weatherDataMap.size());
            if (weatherDataMap.isEmpty()) {
                logger.info("No weather data available. Sending 204 No Content.");
                sendResponse(out, "204 No Content", null);
            } else {
                logger.info("Weather data available. Sending 200 OK with data.");
                List<WeatherData> sortedData = new ArrayList<>(recentUpdates);
                sortedData.sort(Comparator.comparingInt(WeatherData::getLamportClock).reversed());

                StringBuilder jsonResponse = new StringBuilder("[");
                for (WeatherData data : sortedData) {
                    jsonResponse.append(data.toJson()).append(",");
                }
                if (jsonResponse.charAt(jsonResponse.length() - 1) == ',') {
                    jsonResponse.setLength(jsonResponse.length() - 1); // Remove last comma
                }
                jsonResponse.append("]");
                sendResponse(out, "200 OK", jsonResponse.toString());
            }
        }
    }

    /**
     * Handles PUT requests from clients.
     * Parses the request body and updates the weather data for the specified
     * station.
     *
     * @param in  The BufferedReader to read the request from the client.
     * @param out The PrintWriter to send the response to the client.
     * @throws IOException If there is an error reading the request.
     */
    private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
        int contentLength = 0;
        int clientLamportClock = 0;

        // Read headers
        Map<String, Integer> headers = readHeaders(in);
        contentLength = headers.getOrDefault("Content-Length", 0);
        clientLamportClock = headers.getOrDefault("Lamport-Clock", 0);

        // Read the body
        String jsonData = readBody(in, contentLength);
        logger.info("Received PUT request with data: " + jsonData);

        // Update Lamport clock for receiving the request
        updateLamportClock(clientLamportClock);

        // Handle case where no content is sent
        if (jsonData.isEmpty()) {
            sendResponse(out, "204 No Content", null);
            logger.info("No content sent in PUT request. Sending 204 No Content.");
            return;
        }

        // Process weather data
        processWeatherData(jsonData, out);
    }

    /**
     * Reads headers from the request and extracts Content-Length and Lamport-Clock
     * values.
     *
     * @param in The BufferedReader to read the request from the client.
     * @return A map containing the Content-Length and Lamport-Clock values.
     * @throws IOException If there is an error reading the request.
     */
    private Map<String, Integer> readHeaders(BufferedReader in) throws IOException {
        Map<String, Integer> headers = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                headers.put("Content-Length", Integer.parseInt(line.substring("content-length:".length()).trim()));
            } else if (line.toLowerCase().startsWith("lamport-clock:")) {
                headers.put("Lamport-Clock", Integer.parseInt(line.substring("lamport-clock:".length()).trim()));
            }
        }
        return headers;
    }

    /**
     * Reads the body of the request based on the Content-Length.
     *
     * @param in            The BufferedReader to read the request from the client.
     * @param contentLength The length of the content to read.
     * @return The body of the request as a string.
     * @throws IOException If there is an error reading the request.
     */
    private String readBody(BufferedReader in, int contentLength) throws IOException {
        StringBuilder requestBody = new StringBuilder();
        if (contentLength > 0) {
            char[] body = new char[contentLength];
            int charsRead = in.read(body, 0, contentLength);
            requestBody.append(body, 0, charsRead);
        }
        return requestBody.toString().trim();
    }

    /**
     * Updates the Lamport clock based on the received clock value.
     *
     * @param clientLamportClock The Lamport clock value received from the client.
     */
    private void updateLamportClock(int clientLamportClock) {
        synchronized (this) {
            lamportClock = Math.max(lamportClock, clientLamportClock) + 1;
        }
    }

    /**
     * Processes the weather data received in the PUT request.
     * Updates the weather data map and recent updates queue, and saves the data to
     * the file.
     *
     * @param jsonData The JSON data received in the PUT request.
     * @param out      The PrintWriter to send the response to the client.
     */
    private void processWeatherData(String jsonData, PrintWriter out) {
        try {
            WeatherData weatherData = WeatherData.fromInputFormat(jsonData);
            weatherData.setLamportClock(lamportClock);

            String stationId = weatherData.getId();
            boolean isNewContentServer = !weatherDataMap.containsKey(stationId);
            boolean isFirstDataEver = weatherDataMap.isEmpty() && !Files.exists(Paths.get(STORAGE_FILE));

            synchronized (weatherDataMap) {
                weatherDataMap.put(stationId, weatherData);
                lastUpdateTime.put(stationId, System.currentTimeMillis());

                synchronized (recentUpdates) {
                    recentUpdates.removeIf(data -> data.getId().equals(stationId));
                    recentUpdates.offer(weatherData);

                    if (recentUpdates.size() > MAX_STORED_UPDATES) {
                        WeatherData oldestUpdate = recentUpdates.poll();
                        if (oldestUpdate != null) {
                            weatherDataMap.remove(oldestUpdate.getId());
                            lastUpdateTime.remove(oldestUpdate.getId());
                        }
                    }
                }
            }

            saveDataToFile();

            if (isNewContentServer || isFirstDataEver) {
                sendResponse(out, "201 Created", "Data created successfully");
                logger.info("New content server connected or first data ever received. Station: " + stationId);
            } else {
                sendResponse(out, "200 OK", "Data updated successfully");
                logger.info("Weather data updated for station: " + stationId);
            }
        } catch (JsonParseException | IllegalArgumentException e) {
            logger.warning("Invalid input data: " + e.getMessage());
            sendResponse(out, "500 Internal Server Error", "Invalid input data: " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Error processing request: " + e.getMessage());
            sendResponse(out, "500 Internal Server Error", "Error processing request: " + e.getMessage());
        }
    }

    /**
     * Sends an HTTP response to the client.
     *
     * @param out    The PrintWriter to send the response to the client.
     * @param status The HTTP status code and message.
     * @param body   The response body, or null if there is no body.
     */
    private void sendResponse(PrintWriter out, String status, String body) {
        int responseClock;
        synchronized (this) {
            lamportClock++;
            responseClock = lamportClock;
        }
        out.println("HTTP/1.1 " + status);
        out.println("Content-Type: application/json");
        out.println("Lamport-Clock: " + responseClock);
        if (body != null) {
            out.println("Content-Length: " + body.length());
            out.println();
            out.println(body);
        } else {
            out.println("Content-Length: 0");
            out.println();
        }
        out.flush();
        logger.info("Sent response: " + status + " with Lamport clock: " + responseClock);
    }

    /**
     * Removes expired weather data from the server.
     * Data is considered expired if it has not been updated within the specified
     * expiration time.
     */
    private void removeExpiredData() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredStations = new ArrayList<>();

        synchronized (lastUpdateTime) {
            for (Map.Entry<String, Long> entry : lastUpdateTime.entrySet()) {
                if (currentTime - entry.getValue() > DATA_EXPIRATION_SECONDS * 1000) {
                    expiredStations.add(entry.getKey());
                }
            }

            for (String stationId : expiredStations) {
                weatherDataMap.remove(stationId);
                lastUpdateTime.remove(stationId);
                synchronized (recentUpdates) {
                    recentUpdates.removeIf(data -> data.getId().equals(stationId));
                }
                logger.info("Removed expired data for station: " + stationId);
            }

            if (!expiredStations.isEmpty()) {
                saveDataToFile();
            }
        }
    }

    /**
     * Checks if the server is currently running.
     *
     * @return true if the server is running, false otherwise.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the port number on which the server is listening.
     *
     * @return The port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * The main method to start the AggregationServer.
     *
     * @param args Command line arguments. The first argument can be the port
     *             number.
     * @throws JsonParseException If there is an error parsing the JSON data.
     */
    public static void main(String[] args) throws JsonParseException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port " + DEFAULT_PORT);
            }
        }

        AggregationServer server = new AggregationServer(port);
        try {
            server.start();
        } catch (IOException e) {
            server.logger.severe("Error starting server: " + e.getMessage());
        }
    }

    /**
     * Clears all weather data from the server.
     */
    public void clearAllData() {
        weatherDataMap.clear();
        lastUpdateTime.clear();
        recentUpdates.clear();
        logger.info("All data cleared from server.");
    }
}