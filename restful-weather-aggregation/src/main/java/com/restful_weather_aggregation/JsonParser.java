package com.restful_weather_aggregation;

import java.util.Map;
import org.json.JSONObject;
import java.math.BigDecimal;

public class JsonParser {

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