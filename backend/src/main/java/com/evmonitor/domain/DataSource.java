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
    SMARTCAR_LIVE,       // Smartcar webhook-based live session tracking
    VWGROUP_LIVE,        // VW Group (Skoda/VW/Audi/SEAT/CUPRA) MQTT-based live session tracking
    TESSIE,              // Tessie fleet import
    XPENG_IMPORT,        // XPeng Phase 1: EU-Data-Act XLSX, manual user upload
    XPENG_LIVE,          // XPeng Phase 2+: automated mail-poll / future XPeng API (AutoSync Live tier)
    EU_DATA_ACT_IMPORT;  // VW Group EU Data Act portal: manual JSON/ZIP upload (VW, Audi, Skoda, SEAT, CUPRA, Porsche)

    public boolean includeInStatistics() {
        return this == USER_LOGGED || this == SPRITMONITOR_IMPORT
                || this == WALLBOX_OCPP || this == WALLBOX_GOE
                || this == TESLA_FLEET_IMPORT || this == TESLA_LIVE
                || this == TESLA_MANUAL_IMPORT || this == API_UPLOAD
                || this == TRONITY_IMPORT || this == SMARTCAR_LIVE
                || this == VWGROUP_LIVE || this == TESSIE
                || this == XPENG_IMPORT || this == XPENG_LIVE
                || this == EU_DATA_ACT_IMPORT;
    }

    /**
     * True if logs from this source may lack SoC/odometer data due to automatic import,
     * and should therefore be skipped — with their kWh accumulated — when searching for
     * a valid logX predecessor in the consumption chain.
     *
     * A transparent log is never a real trip boundary: it represents intermediate charging
     * the user couldn't control (e.g. solar surplus). The chain continues past it.
     */
    public boolean isTransparentForConsumptionChain() {
        return this == WALLBOX_GOE;
    }

    /**
     * True if a log from this source without odometer is a user-entered partial charge whose
     * energy is fully known — it is then skipped in the logX search with its vehicle-side kWh
     * accumulated (rolling consumption window between two odometer+SoC anchors).
     *
     * Deliberately narrow: only SPRITMONITOR_IMPORT qualifies. For automatic sources a missing
     * odometer can mean missed sessions (e.g. TESLA_FLEET_IMPORT snapshot miss), so they must
     * keep breaking the chain. Logs without kWh break the chain too — their energy is unknown.
     */
    public boolean isTransparentWhenOdometerMissing() {
        return this == SPRITMONITOR_IMPORT;
    }

    /** Returns the measurement point for energy reported by this data source. */
    public EnergyMeasurementType measurementType() {
        return switch (this) {
            case TESLA_LIVE, SMARTCAR_LIVE, VWGROUP_LIVE, TESSIE, XPENG_IMPORT, XPENG_LIVE, EU_DATA_ACT_IMPORT -> EnergyMeasurementType.AT_VEHICLE;
            default -> EnergyMeasurementType.AT_CHARGER;
        };
    }
}
