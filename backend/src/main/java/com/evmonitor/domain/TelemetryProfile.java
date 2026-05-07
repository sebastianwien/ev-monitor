package com.evmonitor.domain;

/**
 * Tesla fleet-telemetry streaming profiles. Mirrors the connectors-service
 * profile concept on the core-api side so /telemetry-access can return the
 * profile a user is entitled to without an extra round-trip.
 *
 * <ul>
 *   <li>{@link #CHARGING_ONLY} - DetailedChargeState + Soc + EnergyRemaining + BmsFullchargecomplete</li>
 *   <li>{@link #FULL} - additionally Gear + Odometer-Heartbeat + Climate (enables trip detection)</li>
 * </ul>
 */
public enum TelemetryProfile {
    CHARGING_ONLY,
    FULL
}
