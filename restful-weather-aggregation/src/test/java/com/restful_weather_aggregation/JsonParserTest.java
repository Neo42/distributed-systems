package com.restful_weather_aggregation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JsonParserTest {

    /**
     * Test the fromJson method with valid input.
     * 
     * @throws JsonParseException if the input is invalid.
     */
    @Test
    public void testFromJson_ValidInput() throws JsonParseException {
        String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
        WeatherData wd = JsonParser.fromJson(input);
        assertEquals("IDS60901", wd.getId());
        assertEquals("Adelaide", wd.getName());
        assertEquals(0, wd.getLamportClock());
        assertEquals(23.5, wd.getData().get("air_temp"));
        assertEquals("10km/h", wd.getData().get("wind_speed"));
    }

    /**
     * Test the fromJson method with invalid input.
     * 
     * @throws JsonParseException if the input is invalid.
     */
    @Test
    public void testFromJson_InvalidInput() {
        String input = "IDS60901,Adelaide,air_temp:23.5,wind_speed:10km/h";
        assertThrows(JsonParseException.class, () -> JsonParser.fromJson(input));
    }

    /**
     * Test the fromJson method with an empty value.
     * 
     * @throws JsonParseException if the input is invalid.
     */
    @Test
    public void testFromJson_EmptyValue() {
        String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":\"\"}";
        assertThrows(JsonParseException.class, () -> JsonParser.fromJson(input));
    }

    /**
     * Test the fromJson method with a missing id.
     * 
     * @throws JsonParseException if the input is invalid.
     */
    @Test
    public void testFromJson_MissingId() {
        String input = "{\"name\":\"Adelaide\",\"air_temp\":23.5}";
        assertThrows(JsonParseException.class, () -> JsonParser.fromJson(input));
    }

    /**
     * Test the toJson method.
     */
    @Test
    public void testToJson() {
        WeatherData wd = new WeatherData("IDS60901", "Adelaide");
        wd.setLamportClock(3);
        wd.addData("air_temp", 23.5);
        wd.addData("wind_speed", "10km/h");
        String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":3,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
        assertEquals(expected, JsonParser.toJson(wd));
    }

    /**
     * Test the toJson method with empty data.
     */
    @Test
    public void testToJson_EmptyData() {
        WeatherData wd = new WeatherData("IDS60901", "Adelaide");
        String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":0}";
        assertEquals(expected, JsonParser.toJson(wd));
    }

    /**
     * Test the fromJson method with fully formatted data.
     * 
     * @throws JsonParseException if the input is invalid.
     */
    @Test
    public void testFromJson_FullyFormattedData() throws JsonParseException {
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

        WeatherData wd = JsonParser.fromJson(input);

        assertEquals("IDS60901", wd.getId());
        assertEquals("Adelaide (West Terrace /  ngayirdapira)", wd.getName());

        String jsonString = JsonParser.toJson(wd);

        assertTrue(jsonString.contains("\"id\":\"IDS60901\""));
        assertTrue(jsonString.contains("\"name\":\"Adelaide (West Terrace /  ngayirdapira)\""));
        assertTrue(jsonString.contains("\"lamportClock\":0"));
        assertTrue(jsonString.contains("\"state\":\"SA\""));
        assertTrue(jsonString.contains("\"time_zone\":\"CST\""));
        assertTrue(jsonString.contains("\"lat\":-34.9"));
        assertTrue(jsonString.contains("\"lon\":138.6"));
        assertTrue(jsonString.contains("\"local_date_time\":\"15/04:00pm\""));
        assertTrue(jsonString.contains("\"local_date_time_full\":\"20230715160000\""));
        assertTrue(jsonString.contains("\"air_temp\":13.3"));
        assertTrue(jsonString.contains("\"apparent_t\":9.5"));
        assertTrue(jsonString.contains("\"cloud\":\"Partly cloudy\""));
        assertTrue(jsonString.contains("\"dewpt\":5.7"));
        assertTrue(jsonString.contains("\"press\":1023.9"));
        assertTrue(jsonString.contains("\"rel_hum\":60"));
        assertTrue(jsonString.contains("\"wind_dir\":\"S\""));
        assertTrue(jsonString.contains("\"wind_spd_kmh\":15"));
        assertTrue(jsonString.contains("\"wind_spd_kt\":8"));
    }
}