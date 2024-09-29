package com.restful_weather_aggregation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for GETClient.
 * This class contains unit tests for various functionalities of the GETClient.
 */
public class GETClientTest {

  private GETClient getClient;
  private HttpURLConnection mockConnection;
  private ByteArrayOutputStream outputStreamCaptor;
  private ByteArrayOutputStream logCaptor;

  /**
   * Set up the test environment before each test.
   * Initializes the GETClient, mock connection, and output stream captors.
   * 
   * @throws IOException if an error occurs while setting up the test environment.
   */
  @BeforeEach
  public void setUp() throws IOException {
    getClient = new GETClient();
    mockConnection = Mockito.mock(HttpURLConnection.class);
    outputStreamCaptor = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStreamCaptor));

    logCaptor = new ByteArrayOutputStream();
    Handler handler = new StreamHandler(logCaptor, new java.util.logging.SimpleFormatter());
    Logger.getLogger(GETClient.class.getName()).addHandler(handler);
    Logger.getLogger(GETClient.class.getName()).setLevel(Level.ALL);
  }

  /**
   * Test the initialization of the Lamport clock.
   * The initial Lamport clock value should be 0.
   */
  @Test
  public void testLamportClockInitialization() {
    assertEquals(0, getClient.getLamportClock(), "Initial Lamport clock should be 0");
  }

  /**
   * Test the increment of the Lamport clock on sending a GET request.
   * The Lamport clock should be incremented after sending the request.
   * 
   * @throws IOException if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockIncrementOnSend() throws IOException {
    URL mockUrl = new URL("http://example.com/weather.json");
    when(mockConnection.getURL()).thenReturn(mockUrl);
    when(mockConnection.getResponseCode()).thenReturn(200);
    when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream("{}".getBytes()));

    getClient.sendGetRequest(mockConnection);

    verify(mockConnection).setRequestProperty("Lamport-Clock", "1");
    assertTrue(getClient.getLamportClock() > 0, "Lamport clock should be incremented after sending");
  }

  /**
   * Test the update of the Lamport clock on receiving a response.
   * The Lamport clock should be updated based on the received clock value.
   * 
   * @throws IOException if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockUpdateOnReceive() throws IOException {
    URL mockUrl = new URL("http://example.com/weather.json");
    when(mockConnection.getURL()).thenReturn(mockUrl);
    when(mockConnection.getResponseCode()).thenReturn(200);
    when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream("{}".getBytes()));
    when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("5");

    getClient.sendGetRequest(mockConnection);

    assertTrue(getClient.getLamportClock() > 5, "Lamport clock should be updated based on received clock");
  }

  /**
   * Test the increment of the Lamport clock on retrying a GET request.
   * The Lamport clock should be incremented multiple times during retry.
   * 
   * @throws IOException if an error occurs while testing the method.
   */
  @Test
  public void testLamportClockIncrementOnRetry() throws IOException {
    URL mockUrl = new URL("http://example.com/weather.json");
    HttpURLConnection mockConnection1 = Mockito.mock(HttpURLConnection.class);
    HttpURLConnection mockConnection2 = Mockito.mock(HttpURLConnection.class);

    when(mockConnection1.getURL()).thenReturn(mockUrl);
    when(mockConnection2.getURL()).thenReturn(mockUrl);
    when(mockConnection1.getResponseCode()).thenThrow(new IOException("Connection failed"));
    when(mockConnection2.getResponseCode()).thenReturn(200);
    when(mockConnection2.getInputStream()).thenReturn(new ByteArrayInputStream("{}".getBytes()));

    GETClient spyClient = Mockito.spy(getClient);
    doReturn(mockConnection2).when(spyClient).createConnection(anyString());

    spyClient.sendGetRequestWithRetry(mockConnection1);

    assertTrue(spyClient.getLamportClock() > 2, "Lamport clock should be incremented multiple times during retry");
  }

  /**
   * Test the sendGetRequestWithRetry method.
   * Verifies that the method retries the GET request and eventually succeeds.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testSendGetRequestWithRetry() throws Exception {
    URL mockUrl = new URL("http://example.com/weather.json");

    HttpURLConnection mockConnection1 = Mockito.mock(HttpURLConnection.class);
    HttpURLConnection mockConnection2 = Mockito.mock(HttpURLConnection.class);
    HttpURLConnection mockConnection3 = Mockito.mock(HttpURLConnection.class);

    when(mockConnection1.getURL()).thenReturn(mockUrl);
    when(mockConnection2.getURL()).thenReturn(mockUrl);
    when(mockConnection3.getURL()).thenReturn(mockUrl);

    when(mockConnection1.getResponseCode()).thenThrow(new IOException("Connection failed"));
    when(mockConnection2.getResponseCode()).thenThrow(new IOException("Connection failed again"));
    when(mockConnection3.getResponseCode()).thenReturn(200);

    when(mockConnection3.getInputStream()).thenReturn(
        new ByteArrayInputStream("{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5}".getBytes()));

    GETClient spyClient = Mockito.spy(getClient);
    doReturn(mockConnection2).doReturn(mockConnection3).when(spyClient).createConnection(anyString());

    spyClient.sendGetRequestWithRetry(mockConnection1);

    System.err.println("Captured logs:");
    System.err.println(logCaptor.toString());

    System.err.println("Captured output:");
    System.err.println(outputStreamCaptor.toString());

    verify(spyClient, times(2)).createConnection(anyString());
    verify(spyClient, times(3)).sendGetRequest(any(HttpURLConnection.class));

    String expectedOutput = "id: IDS60901\nname: Adelaide\nair_temp: 23.5\n";
    assertTrue(outputStreamCaptor.toString().endsWith(expectedOutput));
  }

  /**
   * Test a valid GET request.
   * Verifies that the response is correctly parsed and printed.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testValidGetRequestWithoutStationId() throws Exception {
    String jsonResponse = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5}";
    when(mockConnection.getResponseCode()).thenReturn(200);
    when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(jsonResponse.getBytes()));

    getClient.sendGetRequest(mockConnection);

    String expectedOutput = "id: IDS60901\n" +
        "name: Adelaide\n" +
        "air_temp: 23.5\n";
    assertEquals(expectedOutput, outputStreamCaptor.toString());
  }

  /**
   * Test a GET request that returns no content.
   * Verifies that the appropriate message is printed.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testNoContentResponse() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(204);

    getClient.sendGetRequest(mockConnection);

    String expectedOutput = "No content available\n";
    assertEquals(expectedOutput, outputStreamCaptor.toString());
  }

  /**
   * Test a GET request that returns an error response.
   * Verifies that the appropriate error message is printed.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testErrorResponse() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(500);

    getClient.sendGetRequest(mockConnection);

    String expectedOutput = "Error: Server returned status code 500\n";
    assertEquals(expectedOutput, outputStreamCaptor.toString());
  }

  /**
   * Test creating a connection with an invalid URL.
   * Verifies that an IOException is thrown.
   */
  @Test
  public void testInvalidServerUrl() {
    assertThrows(IOException.class, () -> {
      getClient.createConnection("invalid_url");
    });
  }

  /**
   * Test parsing command line arguments with a station ID.
   * Verifies that the server URL and station ID are correctly parsed.
   */
  @Test
  public void testParseCommandLineArgs() {
    String[] args = { "http://localhost:4567", "IDS60901" };
    GETClient.ServerConfig config = getClient.parseCommandLineArgs(args);

    assertEquals("http://localhost:4567", config.serverUrl);
    assertEquals("IDS60901", config.stationId);
  }

  /**
   * Test parsing command line arguments without a station ID.
   * Verifies that the server URL is correctly parsed and the station ID is null.
   */
  @Test
  public void testParseCommandLineArgsWithoutStationId() {
    String[] args = { "http://localhost:4567" };
    GETClient.ServerConfig config = getClient.parseCommandLineArgs(args);

    assertEquals("http://localhost:4567", config.serverUrl);
    assertNull(config.stationId);
  }

  /**
   * Test parsing invalid command line arguments.
   * Verifies that an IllegalArgumentException is thrown.
   */
  @Test
  public void testParseCommandLineArgsInvalidInput() {
    String[] args = {};
    assertThrows(IllegalArgumentException.class, () -> {
      getClient.parseCommandLineArgs(args);
    });
  }

  /**
   * Test a GET request with a station ID.
   * Verifies that the response is correctly parsed and printed.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testGetRequestWithStationId() throws Exception {
    URL mockUrl = new URL("http://localhost:4567/weather.json?id=IDS60901");
    when(mockConnection.getURL()).thenReturn(mockUrl);
    when(mockConnection.getResponseCode()).thenReturn(200);
    when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(
        "{\"id\":\"IDS60901\",\"name\":\"Test Station\",\"air_temp\":25.0}".getBytes()));

    GETClient spyClient = spy(getClient);
    doReturn(mockConnection).when(spyClient).createConnection(anyString(), anyString());

    spyClient.sendGetRequest(mockConnection);

    String expectedOutput = "id: IDS60901\nname: Test Station\nair_temp: 25.0\n";
    assertEquals(expectedOutput.trim(), outputStreamCaptor.toString().trim());
  }

  /**
   * Test the output format of a valid GET request.
   * Verifies that the response is correctly formatted and printed.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testOutputFormat() throws Exception {
    String jsonResponse = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5}";
    when(mockConnection.getResponseCode()).thenReturn(200);
    when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(jsonResponse.getBytes()));

    getClient.sendGetRequest(mockConnection);

    String expectedOutput = "id: IDS60901\n" +
        "name: Adelaide\n" +
        "air_temp: 23.5\n";
    assertEquals(expectedOutput, outputStreamCaptor.toString());
  }

  /**
   * Test the failure tolerance of the GET client.
   * Verifies that the client retries the GET request and eventually succeeds.
   * 
   * @throws Exception if an error occurs while testing the method.
   */
  @Test
  public void testFailureTolerance() throws Exception {
    URL mockUrl = new URL("http://example.com/weather.json");

    HttpURLConnection mockConnection1 = Mockito.mock(HttpURLConnection.class);
    HttpURLConnection mockConnection2 = Mockito.mock(HttpURLConnection.class);
    HttpURLConnection mockConnection3 = Mockito.mock(HttpURLConnection.class);

    when(mockConnection1.getURL()).thenReturn(mockUrl);
    when(mockConnection2.getURL()).thenReturn(mockUrl);
    when(mockConnection3.getURL()).thenReturn(mockUrl);

    when(mockConnection1.getResponseCode()).thenThrow(new IOException("Connection failed"));
    when(mockConnection2.getResponseCode()).thenThrow(new IOException("Connection failed again"));
    when(mockConnection3.getResponseCode()).thenReturn(200);

    when(mockConnection3.getInputStream()).thenReturn(
        new ByteArrayInputStream("{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5}".getBytes()));

    GETClient spyClient = Mockito.spy(getClient);
    Mockito.doReturn(mockConnection2).doReturn(mockConnection3).when(spyClient).createConnection(anyString());

    spyClient.sendGetRequestWithRetry(mockConnection1);

    verify(spyClient, times(2)).createConnection(anyString());
    verify(spyClient, times(3)).sendGetRequest(any(HttpURLConnection.class));

    String expectedOutput = "id: IDS60901\nname: Adelaide\nair_temp: 23.5\n";
    assertTrue(outputStreamCaptor.toString().endsWith(expectedOutput));
  }
}