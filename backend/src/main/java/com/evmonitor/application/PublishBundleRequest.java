package com.evmonitor.application;

/**
 * Request der CI, um ein hochgeladenes Web-Bundle zu registrieren.
 * Der Dateiname wird serverseitig aus der Version abgeleitet ({version}.zip).
 */
public record PublishBundleRequest(String version, String checksum) {
}
