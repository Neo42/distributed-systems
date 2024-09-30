package com.restful_weather_aggregation;

/**
 * Custom exception class for JSON parsing errors.
 * This exception is thrown when there is an error parsing JSON data.
 */
public class JsonParseException extends Exception {

    /**
     * Constructor for JsonParseException.
     * Initializes the exception with a specific error message.
     *
     * @param message The error message describing the JSON parsing error.
     */
    public JsonParseException(String message) {
        super(message);
    }
}