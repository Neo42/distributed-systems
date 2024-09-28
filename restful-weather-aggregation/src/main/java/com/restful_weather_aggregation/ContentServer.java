package com.restful_weather_aggregation;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ContentServer {
  private static final Logger logger = Logger.getLogger(ContentServer.class.getName());
  private int lamportClock = 0;
  private final String serverUrl;
  private final String filePath;
  private static final int MAX_RETRIES = 3;
  private static final int RETRY_DELAY_MS = 3000;

  public ContentServer(String serverUrl, String filePath) {
    this.serverUrl = serverUrl;
    this.filePath = filePath;
  }

  public void uploadData() throws IOException, IllegalArgumentException, JsonParseException {
    String fileContent = readWeatherDataFromFile();
    Map<String, String> weatherData = parseWeatherData(fileContent);
    String jsonData = convertToJSON(weatherData);

    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        HttpURLConnection connection = createConnection();
        sendPutRequest(connection, jsonData);
        verifyUploadedData(connection, weatherData);
        break;
      } catch (IOException e) {
        logger.warning("Attempt " + (attempt + 1) + " failed: " + e.getMessage());
        if (attempt == MAX_RETRIES - 1) {
          throw e;
        }
        try {
          Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted during retry", ie);
        }
      }
    }
  }

  private String readWeatherDataFromFile() throws IOException {
    return new String(Files.readAllBytes(Paths.get(filePath)));
  }

  private Map<String, String> parseWeatherData(String data) throws IllegalArgumentException {
    Map<String, String> weatherData = new HashMap<>();
    String[] lines = data.split("\n");
    for (String line : lines) {
      String[] parts = line.split(":", 2); // Split only on the first colon
      if (parts.length == 2) {
        String key = parts[0].trim();
        String value = parts[1].trim();
        if (key.isEmpty() || value.isEmpty()) {
          throw new IllegalArgumentException("Invalid data format: empty key or value");
        }
        weatherData.put(key, value);
      } else {
        throw new IllegalArgumentException("Invalid data format: " + line);
      }
    }
    if (!weatherData.containsKey("id")) {
      throw new IllegalArgumentException("Missing required field: id");
    }
    return weatherData;
  }

  private String convertToJSON(Map<String, String> weatherData) {
    WeatherData wd = new WeatherData(weatherData.get("id"), weatherData.get("name"));
    wd.setLamportClock(getLamportClock());
    for (Map.Entry<String, String> entry : weatherData.entrySet()) {
      if (!entry.getKey().equals("id") && !entry.getKey().equals("name")) {
        wd.addData(entry.getKey(), entry.getValue());
      }
    }
    return JsonParser.toJson(wd);
  }

  protected HttpURLConnection createConnection() throws IOException {
    URL url = new URL(serverUrl + "/weather.json");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("PUT");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Lamport-Clock", String.valueOf(getLamportClock()));
    connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
    connection.setDoOutput(true);
    return connection;
  }

  public void sendPutRequest(HttpURLConnection connection, String jsonData) throws IOException {
    incrementLamportClock();
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Content-Length", String.valueOf(jsonData.getBytes().length));
    connection.setRequestProperty("Lamport-Clock", String.valueOf(getLamportClock()));

    try (OutputStream os = connection.getOutputStream()) {
      byte[] input = jsonData.getBytes("utf-8");
      os.write(input, 0, input.length);
    }

    int responseCode = connection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
      throw new IOException("PUT request failed. Response Code: " + responseCode);
    }
  }

  protected void verifyUploadedData(HttpURLConnection connection, Map<String, String> originalData)
      throws IOException, JsonParseException {
    URL getUrl = new URL(serverUrl + "/weather.json");
    HttpURLConnection getConnection = (HttpURLConnection) getUrl.openConnection();
    getConnection.setRequestMethod("GET");
    getConnection.setRequestProperty("User-Agent", "ATOMClient/1/0");

    int responseCode = getConnection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new IOException("Failed to verify uploaded data. GET Response Code: " + responseCode);
    }

    StringBuilder response = new StringBuilder();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(getConnection.getInputStream()))) {
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
    }
    String jsonResponse = response.toString();

    // Log the response for debugging
    System.out.println("Server response: " + jsonResponse);

    try {
      Map<String, String> retrievedData = parseJSONResponse(jsonResponse);
      System.out.println("Original data: " + originalData);
      System.out.println("Retrieved data: " + retrievedData);

      if (!originalData.equals(retrievedData)) {
        System.out.println("Data mismatch. Differences:");
        for (String key : originalData.keySet()) {
          if (!retrievedData.containsKey(key) || !originalData.get(key).equals(retrievedData.get(key))) {
            System.out.println(key + ": Original=" + originalData.get(key) + ", Retrieved=" + retrievedData.get(key));
          }
        }
        for (String key : retrievedData.keySet()) {
          if (!originalData.containsKey(key)) {
            System.out.println(key + ": Original=null, Retrieved=" + retrievedData.get(key));
          }
        }
        throw new IOException("Uploaded data does not match the data on the server");
      }
    } catch (JsonParseException e) {
      System.err.println("Failed to parse JSON response: " + e.getMessage());
      System.err.println("Raw response: " + jsonResponse);
      throw e;
    }
  }

  private Map<String, String> parseJSONResponse(String jsonResponse) throws JsonParseException {
    try {
      // Check if the response is an array
      if (jsonResponse.trim().startsWith("[") && jsonResponse.trim().endsWith("]")) {
        // Remove the square brackets
        jsonResponse = jsonResponse.substring(1, jsonResponse.length() - 1);
      }

      WeatherData wd = JsonParser.fromJson(jsonResponse);
      Map<String, String> data = new HashMap<>();
      data.put("id", wd.getId());
      data.put("name", wd.getName());
      for (Map.Entry<String, Object> entry : wd.getData().entrySet()) {
        data.put(entry.getKey(), entry.getValue().toString());
      }
      return data;
    } catch (Exception e) {
      throw new JsonParseException("Failed to parse JSON: " + e.getMessage());
    }
  }

  public void incrementLamportClock() {
    lamportClock++;
  }

  public int getLamportClock() {
    return lamportClock;
  }

  public static void main(String[] args) throws IOException, IllegalArgumentException, JsonParseException {
    if (args.length != 2) {
      System.err.println("Usage: java ContentServer <server_url> <file_path>");
      System.exit(1);
    }

    ContentServer contentServer = new ContentServer(args[0], args[1]);
    try {
      contentServer.uploadData();
      System.out.println("Data uploaded successfully.");
    } catch (IOException | IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }
}