package com.danielflower.crank4j.utils;

public class Crank4jException extends RuntimeException {
    public Crank4jException(String message) {
        super(message);
    }

    public Crank4jException(String message, Throwable cause) {
        super(message, cause);
    }
}
