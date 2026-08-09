package com.evmonitor.domain;

/**
 * Where a SoH value came from. Drives the confidence badge in the UI - a BMS reading
 * and a five-charge estimate are both "92%", but they are not equally trustworthy.
 */
public enum BatterySohSource {

    /** Entered by the user. */
    MANUAL,

    /** Hub-weighted median over qualifying charging logs (see BatterySohAutoDetector). */
    CHARGE_LOG,

    /** Reported by the vehicle itself (currently Tesla EnergyRemaining). */
    VEHICLE_BMS,

    /** Recorded before V147, when provenance was not stored. Not reconstructible. */
    UNKNOWN
}
