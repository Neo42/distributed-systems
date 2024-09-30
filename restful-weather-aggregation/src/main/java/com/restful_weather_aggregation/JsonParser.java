package com.restful_weather_aggregation;

import java.util.Map;
import java.util.HashMap;

/**
 * Utility class for parsing JSON strings to WeatherData objects and converting
 * WeatherData objects to JSON strings.
 */
public class JsonParser {

    /**
     * Parses a JSON string to create a WeatherData object.
     *
     * @param jsonString The JSON string representing weather data.
     * @return A WeatherData object created from the JSON string.
     * @throws JsonParseException If the JSON string cannot be parsed.
     */
    public static WeatherData fromJson(String jsonString) throws JsonParseException {
        try {
            Map<String, Object> jsonMap = parseJsonString(jsonString);
            String id = (String) jsonMap.get("id");
            String name = (String) jsonMap.get("name");

            if (id == null || id.isEmpty()) {
                throw new JsonParseException("Missing or empty 'id' field");
            }

            WeatherData wd = new WeatherData(id, name);

            if (jsonMap.containsKey("lamportClock")) {
                wd.setLamportClock(((Number) jsonMap.get("lamportClock")).intValue());
            }

            for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (!key.equals("id") && !key.equals("name") && !key.equals("lamportClock")) {
                    if (value instanceof String && ((String) value).isEmpty()) {
                        throw new JsonParseException("Empty value for key: " + key);
                    }
                    wd.addData(key, value);
                }
            }

            return wd;
        } catch (Exception e) {
            throw new JsonParseException("Failed to parse JSON: " + e.getMessage());
        }
    }

    /**
     * Converts a WeatherData object to a JSON string.
     *
     * @param wd The WeatherData object to be converted to JSON.
     * @return A JSON string representing the WeatherData object.
     */
    public static String toJson(WeatherData wd) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"id\":\"").append(wd.getId()).append("\",");
        json.append("\"name\":\"").append(wd.getName()).append("\",");
        json.append("\"lamportClock\":").append(wd.getLamportClock());

        for (Map.Entry<String, Object> entry : wd.getData().entrySet()) {
            json.append(",\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
        }

        json.append("}");
        return json.toString();
    }

    private static Map<String, Object> parseJsonString(String jsonString) throws JsonParseException {
        Map<String, Object> result = new HashMap<>();
        jsonString = jsonString.trim();
        if (!jsonString.startsWith("{") || !jsonString.endsWith("}")) {
            throw new JsonParseException("Invalid JSON format");
        }
        jsonString = jsonString.substring(1, jsonString.length() - 1);
        String[] pairs = jsonString.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length != 2) {
                throw new JsonParseException("Invalid key-value pair: " + pair);
            }
            String key = keyValue[0].trim().replaceAll("\"", "");
            String value = keyValue[1].trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                result.put(key, value.substring(1, value.length() - 1));
            } else if (value.equals("true") || value.equals("false")) {
                result.put(key, Boolean.parseBoolean(value));
            } else {
                try {
                    result.put(key, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    throw new JsonParseException("Invalid value format: " + value);
                }
            }
        }
        return result;
    }
}