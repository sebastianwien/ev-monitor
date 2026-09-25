package com.evmonitor.application.ingest;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EnergySource;
import lombok.extern.slf4j.Slf4j;

/**
 * Enum-Felder interner Absender nachsichtig lesen: ein unbekannter Wert (z. B. neuer Enum-Wert in
 * Connectors vor dem Core-Deploy) kostet nur die Angabe, nicht die Ladung.
 */
@Slf4j
public final class LenientEnums {

    private LenientEnums() {}

    /** Fehlend oder unbekannt: {@link ChargingType#UNKNOWN}. */
    public static ChargingType chargingType(String value) {
        if (value == null) return ChargingType.UNKNOWN;
        try {
            return ChargingType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ChargingType.UNKNOWN;
        }
    }

    /**
     * Fehlend oder unbekannt: null ("Herkunft unbekannt, gilt als vertrauenswürdig"). Der
     * CHECK-Constraint auf {@code ev_log.energy_source} würde einen fremden Wert sonst ablehnen.
     */
    public static EnergySource energySource(String value) {
        if (value == null) return null;
        try {
            return EnergySource.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown energySource '{}' from internal client - storing as NULL", value);
            return null;
        }
    }
}
