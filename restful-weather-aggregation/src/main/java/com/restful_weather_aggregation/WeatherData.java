package com.restful_weather_aggregation;

import java.util.HashMap;
import java.util.Map;

public class WeatherData {
    private String id;
    private String name;
    private int lamportClock;
    private Map<String, Object> data;

    /**
     * Constructor for WeatherData.
     * Initializes the weather data with the specified id and name.
     *
     * @param id   The unique identifier for the weather station.
     * @param name The name of the weather station.
     */
    public WeatherData(String id, String name) {
        this.id = id;
        this.name = name;
        this.lamportClock = 0;
        this.data = new HashMap<>();
    }

    /**
     * Gets the unique identifier for the weather station.
     *
     * @return The unique identifier for the weather station.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the name of the weather station.
     *
     * @return The name of the weather station.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the current value of the Lamport clock.
     *
     * @return The current value of the Lamport clock.
     */
    public int getLamportClock() {
        return lamportClock;
    }

    /**
     * Sets the value of the Lamport clock.
     *
     * @param lamportClock The new value of the Lamport clock.
     */
    public void setLamportClock(int lamportClock) {
        this.lamportClock = lamportClock;
    }

    /**
     * Gets the weather data as a map.
     *
     * @return A map containing the weather data.
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Adds a key-value pair to the weather data.
     *
     * @param key   The key for the data.
     * @param value The value for the data.
     */
    public void addData(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Converts the weather data to a storage format string.
     *
     * @return A string representing the weather data in storage format.
     */
    public String toStorageFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("id:").append(id).append(",");
        sb.append("name:").append(name).append(",");
        sb.append("lamportClock:").append(lamportClock);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append(",").append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Creates a WeatherData object from a storage format string.
     *
     * @param storageString The string representing the weather data in storage
     *                      format.
     * @return A WeatherData object created from the storage format string.
     * @throws IllegalArgumentException If the storage format string is invalid.
     *
     *                                  Special cases:
     *                                  - If the storage string is missing the 'id'
     *                                  field, an IllegalArgumentException is
     *                                  thrown.
     *                                  - If a value cannot be parsed as a double,
     *                                  it is stored as a string.
     */
    public static WeatherData fromStorageFormat(String storageString) throws IllegalArgumentException {
        String[] parts = storageString.split(",");
        WeatherData wd = null;
        for (String part : parts) {
            String[] keyValue = part.split(":");
            if (keyValue.length != 2) {
                throw new IllegalArgumentException("Invalid storage format");
            }
            String key = keyValue[0];
            String value = keyValue[1];
            if (key.equals("id")) {
                wd = new WeatherData(value, null);
            } else if (key.equals("name")) {
                wd.name = value;
            } else if (key.equals("lamportClock")) {
                wd.lamportClock = Integer.parseInt(value);
            } else {
                try {
                    wd.addData(key, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    wd.addData(key, value);
                }
            }
        }
        if (wd == null) {
            throw new IllegalArgumentException("Missing 'id' in storage string");
        }
        return wd;
    }

    /**
     * Converts the weather data to a JSON string.
     *
     * @return A JSON string representing the weather data.
     */
    public String toJson() {
        return JsonParser.toJson(this);
    }

    /**
     * Creates a WeatherData object from a JSON string.
     *
     * @param input The JSON string representing the weather data.
     * @return A WeatherData object created from the JSON string.
     * @throws JsonParseException If the JSON string cannot be parsed.
     */
    public static WeatherData fromInputFormat(String input) throws JsonParseException {
        return JsonParser.fromJson(input);
    }
}