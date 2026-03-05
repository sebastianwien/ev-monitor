package com.evmonitor.domain;

public enum DataSource {
    USER_LOGGED,
    SPRITMONITOR_IMPORT,
    TESLA_IMPORT,
    WALLBOX_OCPP,
    SEED_DATA;

    public boolean includeInStatistics() {
        return this == USER_LOGGED || this == SPRITMONITOR_IMPORT || this == WALLBOX_OCPP;
    }
}
