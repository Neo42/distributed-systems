package com.restful_weather_aggregation;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.logging.*;

public class AggregationServer {
  private static final int DEFAULT_PORT = 4567;
  private static final int DATA_EXPIRATION_SECONDS = 30;
  private static final int MAX_STORED_UPDATES = 20;
  private static final String STORAGE_FILE = "weather_data.txt";
  private static final String TEMP_FILE = "weather_data_temp.txt";
  private static final String LOG_FILE = "weather_data.log";

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

  private void setupLogger() {
    try {
      // Specify the number of log files to use
      int numLogFiles = 5;
      // Specify the maximum size of each log file in bytes
      int fileSizeLimit = 1_000_000; // 1 MB

      FileHandler fileHandler = new FileHandler(LOG_FILE, fileSizeLimit, numLogFiles, true);
      fileHandler.setFormatter(new SimpleFormatter());
      logger.addHandler(fileHandler);

      // Optionally, set the logger level
      logger.setLevel(Level.INFO);
    } catch (IOException e) {
      System.err.println("Failed to set up logger: " + e.getMessage());
    }
  }

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

  public boolean waitForServerStart(long timeout, TimeUnit unit) throws InterruptedException {
    return serverStartLatch.await(timeout, unit);
  }

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

  private void handleClient(Socket clientSocket) {
    try (clientSocket;
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

      String requestLine = in.readLine();
      if (requestLine != null) {
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length == 3) {
          String method = requestParts[0];
          String path = requestParts[1];

          if ("GET".equals(method) && "/weather.json".equals(path)) {
            handleGetRequest(out);
          } else if ("PUT".equals(method) && "/weather.json".equals(path)) {
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

  private void handleGetRequest(PrintWriter out) {
    removeExpiredData();
    logger.info("Handling GET request. Weather data map size: " + weatherDataMap.size());
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

  private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
    StringBuilder requestBody = new StringBuilder();
    String line;
    int contentLength = 0;

    // Read headers
    while ((line = in.readLine()) != null && !line.isEmpty()) {
      if (line.toLowerCase().startsWith("content-length:")) {
        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
      }
    }

    // Read the body
    if (contentLength > 0) {
      char[] body = new char[contentLength];
      int charsRead = in.read(body, 0, contentLength);
      requestBody.append(body, 0, charsRead);
    }

    String jsonData = requestBody.toString().trim();
    logger.info("Received PUT request with data: " + jsonData);

    try {
      WeatherData weatherData = WeatherData.fromInputFormat(jsonData);
      synchronized (this) {
        lamportClock++;
        weatherData.setLamportClock(lamportClock);
      }

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

  private void sendResponse(PrintWriter out, String status, String body) {
    out.println("HTTP/1.1 " + status);
    out.println("Content-Type: application/json");
    if (body != null) {
      out.println("Content-Length: " + body.length());
      out.println();
      out.println(body);
    } else {
      out.println("Content-Length: 0");
      out.println();
    }
    out.flush(); // Ensure the response is sent immediately
    logger.info("Sent response: " + status);
  }

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

  public boolean isRunning() {
    return running;
  }

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

  public void clearAllData() {
    weatherDataMap.clear();
    lastUpdateTime.clear();
    recentUpdates.clear();
    logger.info("All data cleared from server.");
  }
}