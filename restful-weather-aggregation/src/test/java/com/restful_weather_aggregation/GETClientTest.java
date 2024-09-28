package com.restful_weather_aggregation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

public class GETClientTest {

  private GETClient getClient;
  private HttpURLConnection mockConnection;
  private ByteArrayOutputStream outputStreamCaptor;
  private ByteArrayOutputStream logCaptor;

  @BeforeEach
  public void setUp() throws IOException {
    getClient = new GETClient(); // Initialize getClient here
    mockConnection = Mockito.mock(HttpURLConnection.class);
    outputStreamCaptor = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStreamCaptor));

    // Set up log capturing
    logCaptor = new ByteArrayOutputStream();
    Handler handler = new StreamHandler(logCaptor, new java.util.logging.SimpleFormatter());
    Logger.getLogger(GETClient.class.getName()).addHandler(handler);
    Logger.getLogger(GETClient.class.getName()).setLevel(Level.ALL);
  }

  @Test
  public void testSendGetRequestWithRetry() throws Exception {
    // Create mock URL
    URL mockUrl = new URL("http://example.com/weather.json");

    // Create and set up mock connections
    HttpURLConnection mockConnection1 = Mockito.mock(HttpURLConnection.class);
    HttpURLConnection mockConnection2 = Mockito.mock(HttpURLConnection.class);
    HttpURLConnection mockConnection3 = Mockito.mock(HttpURLConnection.class);

    // Set up getURL() for all mock connections
    when(mockConnection1.getURL()).thenReturn(mockUrl);
    when(mockConnection2.getURL()).thenReturn(mockUrl);
    when(mockConnection3.getURL()).thenReturn(mockUrl);

    // Set up getResponseCode() for all mock connections
    when(mockConnection1.getResponseCode()).thenThrow(new IOException("Connection failed"));
    when(mockConnection2.getResponseCode()).thenThrow(new IOException("Connection failed again"));
    when(mockConnection3.getResponseCode()).thenReturn(200);

    // Set up getInputStream() for the successful connection
    when(mockConnection3.getInputStream()).thenReturn(
        new java.io.ByteArrayInputStream("{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5}".getBytes()));

    // Mock the createConnection method to return our mock connections in sequence
    GETClient spyClient = Mockito.spy(getClient);
    Mockito.doReturn(mockConnection2).doReturn(mockConnection3)
        .when(spyClient).createConnection(anyString());

    // Call sendGetRequestWithRetry
    spyClient.sendGetRequestWithRetry(mockConnection1);

    // Print captured logs
    System.err.println("Captured logs:");
    System.err.println(logCaptor.toString());

    // Print captured output
    System.err.println("Captured output:");
    System.err.println(outputStreamCaptor.toString());

    // Verify that createConnection was called 2 times (for the 2 retries)
    Mockito.verify(spyClient, times(2)).createConnection(anyString());

    // Verify that sendGetRequest was called 3 times
    Mockito.verify(spyClient, times(3)).sendGetRequest(any(HttpURLConnection.class));

    String expectedOutput = "id: IDS60901\nname: Adelaide\nair_temp: 23.5\n";
    assertTrue(outputStreamCaptor.toString().endsWith(expectedOutput));
  }

  @Test
  public void testValidGetRequest() throws Exception {
    String jsonResponse = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5}";
    when(mockConnection.getResponseCode()).thenReturn(200);
    when(mockConnection.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(jsonResponse.getBytes()));

    getClient.sendGetRequest(mockConnection);

    String expectedOutput = "id: IDS60901\n" +
        "name: Adelaide\n" +
        "air_temp: 23.5\n";
    assertEquals(expectedOutput, outputStreamCaptor.toString());
  }

  @Test
  public void testNoContentResponse() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(204);

    getClient.sendGetRequest(mockConnection);

    String expectedOutput = "No content available\n";
    assertEquals(expectedOutput, outputStreamCaptor.toString());
  }

  @Test
  public void testErrorResponse() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(500);

    getClient.sendGetRequest(mockConnection);

    String expectedOutput = "Error: Server returned status code 500\n";
    assertEquals(expectedOutput, outputStreamCaptor.toString());
  }

  @Test
  public void testInvalidServerUrl() {
    assertThrows(IOException.class, () -> {
      getClient.createConnection("invalid_url");
    });
  }

  @Test
  public void testParseCommandLineArgs() {
    String[] args = { "http://localhost:4567", "IDS60901" };
    GETClient.ServerConfig config = getClient.parseCommandLineArgs(args);

    assertEquals("http://localhost:4567", config.serverUrl);
    assertEquals("IDS60901", config.stationId);
  }

  @Test
  public void testParseCommandLineArgsWithoutStationId() {
    String[] args = { "http://localhost:4567" };
    GETClient.ServerConfig config = getClient.parseCommandLineArgs(args);

    assertEquals("http://localhost:4567", config.serverUrl);
    assertNull(config.stationId);
  }

  @Test
  public void testParseCommandLineArgsInvalidInput() {
    String[] args = {};
    assertThrows(IllegalArgumentException.class, () -> {
      getClient.parseCommandLineArgs(args);
    });
  }
}