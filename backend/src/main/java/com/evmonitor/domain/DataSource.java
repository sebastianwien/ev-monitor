package com.evmonitor.domain;

public enum DataSource {
    USER_LOGGED,
    SPRITMONITOR_IMPORT,
    TESLA_IMPORT,        // legacy Owner API (historical data, no longer active; excluded from stats: incomplete cost/duration data)
    TESLA_FLEET_IMPORT,  // Fleet API Supercharger history via ev-monitor-connectors
    TESLA_LIVE,          // Fleet API real-time polling (home/third-party charging)
    TESLA_MANUAL_IMPORT, // Manual import from TeslaMate / TeslaLogger / TeslaFi
    WALLBOX_OCPP,
    WALLBOX_GOE,         // go-eCharger Cloud via ev-monitor-connectors
    API_UPLOAD,          // Public Upload API (Wallboxen, Skripte, Home-Automation)
    TRONITY_IMPORT,      // Tronity XLSX export import
    SEED_DATA;

    public boolean includeInStatistics() {
        return this == USER_LOGGED || this == SPRITMONITOR_IMPORT
                || this == WALLBOX_OCPP || this == WALLBOX_GOE
                || this == TESLA_FLEET_IMPORT || this == TESLA_LIVE
                || this == TESLA_MANUAL_IMPORT || this == API_UPLOAD
                || this == TRONITY_IMPORT;
    }
}
