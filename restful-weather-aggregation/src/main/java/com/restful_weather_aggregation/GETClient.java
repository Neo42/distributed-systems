package com.restful_weather_aggregation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GETClient {

  private int lamportClock = 0;
  private static final int MAX_RETRIES = 3;
  private static final int RETRY_DELAY_MS = 3000;
  private static final Logger logger = Logger.getLogger(GETClient.class.getName());

  public static void main(String[] args) {
    GETClient client = new GETClient();
    try {
      ServerConfig config = client.parseCommandLineArgs(args);
      HttpURLConnection connection = client.createConnection(config.serverUrl, config.stationId);
      client.sendGetRequestWithRetry(connection);
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
    }
  }

  public void sendGetRequest(HttpURLConnection connection) throws IOException {
    logger.info("Sending GET request to: " + connection.getURL());
    int responseCode = connection.getResponseCode();
    logger.info("Received response code: " + responseCode);

    if (responseCode == 200) {
      BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      String inputLine;
      StringBuilder response = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();
      String responseString = response.toString();
      logger.info("Received response: " + responseString + "\n");
      displayWeatherData(responseString);
    } else if (responseCode == 204) {
      logger.info("No content available");
      System.out.println("No content available");
    } else {
      logger.warning("Error: Server returned status code " + responseCode);
      System.out.println("Error: Server returned status code " + responseCode);
    }
  }

  public void sendGetRequestWithRetry(HttpURLConnection connection) throws IOException {
    int retries = 0;
    IOException lastException = null;
    String url = connection.getURL().toString();
    logger.info("Starting GET request for URL: " + url);

    while (retries < MAX_RETRIES) {
      try {
        if (retries == 0) {
          logger.info("Initial attempt");
        } else {
          logger.info("Retry " + retries);
        }
        sendGetRequest(connection);
        return; // If successful, exit the method
      } catch (IOException e) {
        lastException = e;
        logger.log(Level.WARNING, "Request failed. Exception: " + e.getMessage());
        retries++;
        if (retries >= MAX_RETRIES) {
          break; // Exit the loop if max retries reached
        }
        try {
          Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IOException("Retry interrupted", ie);
        }
        // Recreate the connection for the retry
        connection = createConnection(url);
      }
    }
    // If we've exhausted all retries, throw the last exception
    if (lastException != null) {
      logger.log(Level.SEVERE, "All retries failed");
      throw lastException;
    }
  }

  public HttpURLConnection createConnection(String serverUrl) throws IOException {
    URL url = new URL(serverUrl);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Lamport-Clock", String.valueOf(++lamportClock));
    return connection;
  }

  public HttpURLConnection createConnection(String serverUrl, String stationId) throws IOException {
    String urlString = serverUrl + "/weather.json" + (stationId != null ? "?id=" + stationId : "");
    URL url = new URL(urlString);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Lamport-Clock", String.valueOf(++lamportClock));
    return connection;
  }

  public ServerConfig parseCommandLineArgs(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: java GETClient <server-url> [station-id]");
    }
    String serverUrl = args[0];
    String stationId = args.length > 1 ? args[1] : null;
    return new ServerConfig(serverUrl, stationId);
  }

  private void displayWeatherData(String jsonData) {
    jsonData = jsonData.trim();
    if (jsonData.startsWith("[") && jsonData.endsWith("]")) {
      // It's an array, so we'll display each object
      jsonData = jsonData.substring(1, jsonData.length() - 1);
      String[] objects = jsonData.split("\\},\\{");
      for (int i = 0; i < objects.length; i++) {
        String object = objects[i];
        if (!object.startsWith("{"))
          object = "{" + object;
        if (!object.endsWith("}"))
          object = object + "}";
        System.out.println("Weather Data " + (i + 1) + ":");
        displayJsonObject(object);
        System.out.println(); // Add a blank line between objects
      }
    } else if (jsonData.startsWith("{") && jsonData.endsWith("}")) {
      // It's a single object
      displayJsonObject(jsonData);
    } else {
      System.out.println("Error: Invalid JSON data");
    }
  }

  private void displayJsonObject(String jsonObject) {
    jsonObject = jsonObject.substring(1, jsonObject.length() - 1);
    String[] pairs = jsonObject.split(",");
    for (String pair : pairs) {
      String[] keyValue = pair.split(":", 2);
      if (keyValue.length == 2) {
        String key = keyValue[0].trim().replace("\"", "");
        String value = keyValue[1].trim().replace("\"", "");
        System.out.println(key + ": " + value);
      }
    }
  }

  public static class ServerConfig {
    public final String serverUrl;
    public final String stationId;

    public ServerConfig(String serverUrl, String stationId) {
      this.serverUrl = serverUrl;
      this.stationId = stationId;
    }
  }
}