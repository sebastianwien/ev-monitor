package com.evmonitor.infrastructure.external.audit;

public class SiteCrawlerException extends RuntimeException {

    public SiteCrawlerException(String message) {
        super(message);
    }

    public SiteCrawlerException(String message, Throwable cause) {
        super(message, cause);
    }
}
