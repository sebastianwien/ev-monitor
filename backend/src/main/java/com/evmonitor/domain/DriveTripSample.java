package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Was eine Fahrt zum Fahrverbrauch beiträgt, ohne Route und Rohdaten. */
public record DriveTripSample(
        UUID carId,
        UUID userId,
        String dataSource,
        OffsetDateTime startedAt,
        BigDecimal distanceKm,
        BigDecimal estimatedConsumedKwh,
        BigDecimal socStart,
        BigDecimal socEnd) {

    /**
     * kWh aus dem Akku: Wert des Fahrzeugs, sonst SoC-Abfall mal effektive Kapazität, mit Vorzeichen.
     * Steigt der SoC (Gefälle, Rundung), wird der Wert negativ; Summen über viele Fahrten bleiben so
     * erwartungstreu. Null ohne Energiedaten.
     */
    public BigDecimal consumedKwh(BigDecimal effectiveCapacityKwh) {
        if (estimatedConsumedKwh != null) return estimatedConsumedKwh;
        if (socStart == null || socEnd == null || effectiveCapacityKwh == null) return null;
        return socStart.subtract(socEnd).multiply(effectiveCapacityKwh).movePointLeft(2);
    }
}
