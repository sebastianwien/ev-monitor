package com.evmonitor.infrastructure.external.plausible;

public class PlausibleClientException extends RuntimeException {
    public PlausibleClientException(String message) {
        super(message);
    }

    public PlausibleClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
