package com.restful_weather_aggregation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;

public class WeatherDataTest {

  @Test
  public void testConstructorAndGetters() {
    WeatherData wd = new WeatherData("IDS60901", "Adelaide");
    assertEquals("IDS60901", wd.getId());
    assertEquals("Adelaide", wd.getName());
    assertEquals(0, wd.getLamportClock());
  }

  @Test
  public void testSetLamportClock() {
    WeatherData wd = new WeatherData("IDS60901", "Adelaide");
    wd.setLamportClock(5);
    assertEquals(5, wd.getLamportClock());
  }

  @Test
  public void testAddData() {
    WeatherData wd = new WeatherData("IDS60901", "Adelaide");
    wd.addData("air_temp", 23.5);
    wd.addData("wind_speed", "10km/h");
    String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":0,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
    assertEquals(expected, wd.toJson());
  }

  @Test
  public void testFromInputFormat_ValidInput() throws JsonParseException {
    String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
    WeatherData wd = WeatherData.fromInputFormat(input);
    assertEquals("IDS60901", wd.getId());
    assertEquals("Adelaide", wd.getName());
    String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":0,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
    assertEquals(expected, wd.toJson());
  }

  @Test
  public void testFromInputFormat_InvalidInput() {
    String input = "IDS60901,Adelaide,air_temp:23.5,wind_speed:10km/h";
    assertThrows(JsonParseException.class, () -> WeatherData.fromInputFormat(input));
  }

  @Test
  public void testFromStorageFormat() throws JsonParseException {
    String storageString = "id:IDS60901,name:Adelaide,lamportClock:5,air_temp:23.5,wind_speed:10km/h";
    WeatherData wd = WeatherData.fromStorageFormat(storageString);
    assertEquals("IDS60901", wd.getId());
    assertEquals("Adelaide", wd.getName());
    assertEquals(5, wd.getLamportClock());
    String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":5,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
    assertEquals(expected, wd.toJson());
  }

  @Test
  public void testToStorageFormat() {
    WeatherData wd = new WeatherData("IDS60901", "Adelaide");
    wd.setLamportClock(5);
    wd.addData("air_temp", 23.5);
    wd.addData("wind_speed", "10km/h");
    String expected = "id:IDS60901,name:Adelaide,lamportClock:5,air_temp:23.5,wind_speed:10km/h";
    assertEquals(expected, wd.toStorageFormat());
  }

  @Test
  public void testToJson() {
    WeatherData wd = new WeatherData("IDS60901", "Adelaide");
    wd.setLamportClock(3);
    wd.addData("air_temp", 23.5);
    wd.addData("wind_speed", "10km/h");
    String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":3,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
    assertEquals(expected, wd.toJson());
  }

  @Test
  public void testToJson_EmptyData() {
    WeatherData wd = new WeatherData("IDS60901", "Adelaide");
    String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":0}";
    assertEquals(expected, wd.toJson());
  }

  @Test
  public void testFromInputFormat_MissingId() {
    String input = "{\"name\":\"Adelaide\",\"air_temp\":23.5}";
    assertThrows(JsonParseException.class, () -> WeatherData.fromInputFormat(input));
  }

  @Test
  public void testFromInputFormat_EmptyValue() {
    String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":\"\"}";
    assertThrows(JsonParseException.class, () -> WeatherData.fromInputFormat(input));
  }

  @Test
  public void testFromStorageFormat_InvalidFormat() {
    String storageString = "id:IDS60901,name:Adelaide,lamportClock:invalid";
    assertThrows(IllegalArgumentException.class, () -> WeatherData.fromStorageFormat(storageString));
  }

  @Test
  public void testFromInputFormat_FullyFormattedData() throws Exception {
    String input = "{" +
        "\"id\":\"IDS60901\"," +
        "\"name\":\"Adelaide (West Terrace /  ngayirdapira)\"," +
        "\"state\":\"SA\"," +
        "\"time_zone\":\"CST\"," +
        "\"lat\":-34.9," +
        "\"lon\":138.6," +
        "\"local_date_time\":\"15/04:00pm\"," +
        "\"local_date_time_full\":\"20230715160000\"," +
        "\"air_temp\":13.3," +
        "\"apparent_t\":9.5," +
        "\"cloud\":\"Partly cloudy\"," +
        "\"dewpt\":5.7," +
        "\"press\":1023.9," +
        "\"rel_hum\":60," +
        "\"wind_dir\":\"S\"," +
        "\"wind_spd_kmh\":15," +
        "\"wind_spd_kt\":8" +
        "}";

    WeatherData wd = WeatherData.fromInputFormat(input);

    assertEquals("IDS60901", wd.getId());
    assertEquals("Adelaide (West Terrace /  ngayirdapira)", wd.getName());

    JSONObject jsonObject = new JSONObject(wd.toJson());

    assertEquals("IDS60901", jsonObject.getString("id"));
    assertEquals("Adelaide (West Terrace /  ngayirdapira)", jsonObject.getString("name"));
    assertEquals(0, jsonObject.getInt("lamportClock"));
    assertEquals("SA", jsonObject.getString("state"));
    assertEquals("CST", jsonObject.getString("time_zone"));
    assertEquals(-34.9, jsonObject.getDouble("lat"), 0.001);
    assertEquals(138.6, jsonObject.getDouble("lon"), 0.001);
    assertEquals("15/04:00pm", jsonObject.getString("local_date_time"));
    assertEquals("20230715160000", jsonObject.getString("local_date_time_full"));
    assertEquals(13.3, jsonObject.getDouble("air_temp"), 0.001);
    assertEquals(9.5, jsonObject.getDouble("apparent_t"), 0.001);
    assertEquals("Partly cloudy", jsonObject.getString("cloud"));
    assertEquals(5.7, jsonObject.getDouble("dewpt"), 0.001);
    assertEquals(1023.9, jsonObject.getDouble("press"), 0.001);
    assertEquals(60, jsonObject.getInt("rel_hum"));
    assertEquals("S", jsonObject.getString("wind_dir"));
    assertEquals(15, jsonObject.getInt("wind_spd_kmh"));
    assertEquals(8, jsonObject.getInt("wind_spd_kt"));
  }
}