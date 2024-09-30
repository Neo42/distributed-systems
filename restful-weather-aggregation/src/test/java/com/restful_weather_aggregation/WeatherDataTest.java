package com.restful_weather_aggregation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WeatherDataTest {

    /**
     * Test the constructor and getters.
     */
    @Test
    public void testConstructorAndGetters() {
        WeatherData wd = new WeatherData("IDS60901", "Adelaide");
        assertEquals("IDS60901", wd.getId());
        assertEquals("Adelaide", wd.getName());
        assertEquals(0, wd.getLamportClock());
    }

    /**
     * Test the setLamportClock method.
     */
    @Test
    public void testSetLamportClock() {
        WeatherData wd = new WeatherData("IDS60901", "Adelaide");
        wd.setLamportClock(5);
        assertEquals(5, wd.getLamportClock());
    }

    /**
     * Test the addData method.
     */
    @Test
    public void testAddData() {
        WeatherData wd = new WeatherData("IDS60901", "Adelaide");
        wd.addData("air_temp", 23.5);
        wd.addData("wind_speed", "10km/h");
        String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":0,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
        assertEquals(expected, wd.toJson());
    }

    /**
     * Test the fromInputFormat method with valid input.
     * 
     * @throws JsonParseException if the input format is invalid.
     */
    @Test
    public void testFromInputFormat_ValidInput() throws JsonParseException {
        String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
        WeatherData wd = WeatherData.fromInputFormat(input);
        assertEquals("IDS60901", wd.getId());
        assertEquals("Adelaide", wd.getName());
        String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":0,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
        assertEquals(expected, wd.toJson());
    }

    /**
     * Test the fromInputFormat method with invalid input.
     */
    @Test
    public void testFromInputFormat_InvalidInput() {
        String input = "IDS60901,Adelaide,air_temp:23.5,wind_speed:10km/h";
        assertThrows(JsonParseException.class, () -> WeatherData.fromInputFormat(input));
    }

    /**
     * Test the fromStorageFormat method.
     */
    @Test
    public void testFromStorageFormat() {
        String storageString = "id:IDS60901,name:Adelaide,lamportClock:5,air_temp:23.5,wind_speed:10km/h";
        WeatherData wd = WeatherData.fromStorageFormat(storageString);
        assertEquals("IDS60901", wd.getId());
        assertEquals("Adelaide", wd.getName());
        assertEquals(5, wd.getLamportClock());
        String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":5,\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
        assertEquals(expected, wd.toJson());
    }

    /**
     * Test the toStorageFormat method.
     */
    @Test
    public void testToStorageFormat() {
        WeatherData wd = new WeatherData("IDS60901", "Adelaide");
        wd.setLamportClock(5);
        wd.addData("air_temp", 23.5);
        wd.addData("wind_speed", "10km/h");
        String expected = "id:IDS60901,name:Adelaide,lamportClock:5,air_temp:23.5,wind_speed:10km/h";
        assertEquals(expected, wd.toStorageFormat());
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
        assertEquals(expected, wd.toJson());
    }

    /**
     * Test the toJson method with empty data.
     */
    @Test
    public void testToJson_EmptyData() {
        WeatherData wd = new WeatherData("IDS60901", "Adelaide");
        String expected = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"lamportClock\":0}";
        assertEquals(expected, wd.toJson());
    }

    /**
     * Test the fromInputFormat method with missing id.
     */
    @Test
    public void testFromInputFormat_MissingId() {
        String input = "{\"name\":\"Adelaide\",\"air_temp\":23.5}";
        assertThrows(JsonParseException.class, () -> WeatherData.fromInputFormat(input));
    }

    /**
     * Test the fromInputFormat method with empty value.
     */
    @Test
    public void testFromInputFormat_EmptyValue() {
        String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":\"\"}";
        assertThrows(JsonParseException.class, () -> WeatherData.fromInputFormat(input));
    }

    /**
     * Test the fromStorageFormat method with invalid format.
     */
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

        String jsonString = wd.toJson();

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