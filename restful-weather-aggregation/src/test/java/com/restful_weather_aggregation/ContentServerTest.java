package com.restful_weather_aggregation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ContentServerTest {

  private static final String TEST_SERVER_URL = "http://localhost:4567";
  private static final String TEST_FILE_PATH = "content_server_data.txt";
  private ContentServer contentServer;

  /**
   * Set up the test environment before each test.
   * 
   * @throws IOException if an error occurs while setting up the test environment.
   */
  @BeforeEach
  public void setUp() throws IOException {
    String sampleData = "id:IDS60901\n" +
        "name:Adelaide (West Terrace /  ngayirdapira)\n" +
        "state:SA\n" +
        "time_zone:CST\n" +
        "lat:-34.9\n" +
        "lon:138.6\n" +
        "local_date_time:15/04:00pm\n" +
        "local_date_time_full:20230715160000\n" +
        "air_temp:13.3\n" +
        "apparent_t:9.5\n" +
        "cloud:Partly cloudy\n" +
        "dewpt:5.7\n" +
        "press:1023.9\n" +
        "rel_hum:60\n" +
        "wind_dir:S\n" +
        "wind_spd_kmh:15\n" +
        "wind_spd_kt:8";
    Files.write(Paths.get(TEST_FILE_PATH), sampleData.getBytes());
    contentServer = new ContentServer(TEST_SERVER_URL, TEST_FILE_PATH);
  }

  /**
   * Clean up the test environment after each test.
   * 
   * @throws IOException if an error occurs while cleaning up the test
   *                     environment.
   */
  @AfterEach
  public void tearDown() throws IOException {
    Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
  }

  /**
   * Test the readWeatherDataFromFile method.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testReadWeatherDataFromFile() throws Exception {
    Method readWeatherDataFromFile = ContentServer.class.getDeclaredMethod("readWeatherDataFromFile");
    readWeatherDataFromFile.setAccessible(true);
    String data = (String) readWeatherDataFromFile.invoke(contentServer);
    assertNotNull(data);
    assertTrue(data.contains("id:IDS60901"));
  }

  /**
   * Test the parseWeatherData method.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testParseWeatherData() throws Exception {
    Method parseWeatherData = ContentServer.class.getDeclaredMethod("parseWeatherData", String.class);
    parseWeatherData.setAccessible(true);
    String data = "id:IDS60901\nname:Adelaide\nair_temp:23.5";

    Object result = parseWeatherData.invoke(contentServer, data);

    assertTrue(result instanceof Map<?, ?>);
    Map<?, ?> weatherData = (Map<?, ?>) result;

    assertEquals("IDS60901", weatherData.get("id"));
    assertEquals("Adelaide", weatherData.get("name"));
    assertEquals("23.5", weatherData.get("air_temp"));
  }

  /**
   * Test the parseWeatherData method with missing id.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testParseWeatherDataMissingId() throws Exception {
    Method parseWeatherData = ContentServer.class.getDeclaredMethod("parseWeatherData", String.class);
    parseWeatherData.setAccessible(true);
    String data = "name:Adelaide\nair_temp:23.5";
    try {
      parseWeatherData.invoke(contentServer, data);
      fail("Expected IllegalArgumentException");
    } catch (InvocationTargetException e) {
      assertTrue(e.getCause() instanceof IllegalArgumentException);
      assertEquals("Missing required field: id", e.getCause().getMessage());
    }
  }

  /**
   * Test the convertToJSON method.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testConvertToJSON() throws Exception {
    Method convertToJSON = ContentServer.class.getDeclaredMethod("convertToJSON", Map.class);
    convertToJSON.setAccessible(true);
    Map<String, String> weatherData = new HashMap<>();
    weatherData.put("id", "IDS60901");
    weatherData.put("name", "Adelaide");
    weatherData.put("air_temp", "23.5");
    String json = (String) convertToJSON.invoke(contentServer, weatherData);
    assertTrue(json.contains("\"id\":\"IDS60901\""));
    assertTrue(json.contains("\"name\":\"Adelaide\""));
    assertTrue(json.contains("\"air_temp\":23.5"));
  }

  /**
   * Test the sendPutRequest method.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testSendPutRequest() throws Exception {
    String jsonData = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":\"23.5\"}";

    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    OutputStream mockOutputStream = mock(OutputStream.class);
    when(mockConnection.getResponseCode()).thenReturn(201);
    when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);

    contentServer.sendPutRequest(mockConnection, jsonData);

    verify(mockConnection).setRequestProperty("Content-Type", "application/json");
    verify(mockConnection).setRequestProperty("Content-Length", String.valueOf(jsonData.getBytes().length));
    verify(mockConnection).setRequestProperty("Lamport-Clock", String.valueOf(contentServer.getLamportClock()));
    verify(mockOutputStream).write(any(byte[].class), eq(0), anyInt());
    verify(mockConnection).getResponseCode();
  }

  /**
   * Test the incrementLamportClock method.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockImplementation() {
    int initialClock = contentServer.getLamportClock();
    contentServer.incrementLamportClock();
    assertEquals(initialClock + 1, contentServer.getLamportClock());
  }

  @Test
  public void testRetryMechanism() throws Exception {
    ContentServer spyContentServer = spy(contentServer);
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    OutputStream mockOutputStream = mock(OutputStream.class);

    when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockConnection.getResponseCode())
        .thenThrow(new IOException("Network error"))
        .thenReturn(201);

    doReturn(mockConnection).when(spyContentServer).createConnection();
    doNothing().when(spyContentServer).verifyUploadedData(any(HttpURLConnection.class), anyMap());

    spyContentServer.uploadData();

    verify(spyContentServer, times(2)).sendPutRequest(any(HttpURLConnection.class), anyString());
  }

  /**
   * Test the invalid data handling.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testInvalidDataHandling() throws Exception {
    Files.write(Paths.get(TEST_FILE_PATH), "InvalidData".getBytes());
    Method uploadData = ContentServer.class.getDeclaredMethod("uploadData");
    uploadData.setAccessible(true);
    try {
      uploadData.invoke(contentServer);
      fail("Expected IllegalArgumentException");
    } catch (InvocationTargetException e) {
      assertTrue(e.getCause() instanceof IllegalArgumentException);
      assertEquals("Invalid data format: InvalidData", e.getCause().getMessage());
    }
  }

  /**
   * Test the different HTTP response codes.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testDifferentHttpResponseCodes() throws Exception {
    ContentServer spyContentServer = spy(contentServer);
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    OutputStream mockOutputStream = mock(OutputStream.class);

    when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockConnection.getResponseCode()).thenReturn(200, 201, 400, 500);

    assertDoesNotThrow(() -> spyContentServer.sendPutRequest(mockConnection, "{}"));
    assertDoesNotThrow(() -> spyContentServer.sendPutRequest(mockConnection, "{}"));

    Exception exception = assertThrows(IOException.class, () -> spyContentServer.sendPutRequest(mockConnection, "{}"));
    assertEquals("PUT request failed. Response Code: 400", exception.getMessage());

    exception = assertThrows(IOException.class, () -> spyContentServer.sendPutRequest(mockConnection, "{}"));
    assertEquals("PUT request failed. Response Code: 500", exception.getMessage());

    verify(mockConnection, times(4)).getResponseCode();
  }

  /**
   * Test the createConnection method.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testCreateConnection() throws Exception {
    Method createConnection = ContentServer.class.getDeclaredMethod("createConnection");
    createConnection.setAccessible(true);
    HttpURLConnection connection = (HttpURLConnection) createConnection.invoke(contentServer);

    assertNotNull(connection);
    assertEquals("PUT", connection.getRequestMethod());
    assertEquals("application/json", connection.getRequestProperty("Content-Type"));
    assertEquals("ATOMClient/1/0", connection.getRequestProperty("User-Agent"));
    assertNotNull(connection.getRequestProperty("Lamport-Clock"));
    assertTrue(connection.getDoOutput());
  }

  @Test
  public void testLamportClockInitialization() {
    assertEquals(0, contentServer.getLamportClock(), "Lamport clock should be initialized to 0");
  }

  /**
   * Test the lamport clock increment on local event.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockIncrementOnLocalEvent() throws Exception {
    Method convertToJSON = ContentServer.class.getDeclaredMethod("convertToJSON", Map.class);
    convertToJSON.setAccessible(true);

    int initialClock = contentServer.getLamportClock();
    Map<String, String> weatherData = new HashMap<>();
    weatherData.put("id", "IDS60901");
    weatherData.put("name", "Adelaide");

    convertToJSON.invoke(contentServer, weatherData);

    assertEquals(initialClock + 1, contentServer.getLamportClock(), "Lamport clock should increment on local event");
  }

  /**
   * Test the lamport clock increment before sending message.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockIncrementBeforeSendingMessage() throws Exception {
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    OutputStream mockOutputStream = mock(OutputStream.class);
    when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockConnection.getResponseCode()).thenReturn(201);

    int initialClock = contentServer.getLamportClock();
    contentServer.sendPutRequest(mockConnection, "{}");

    assertTrue(contentServer.getLamportClock() > initialClock, "Lamport clock should increment before sending message");
  }

  /**
   * Test the lamport clock update on receiving message.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockUpdateOnReceivingMessage() throws Exception {
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    OutputStream mockOutputStream = mock(OutputStream.class);
    when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockConnection.getResponseCode()).thenReturn(201);
    when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("5");

    int initialClock = contentServer.getLamportClock();
    contentServer.sendPutRequest(mockConnection, "{}");

    assertTrue(contentServer.getLamportClock() > initialClock, "Lamport clock should update on receiving message");
    assertTrue(contentServer.getLamportClock() > 5, "Lamport clock should be greater than received clock");
  }

  /**
   * Test the lamport clock consistency.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockConsistency() throws Exception {
    ContentServer spyContentServer = spy(contentServer);
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    OutputStream mockOutputStream = mock(OutputStream.class);

    when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockConnection.getResponseCode()).thenReturn(201);
    when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("10");
    when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("20");
    when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("30");

    doReturn(mockConnection).when(spyContentServer).createConnection();
    doNothing().when(spyContentServer).verifyUploadedData(any(HttpURLConnection.class), anyMap());

    int initialClock = spyContentServer.getLamportClock();
    spyContentServer.uploadData();
    int finalClock = spyContentServer.getLamportClock();

    // Check that the Lamport clock has incremented
    assertTrue(finalClock > initialClock, "Lamport clock should increase during uploadData");

    // Check that the final clock is greater than the highest received clock
    assertTrue(finalClock > 30, "Final Lamport clock should be greater than all received clocks");

    // Verify that sendPutRequest was called, which internally updates the clock
    verify(spyContentServer, times(1)).sendPutRequest(any(HttpURLConnection.class), anyString());

    // Verify that the clock was incremented at least 5 times
    // (start, parse, convert, send, receive)
    assertTrue(finalClock >= initialClock + 5, "Lamport clock should increment at least 5 times");
  }

  /**
   * Test the lamport clock increment in the uploadData method.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockInUploadDataMethod() throws Exception {
    ContentServer spyContentServer = spy(contentServer);
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    OutputStream mockOutputStream = mock(OutputStream.class);

    when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockConnection.getResponseCode()).thenReturn(201);

    doReturn(mockConnection).when(spyContentServer).createConnection();
    doNothing().when(spyContentServer).verifyUploadedData(any(HttpURLConnection.class), anyMap());

    int initialClock = spyContentServer.getLamportClock();
    spyContentServer.uploadData();

    assertTrue(spyContentServer.getLamportClock() > initialClock,
        "Lamport clock should increment multiple times during uploadData");
  }
}