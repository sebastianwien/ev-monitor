package com.evmonitor.domain;

/**
 * The Live-Sync integration a telemetry-access check is being made for. Determines
 * the activation entitlement: Tesla Fleet-Telemetry is free for all Tesla drivers,
 * while Smartcar (VW etc.) stays on the paid AutoSync model.
 *
 * @see User#canActivateTelemetry(TelemetrySource)
 */
public enum TelemetrySource {
    TESLA,
    SMARTCAR
}
