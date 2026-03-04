package com.evmonitor.domain;

public enum DataSource {
    USER_LOGGED,
    SPRITMONITOR_IMPORT,
    TESLA_IMPORT,
    SEED_DATA;

    public boolean includeInStatistics() {
        return this == USER_LOGGED || this == SPRITMONITOR_IMPORT;
    }
}
