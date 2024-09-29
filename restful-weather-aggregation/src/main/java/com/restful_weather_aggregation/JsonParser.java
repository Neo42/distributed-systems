package com.restful_weather_aggregation;

import java.util.Map;
import org.json.JSONObject;
import java.math.BigDecimal;

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
     *
     *                            Special cases:
     *                            - If the JSON string contains an empty value for
     *                            any key, a JsonParseException is thrown.
     *                            - If the JSON string contains a BigDecimal value,
     *                            it is converted to a double.
     */
    public static WeatherData fromJson(String jsonString) throws JsonParseException {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            String id = jsonObject.getString("id");
            String name = jsonObject.getString("name");
            WeatherData wd = new WeatherData(id, name);

            if (jsonObject.has("lamportClock")) {
                wd.setLamportClock(jsonObject.getInt("lamportClock"));
            }

            for (String key : jsonObject.keySet()) {
                if (!key.equals("id") && !key.equals("name") && !key.equals("lamportClock")) {
                    Object value = jsonObject.get(key);
                    if (value instanceof String && ((String) value).isEmpty()) {
                        throw new JsonParseException("Empty value for key: " + key);
                    }
                    if (value instanceof BigDecimal) {
                        value = ((BigDecimal) value).doubleValue();
                    }
                    wd.addData(key, value);
                }
            }

            return wd;
        } catch (Exception e) {
            throw new JsonParseException("Failed to parse JSON");
        }
    }

    /**
     * Converts a WeatherData object to a JSON string.
     *
     * @param wd The WeatherData object to be converted to JSON.
     * @return A JSON string representing the WeatherData object.
     */
    public static String toJson(WeatherData wd) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", wd.getId());
        jsonObject.put("name", wd.getName());
        jsonObject.put("lamportClock", wd.getLamportClock());

        for (Map.Entry<String, Object> entry : wd.getData().entrySet()) {
            jsonObject.put(entry.getKey(), entry.getValue());
        }

        return jsonObject.toString();
    }
}