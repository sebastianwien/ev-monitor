package com.evmonitor.infrastructure.external.gsc;

public class GscClientException extends RuntimeException {

    public GscClientException(String message) {
        super(message);
    }

    public GscClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
