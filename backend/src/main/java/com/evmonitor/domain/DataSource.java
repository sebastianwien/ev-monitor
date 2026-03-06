package com.evmonitor.domain;

public enum DataSource {
    USER_LOGGED,
    SPRITMONITOR_IMPORT,
    TESLA_IMPORT,       // legacy Owner API
    TESLA_FLEET,        // Fleet API via ev-monitor-connectors
    WALLBOX_OCPP,
    WALLBOX_GOE,        // go-eCharger Cloud via ev-monitor-connectors
    SEED_DATA;

    public boolean includeInStatistics() {
        return this == USER_LOGGED || this == SPRITMONITOR_IMPORT
                || this == WALLBOX_OCPP || this == WALLBOX_GOE || this == TESLA_FLEET;
    }
}
