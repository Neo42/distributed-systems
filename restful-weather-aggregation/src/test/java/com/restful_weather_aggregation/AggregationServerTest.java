package com.restful_weather_aggregation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test class for the Aggregation Server.
 * This class contains unit tests for various functionalities of the Aggregation
 * Server.
 */
public class AggregationServerTest {

  private static final int TEST_PORT = 4567;
  private static final String TEST_HOST = "localhost";
  private static final int TIMEOUT_SECONDS = 31;
  private static final int MAX_STORED_UPDATES = 20;
  private static final double BASE_TEMPERATURE = 20.0;
  private static final String WEATHER_ENDPOINT = "/weather.json";
  private static final String STORAGE_FILE = "aggregation_server_data.txt";
  private static final String HTTP_GET = "GET";
  private static final String HTTP_PUT = "PUT";
  private static final String HTTP_VERSION = "HTTP/1.1";
  private static final String CONTENT_TYPE_JSON = "Content-Type: application/json";
  private static final String CONTENT_LENGTH = "Content-Length: ";

  private AggregationServer server;

  /**
   * Set up the test environment before each test.
   * Initializes the server and creates a client socket.
   * 
   * @throws IOException
   * @throws InterruptedException
   * @throws TimeoutException
   * @throws JsonParseException
   */
  @BeforeEach
  public void setUp() throws IOException, InterruptedException, TimeoutException, JsonParseException {
    Files.write(Paths.get(STORAGE_FILE), new byte[0]);

    server = new AggregationServer(TEST_PORT);
    server.clearAllData(); // Clear data at the start of each test
    new Thread(() -> {
      try {
        server.start();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }).start();

    boolean serverStarted = server.waitForServerStart(30, TimeUnit.SECONDS);
    if (!serverStarted) {
      throw new TimeoutException("Server did not start within the specified timeout.");
    }

    Thread.sleep(1000); // Add a small delay to ensure the server is fully started
  }

  /**
   * Clean up the test environment after each test.
   * Closes the client socket and shuts down the server.
   * 
   * @throws IOException
   * @throws InterruptedException
   */
  @AfterEach
  public void tearDown() throws IOException, InterruptedException {
    if (server != null) {
      server.stop();
      // Wait for the server to fully stop
      Thread.sleep(2000);
      server = null; // Ensure the server instance is garbage collected
    }
    // Delete the storage file to ensure a clean state for the next test
    Files.deleteIfExists(Paths.get(STORAGE_FILE));
  }

  /**
   * Test if the server starts successfully on the specified port.
   */
  @Test
  public void testServerStartup() {
    assertTrue(server.isRunning(), "Server should be running after startup");
  }

  /**
   * Test if the server correctly handles a PUT request, create a storage file and
   * stores the data.
   * 
   * @throws IOException
   */
  @Test
  public void testPutRequest() throws IOException {
    String jsonData = createJsonData("IDS60901", "Test Station", BASE_TEMPERATURE);
    String putRequest = createPutRequest(jsonData);

    String response = sendRequest(putRequest);
    System.out.println("PUT Response: " + response);
    assertTrue(response.contains(HTTP_VERSION + " 201 Created"),
        "Server should return 201 Created for a successful PUT request. Actual response: " + response);

    // Optionally, you can add a GET request to verify the data was stored
    String getResponse = sendRequest(createGetRequest());
    assertTrue(getResponse.contains("\"id\":\"IDS60901\""),
        "GET response should contain the PUT data");
  }

  /**
   * Test if the server correctly handles subsequent PUT requests with a 200 OK
   * response.
   * 
   * @throws IOException
   */
  @Test
  public void testSubsequentPutRequests() throws IOException {
    String jsonData1 = createJsonData("IDS60901", "Test Station 1", 20.0);
    String jsonData2 = createJsonData("IDS60901", "Test Station 1", 21.0);
    String jsonData3 = createJsonData("IDS60902", "Test Station 2", 20.0);

    String response1 = sendRequest(createPutRequest(jsonData1));
    String response2 = sendRequest(createPutRequest(jsonData2));
    String response3 = sendRequest(createPutRequest(jsonData3));

    assertTrue(response1.contains(HTTP_VERSION + " 201 Created"),
        "Server should return 201 Created for the first PUT request");
    assertTrue(response2.contains(HTTP_VERSION + " 200 OK"),
        "Server should return 200 OK for subsequent PUT requests to the same station");
    assertTrue(response3.contains(HTTP_VERSION + " 201 Created"),
        "Server should return 201 Created for a new station");
  }

  /**
   * Test if the server correctly handles a GET request after data has been PUT.
   * 
   * @throws IOException
   */
  @Test
  public void testGetRequestAfterPut() throws IOException {
    String jsonData = createJsonData("IDS60901", "Test Station", BASE_TEMPERATURE);
    String putRequest = createPutRequest(jsonData);
    sendRequest(putRequest);

    String getResponse = sendRequest(createGetRequest());
    assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
        "Server should return 200 OK for GET request after PUT");
    assertTrue(getResponse.contains("\"id\":\"IDS60901\""),
        "GET response should contain the PUT data");
  }

  /**
   * Test if the server correctly removes data from inactive content servers.
   * 
   * @throws InterruptedException
   * @throws IOException
   */
  @Test
  public void testDataExpirationAfterTimeout() throws InterruptedException, IOException {
    String jsonData = createJsonData("IDS60901", "Test Station", BASE_TEMPERATURE);
    String putRequest = createPutRequest(jsonData);
    sendRequest(putRequest);

    Thread.sleep(TIMEOUT_SECONDS * 1000);

    String getResponse = sendRequest(createGetRequest());
    assertTrue(getResponse.contains(HTTP_VERSION + " 404 Not Found"),
        "Server should return 404 Not Found after data expiration");
  }

  /* Test if the server correctly handles a PUT request when no content is sent */
  @Test
  public void testPutRequestWithNoContent() throws IOException {
    String putRequest = "PUT " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
        CONTENT_TYPE_JSON + "\r\n" +
        CONTENT_LENGTH + "0\r\n\r\n";

    String response = sendRequest(putRequest);
    assertTrue(response.contains(HTTP_VERSION + " 204 No Content"),
        "Server should return 204 No Content when no content is sent. Actual response: " + response);
  }

  /**
   * Test if the server correctly handles multiple simultaneous GET and PUT
   * requests.
   */
  @Test
  public void testConcurrentRequests() throws InterruptedException, IOException {
    int numThreads = 10;
    CountDownLatch latch = new CountDownLatch(numThreads);
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    for (int i = 0; i < numThreads; i++) {
      final int index = i;
      executor.submit(() -> {
        try {
          String jsonData = createJsonData("IDS6090" + index, "Test Station " + index, BASE_TEMPERATURE + index);
          String putRequest = createPutRequest(jsonData);
          String response = sendRequest(putRequest);
          System.out.println("Concurrent PUT response for station " + index + ": " + response);
          assertTrue(response.contains(HTTP_VERSION + " 200 OK") || response.contains(HTTP_VERSION + " 201 Created"),
              "All requests should be successful. Actual response: " + response);
        } catch (IOException e) {
          e.printStackTrace();
          fail("Exception occurred during concurrent request: " + e.getMessage());
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    String getResponse = sendRequest(createGetRequest());
    System.out.println("GET response after concurrent requests: " + getResponse);
    assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
        "GET request after concurrent PUTs should return 200 OK");
  }

  /**
   * Test if the server correctly implements Lamport clocks for request ordering.
   * 
   * @throws IOException
   * @throws InterruptedException
   */
  @Test
  public void testLamportClockOrdering() throws IOException, InterruptedException {
    String jsonData1 = createJsonData("IDS60901", "Station 1", 20.5);
    String jsonData2 = createJsonData("IDS60902", "Station 2", 25.0);
    String jsonData3 = createJsonData("IDS60903", "Station 3", 22.5);

    sendRequest(createPutRequest(jsonData1));
    Thread.sleep(100); // Ensure different timestamps
    sendRequest(createPutRequest(jsonData2));
    Thread.sleep(100);
    sendRequest(createPutRequest(jsonData3));

    String getResponse = sendRequest(createGetRequest());
    System.out.println("GET response: " + getResponse);

    assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
        "Server should return 200 OK for GET request");
    assertTrue(getResponse.indexOf("Station 3") < getResponse.indexOf("Station 2") &&
        getResponse.indexOf("Station 2") < getResponse.indexOf("Station 1"),
        "Data should be ordered according to Lamport clock values (descending order)");
  }

  /**
   * Test if the server correctly handles an invalid request method.
   * 
   * @throws IOException
   */
  @Test
  public void testInvalidRequestMethod() throws IOException {
    String invalidRequest = "POST /weather.json HTTP/1.1\r\n\r\n";
    String response = sendRequest(invalidRequest);

    System.out.println("Invalid method response: " + response);

    assertTrue(response.contains("HTTP/1.1 400 Bad Request"),
        "Server should return 400 Bad Request for an invalid request method. Actual response: " + response);
  }

  /**
   * Test if the server correctly handles an invalid request format.
   * 
   * @throws IOException
   */
  @Test
  public void testInvalidRequestFormat() throws IOException {
    String invalidRequest = "GET /invalid_endpoint HTTP/1.1\r\n\r\n";
    String response = sendRequest(invalidRequest);

    System.out.println("Invalid format response: " + response);

    assertTrue(response.contains("HTTP/1.1 400 Bad Request"),
        "Server should return 400 Bad Request for an invalid request format. Actual response: " + response);
  }

  /**
   * Test if the server correctly recovers from a simulated crash.
   * 
   * @throws IOException
   * @throws InterruptedException
   * @throws TimeoutException
   * @throws JsonParseException
   */
  @Test
  public void testServerRecoveryAfterCrash()
      throws IOException, InterruptedException, TimeoutException, JsonParseException {
    String jsonData = createJsonData("IDS60901", "Test Station", BASE_TEMPERATURE);
    String putRequest = createPutRequest(jsonData);
    String putResponse = sendRequest(putRequest);
    System.out.println("PUT response before crash: " + putResponse);

    // Simulate server crash
    server.stop();

    // Restart the server
    server = new AggregationServer(TEST_PORT);
    new Thread(() -> {
      try {
        server.start();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }).start();

    boolean serverStarted = server.waitForServerStart(30, TimeUnit.SECONDS);
    if (!serverStarted) {
      throw new TimeoutException("Server did not restart within the specified timeout.");
    }

    Thread.sleep(1000); // Add a small delay to ensure the server is fully started

    String getResponse = sendRequest(createGetRequest());
    System.out.println("GET response after restart: " + getResponse);

    assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
        "Server should return 200 OK after recovery. Actual response: " + getResponse);
    assertTrue(getResponse.contains("\"id\":\"IDS60901\""),
        "Data should be preserved after server recovery. Actual response: " + getResponse);
  }

  /**
   * Test if the server correctly handles malformed JSON data in PUT requests.
   * 
   * @throws IOException
   */
  @Test
  public void testMalformedJsonHandling() throws IOException {
    String malformedJson = "{\"id\":\"IDS60901\",\"name\":\"Test Station\",\"air_temp\":}";

    String putRequest = createPutRequest(malformedJson);

    String response = sendRequest(putRequest);
    System.out.println("Malformed JSON response: " + response);

    assertTrue(response.contains(HTTP_VERSION + " 500 Internal Server Error"),
        "Server should return 500 Internal Server Error for malformed JSON. Actual response: " + response);
  }

  /**
   * Test if the server correctly limits stored data to the most recent 20
   * updates.
   * 
   * @throws IOException
   * @throws InterruptedException
   */
  @Test
  public void testDataLimitTo20Updates() throws IOException, InterruptedException {
    for (int i = 0; i < 25; i++) {
      String jsonData = createJsonData("IDS6090" + i, "Test Station " + i, BASE_TEMPERATURE + i);
      String putRequest = createPutRequest(jsonData);
      String putResponse = sendRequest(putRequest);
      System.out.println("PUT response for station " + i + ": " + putResponse);
      Thread.sleep(100); // Ensure different timestamps
    }

    String getResponse = sendRequest(createGetRequest());
    System.out.println("GET response: " + getResponse);

    String[] ids = getResponse.split("\"id\":\"IDS6090");
    System.out.println("Number of IDs found: " + (ids.length - 1));

    assertEquals(MAX_STORED_UPDATES + 1, ids.length,
        "There should be 20 weather stations in the response. Actual response: " + getResponse);

    // Check that only the most recent 20 updates are present
    for (int i = 24; i >= 5; i--) {
      assertTrue(getResponse.contains("\"id\":\"IDS6090" + i + "\""),
          "Response should contain the most recent 20 updates. Missing: IDS6090" + i);
    }

    // Check that the oldest 5 updates are not present
    for (int i = 0; i < 5; i++) {
      assertFalse(getResponse.contains("\"id\":\"IDS6090" + i + "\""),
          "Response should not contain the oldest 5 updates. Found: IDS6090" + i);
    }

    // Verify that the stations are in the correct order (most recent first)
    int lastIndex = -1;
    for (int i = 24; i >= 5; i--) {
      int currentIndex = getResponse.indexOf("\"id\":\"IDS6090" + i + "\"");
      assertTrue(currentIndex > lastIndex,
          "Stations should be ordered from most recent to oldest. Incorrect order for IDS6090" + i);
      lastIndex = currentIndex;
    }
  }

  /**
   * Test if the server correctly handles fully formatted weather data.
   * 
   * @throws IOException
   */
  @Test
  public void testFullyFormattedWeatherData() throws IOException {
    String fullyFormattedData = "{"
        + "\"id\": \"IDS60901\","
        + "\"name\": \"Adelaide (West Terrace / ngayirdapira)\","
        + "\"state\": \"SA\","
        + "\"time_zone\": \"CST\","
        + "\"lat\": -34.9,"
        + "\"lon\": 138.6,"
        + "\"local_date_time\": \"15/04:00pm\","
        + "\"local_date_time_full\": \"20230715160000\","
        + "\"air_temp\": 13.3,"
        + "\"apparent_t\": 9.5,"
        + "\"cloud\": \"Partly cloudy\","
        + "\"dewpt\": 5.7,"
        + "\"press\": 1023.9,"
        + "\"rel_hum\": 60,"
        + "\"wind_dir\": \"S\","
        + "\"wind_spd_kmh\": 15,"
        + "\"wind_spd_kt\": 8"
        + "}";

    String putRequest = createPutRequest(fullyFormattedData);
    String putResponse = sendRequest(putRequest);
    System.out.println("PUT response for fully formatted data: " + putResponse);

    assertTrue(putResponse.contains(HTTP_VERSION + " 201 Created") || putResponse.contains(HTTP_VERSION + " 200 OK"),
        "Server should return 201 Created or 200 OK for a successful PUT request. Actual response: " + putResponse);

    String getResponse = sendRequest(createGetRequest());
    System.out.println("GET response for fully formatted data: " + getResponse);

    assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
        "Server should return 200 OK for GET request after PUT");
    assertTrue(getResponse.contains("\"id\":\"IDS60901\""),
        "GET response should contain the station ID");
    assertTrue(getResponse.contains("\"name\":\"Adelaide (West Terrace / ngayirdapira)\""),
        "GET response should contain the station name");
    assertTrue(getResponse.contains("\"state\":\"SA\""),
        "GET response should contain the state");
    assertTrue(getResponse.contains("\"time_zone\":\"CST\""),
        "GET response should contain the time zone");
    assertTrue(getResponse.contains("\"lat\":-34.9"),
        "GET response should contain the latitude");
    assertTrue(getResponse.contains("\"lon\":138.6"),
        "GET response should contain the longitude");
    assertTrue(getResponse.contains("\"local_date_time\":\"15/04:00pm\""),
        "GET response should contain the local date time");
    assertTrue(getResponse.contains("\"local_date_time_full\":\"20230715160000\""),
        "GET response should contain the full local date time");
    assertTrue(getResponse.contains("\"air_temp\":13.3"),
        "GET response should contain the air temperature");
    assertTrue(getResponse.contains("\"apparent_t\":9.5"),
        "GET response should contain the apparent temperature");
    assertTrue(getResponse.contains("\"cloud\":\"Partly cloudy\""),
        "GET response should contain the cloud condition");
    assertTrue(getResponse.contains("\"dewpt\":5.7"),
        "GET response should contain the dew point");
    assertTrue(getResponse.contains("\"press\":1023.9"),
        "GET response should contain the pressure");
    assertTrue(getResponse.contains("\"rel_hum\":60"),
        "GET response should contain the relative humidity");
    assertTrue(getResponse.contains("\"wind_dir\":\"S\""),
        "GET response should contain the wind direction");
    assertTrue(getResponse.contains("\"wind_spd_kmh\":15"),
        "GET response should contain the wind speed in km/h");
    assertTrue(getResponse.contains("\"wind_spd_kt\":8"),
        "GET response should contain the wind speed in knots");
  }

  /**
   * Test if the Lamport clock increments on each PUT request.
   * 
   * @throws IOException
   */
  @Test
  public void testLamportClockIncrementOnPut() throws IOException {
    String jsonData1 = createJsonData("IDS60901", "Station 1", 20.5);
    String jsonData2 = createJsonData("IDS60902", "Station 2", 21.0);

    String response1 = sendRequest(createPutRequest(jsonData1));
    int clock1 = extractLamportClock(response1);

    String response2 = sendRequest(createPutRequest(jsonData2));
    int clock2 = extractLamportClock(response2);

    assertTrue(clock2 > clock1, "Lamport clock should increment on each PUT request");
  }

  /**
   * Test if the Lamport clock increments on each GET request.
   * 
   * @throws IOException
   */
  @Test
  public void testLamportClockIncrementOnGet() throws IOException {
    String jsonData = createJsonData("IDS60901", "Station 1", 20.5);
    sendRequest(createPutRequest(jsonData));

    String response1 = sendRequest(createGetRequest());
    int clock1 = extractLamportClock(response1);

    String response2 = sendRequest(createGetRequest());
    int clock2 = extractLamportClock(response2);

    assertTrue(clock2 > clock1, "Lamport clock should increment on each GET request");
  }

  /**
   * Test if the Lamport clock increments on local events (PUT and GET).
   * 
   * @throws IOException
   */
  @Test
  public void testLamportClockIncrementOnLocalEvents() throws IOException {
    String jsonData = createJsonData("IDS60901", "Station 1", 20.5);

    String response1 = sendRequest(createPutRequest(jsonData));
    int clock1 = extractLamportClock(response1);

    String response2 = sendRequest(createGetRequest());
    int clock2 = extractLamportClock(response2);

    assertTrue(clock2 > clock1, "Lamport clock should increment on each local event (PUT and GET)");
  }

  /**
   * Test if the server correctly synchronizes the Lamport clock when receiving a
   * higher clock value.
   * 
   * @throws IOException
   */
  @Test
  public void testLamportClockSynchronization() throws IOException {
    String jsonData1 = createJsonData("IDS60901", "Station 1", 20.5);
    String jsonData2 = createJsonData("IDS60902", "Station 2", 21.0);

    String response1 = sendRequest(createPutRequest(jsonData1));
    int clock1 = extractLamportClock(response1);

    // Simulate a client with a higher Lamport clock
    int higherClock = clock1 + 10;
    String putRequestWithHigherClock = createPutRequestWithClock(jsonData2, higherClock);
    String response2 = sendRequest(putRequestWithHigherClock);
    int clock2 = extractLamportClock(response2);

    assertTrue(clock2 > higherClock, "Server should update its Lamport clock when receiving a higher clock value");
    assertEquals(higherClock + 2, clock2,
        "Server should set its clock to max(local, received) + 1, then increment for response");
  }

  /**
   * Test if the Lamport clock values are unique and correct in a concurrent
   * environment.
   * 
   * @throws InterruptedException
   */
  @Test
  public void testLamportClockConcurrency() throws InterruptedException {
    int numThreads = 10;
    CountDownLatch latch = new CountDownLatch(numThreads);
    Set<Integer> clockValues = new HashSet<>();

    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    for (int i = 0; i < numThreads; i++) {
      final int index = i;
      executor.submit(() -> {
        try {
          String jsonData = createJsonData("IDS6090" + index, "Station " + index, 20.0 + index);
          String response = sendRequest(createPutRequest(jsonData));
          int clock = extractLamportClock(response);
          clockValues.add(clock);
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(numThreads, clockValues.size(), "Each request should have a unique Lamport clock value");
  }

  /**
   * Creates a PUT request with the given JSON data and Lamport clock value.
   *
   * @param jsonData The JSON data to include in the PUT request.
   * @param clock    The Lamport clock value to include in the PUT request.
   * @return The formatted PUT request string.
   */
  private String createPutRequestWithClock(String jsonData, int clock) {
    return HTTP_PUT + " " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
        CONTENT_TYPE_JSON + "\r\n" +
        "Lamport-Clock: " + clock + "\r\n" +
        CONTENT_LENGTH + jsonData.length() + "\r\n\r\n" +
        jsonData;
  }

  /**
   * Sends the given request to the server and returns the response.
   *
   * @param request The request to send to the server.
   * @return The response from the server.
   * @throws IOException If an I/O error occurs.
   */
  private String sendRequest(String request) throws IOException {
    try (Socket socket = new Socket(TEST_HOST, TEST_PORT)) {
      socket.setSoTimeout(5000); // 5 second timeout
      try (OutputStream os = socket.getOutputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
        os.write(request.getBytes());
        os.flush();

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
          response.append(line).append("\r\n");
        }

        if (response.toString().contains(CONTENT_LENGTH)) {
          int contentLength = Integer.parseInt(response.toString().split(CONTENT_LENGTH)[1].split("\r\n")[0]);
          char[] body = new char[contentLength];
          int charsRead = reader.read(body, 0, contentLength);
          if (charsRead != contentLength) {
            throw new IOException("Failed to read entire response body");
          }
          response.append(body);
        }

        return response.toString();
      }
    } catch (Exception e) {
      throw new IOException("Error sending request or receiving response: " + e.getMessage(), e);
    }
  }

  /**
   * Creates a GET request.
   *
   * @return The formatted GET request string.
   */
  private String createGetRequest() {
    return HTTP_GET + " " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
        "Lamport-Clock: " + 0 + "\r\n\r\n";
  }

  /**
   * Creates a PUT request with the given JSON data.
   *
   * @param jsonData The JSON data to include in the PUT request.
   * @return The formatted PUT request string.
   */
  private String createPutRequest(String jsonData) {
    return HTTP_PUT + " " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
        CONTENT_TYPE_JSON + "\r\n" +
        CONTENT_LENGTH + jsonData.length() + "\r\n\r\n" +
        jsonData;
  }

  /**
   * Creates JSON data for a weather station.
   *
   * @param id          The unique identifier for the weather station.
   * @param name        The name of the weather station.
   * @param temperature The air temperature at the weather station.
   * @return The formatted JSON data string.
   */
  private String createJsonData(String id, String name, double temperature) {
    return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"air_temp\":%.1f}", id, name, temperature);
  }

  /**
   * Extracts the Lamport clock value from the server response.
   *
   * @param response The response from the server.
   * @return The Lamport clock value, or -1 if not found.
   */
  private int extractLamportClock(String response) {
    String[] lines = response.split("\r\n");
    for (String line : lines) {
      if (line.startsWith("Lamport-Clock:")) {
        return Integer.parseInt(line.split(":")[1].trim());
      }
    }
    return -1; // Return -1 if the Lamport-Clock header is not found
  }
}