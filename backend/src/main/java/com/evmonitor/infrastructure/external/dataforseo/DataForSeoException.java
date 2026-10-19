package com.evmonitor.infrastructure.external.dataforseo;

public class DataForSeoException extends RuntimeException {

    public DataForSeoException(String message) {
        super(message);
    }

    public DataForSeoException(String message, Throwable cause) {
        super(message, cause);
    }
}
