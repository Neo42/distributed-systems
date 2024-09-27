package com.restful_weather_aggregation;

import java.util.HashMap;
import java.util.Map;

public class WeatherData {
  private String id;
  private String name;

  private Map<String, Object> data;
  private int lamportClock;

  public WeatherData(String id, String name) {
    this.id = id;
    this.name = name;
    this.data = new HashMap<>();
    this.lamportClock = 0;
  }

  public String getId() {
    return id;
  }

  public String getName(){
    return name;
  }

  public int getLamportClock() {
    return lamportClock;
  }

  public void setLamportClock(int lamportClock) {
    this.lamportClock = lamportClock;
  }

  public void addData(String key, Object value) {
    data.put(key, value);
  }

  public static WeatherData fromInputFormat(String input) throws IllegalArgumentException, JsonParseException {
    input = input.trim();
    if (!input.startsWith("{") || !input.endsWith("}")) {
      throw new JsonParseException("Invalid JSON format: must start with '{' and end with '}'");
    }

    String[] parts = input.substring(1, input.length() - 1).split(",");
    WeatherData wd = null;

    for (String part : parts) {
      String[] keyValue = part.split(":", 2);
      if (keyValue.length != 2) {
        throw new JsonParseException("Invalid key-value pair: " + part);
      }

      String key = keyValue[0].trim().replace("\"", "");
      String value = keyValue[1].trim().replace("\"", "");

      if (value.isEmpty()) {
        throw new JsonParseException("Empty value for key: " + key);
      }

      switch (key) {
        case "id":
          wd = new WeatherData(value, "");
          break;
        case "name":
          if (wd == null)
            throw new JsonParseException("'id' must come before 'name'");
          wd.name = value;
          break;
        case "local_date_time_full":
          wd.addData(key, value); // Store as string without parsing
          break;
        default:
          if (wd == null)
            throw new JsonParseException("'id' and 'name' must come before other data");
          try {
            double numericValue = Double.parseDouble(value);
            wd.addData(key, numericValue);
          } catch (NumberFormatException e) {
            wd.addData(key, value);
          }
      }
    }

    if (wd == null) {
      throw new JsonParseException("Missing 'id' in JSON data");
    }

    return wd;
  }

  public static WeatherData fromStorageFormat(String storageString)
      throws IllegalArgumentException, JsonParseException {
    String[] parts = storageString.split(",");
    WeatherData wd = null;

    for (String part : parts) {
      String[] keyValue = part.split(":", 2);
      if (keyValue.length != 2) {
        throw new IllegalArgumentException("Invalid key-value pair: " + part);
      }

      String key = keyValue[0].trim();
      String value = keyValue[1].trim();

      if (wd == null) {
        if (!key.equals("id")) {
          throw new IllegalArgumentException("First key must be 'id'");
        }
        wd = new WeatherData(value, "");
      } else {
        switch (key) {
          case "name":
            wd.name = value;
            break;
          case "lamportClock":
            wd.setLamportClock(Integer.parseInt(value));
            break;
          default:
            if (key.equals("local_date_time_full")) {
              wd.addData(key, value);
            } else {
              try {
                double numericValue = Double.parseDouble(value);
                wd.addData(key, numericValue);
              } catch (NumberFormatException e) {
                wd.addData(key, value);
              }
            }
        }
      }
    }

    if (wd == null) {
      throw new IllegalArgumentException("Missing 'id' in storage string");
    }

    return wd;
  }

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

  public String toJson() {
    StringBuilder json = new StringBuilder("{");
    json.append("\"id\":\"").append(id).append("\",");
    json.append("\"name\":\"").append(name).append("\",");
    json.append("\"lamportClock\":").append(lamportClock).append(",");
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      json.append("\"").append(entry.getKey()).append("\":");
      if (entry.getValue() instanceof String || entry.getKey().equals("local_date_time_full")) {
        json.append("\"").append(entry.getValue()).append("\",");
      } else {
        json.append(entry.getValue()).append(",");
      }
    }
    if (json.charAt(json.length() - 1) == ',') {
      json.setLength(json.length() - 1); // Remove last comma
    }
    json.append("}");
    return json.toString();
  }
}
