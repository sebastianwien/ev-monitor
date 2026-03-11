package com.evmonitor.domain;

public enum DataSource {
    USER_LOGGED,
    SPRITMONITOR_IMPORT,
    TESLA_IMPORT,       // legacy Owner API
    TESLA_FLEET,        // Fleet API Supercharger history via ev-monitor-connectors
    TESLA_HOME,         // Fleet API home/third-party charging via real-time polling
    WALLBOX_OCPP,
    WALLBOX_GOE,        // go-eCharger Cloud via ev-monitor-connectors
    SEED_DATA;

    public boolean includeInStatistics() {
        return this == USER_LOGGED || this == SPRITMONITOR_IMPORT
                || this == WALLBOX_OCPP || this == WALLBOX_GOE
                || this == TESLA_FLEET || this == TESLA_HOME;
    }
}
