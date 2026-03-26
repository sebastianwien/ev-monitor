package com.evmonitor.domain;

/**
 * Describes at which point in the charging chain energy is measured.
 *
 * AT_CHARGER  (Level 1) - Gross energy at the charger/wallbox (default).
 *                         Source: wallboxes, charge stations, OCPP.
 *                         ~7% higher than AT_VEHICLE due to cable and onboard charger losses.
 *
 * AT_VEHICLE  (Level 2) - Net energy entering the battery.
 *                         Source: vehicle API (Smartcar, Tesla Fleet polling).
 *                         ~7% lower than AT_CHARGER.
 *
 * DRIVING_ONLY (Level 3) - Drive consumption only, excludes standby, preconditioning, etc.
 *                          Source: Tessie, TeslaFi-style apps.
 *                          ~20-30% lower than AT_CHARGER.
 *
 * EV Monitor standardizes on AT_CHARGER for community stats.
 * This field is stored for future use — no filtering by type yet.
 */
public enum EnergyMeasurementType {
    AT_CHARGER,
    AT_VEHICLE,
    DRIVING_ONLY
}
