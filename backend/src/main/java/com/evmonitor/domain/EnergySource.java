package com.evmonitor.domain;

/**
 * Provenance of the kWh value stored in {@code ev_log.kwh_charged} / {@code kwh_at_vehicle}.
 *
 * <p>This marker captures HOW the energy figure was obtained, independent of {@link
 * EnergyMeasurementType} (which captures WHERE in the charging chain it sits). Both
 * dimensions matter:
 *
 * <ul>
 *   <li>{@link #OEM_MEASURED}  - real reading from the vehicle's OEM API
 *       (e.g. Tesla {@code charge_energy_added}, Smartcar brands that expose
 *       {@code energy_added}). Trustworthy for SoH detection.</li>
 *   <li>{@link #SOC_INFERRED}  - the connector had no OEM energy reading and computed
 *       kWh as {@code SoC-delta x effective_capacity / 100}. Feeding this back into
 *       SoH auto-detection creates a self-referential loop (SoH drifts by the brutto/netto
 *       ratio per run). Must be EXCLUDED from {@link
 *       com.evmonitor.application.BatterySohAutoDetector}.</li>
 *   <li>{@link #USER_INPUT}    - user typed the kWh value manually in the UI.</li>
 *   <li>{@link #WALLBOX}       - meter reading from a wallbox / OCPP source.</li>
 * </ul>
 *
 * <p>{@code NULL} on legacy rows means "unknown provenance, treat as trusted" - same
 * behaviour as before this marker existed (backwards-compatible).
 */
public enum EnergySource {
    OEM_MEASURED,
    SOC_INFERRED,
    USER_INPUT,
    WALLBOX
}
