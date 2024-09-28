package com.restful_weather_aggregation;

import java.util.HashMap;
import java.util.Map;

public class WeatherData {
  private String id;
  private String name;
  private int lamportClock;
  private Map<String, Object> data;

  public WeatherData(String id, String name) {
    this.id = id;
    this.name = name;
    this.lamportClock = 0;
    this.data = new HashMap<>();
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getLamportClock() {
    return lamportClock;
  }

  public void setLamportClock(int lamportClock) {
    this.lamportClock = lamportClock;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void addData(String key, Object value) {
    data.put(key, value);
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

  public String toJson() {
    return JsonParser.toJson(this);
  }

  public static WeatherData fromInputFormat(String input) throws JsonParseException {
    return JsonParser.fromJson(input);
  }
}