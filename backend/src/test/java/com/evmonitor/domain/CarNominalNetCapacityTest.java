package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * getNominalNetCapacityKwh() ist die Single Source of Truth fuer
 * "wieviel kWh netto hat dieses Auto laut Stammdaten" (ohne SoH-Adjustierung).
 *
 * Vorrang: specNetBatteryCapacityKwh (verifiziert) ueber customNetCapacityKwh (User-Eingabe).
 * Wird verwendet wo SoH-Berechnungen die nominale Basis brauchen (BatterySohService).
 */
class CarNominalNetCapacityTest {

    private Car car(BigDecimal customNet, BigDecimal specNet) {
        return Car.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .model(CarBrand.CarModel.ENYAQ).year(2025)
                .customNetCapacityKwh(customNet)
                .specNetBatteryCapacityKwh(specNet)
                .status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .registrationDate(LocalDate.of(2025, 1, 1))
                .build();
    }

    @Test
    void prefersSpecNet_overCustomNet() {
        Car c = car(new BigDecimal("82.0"), new BigDecimal("77.0"));
        assertEquals(0, new BigDecimal("77.0").compareTo(c.getNominalNetCapacityKwh()));
    }

    @Test
    void fallsBackToCustomNet_whenSpecNetMissing() {
        Car c = car(new BigDecimal("82.0"), null);
        assertEquals(0, new BigDecimal("82.0").compareTo(c.getNominalNetCapacityKwh()));
    }

    @Test
    void returnsNull_whenBothMissing() {
        assertNull(car(null, null).getNominalNetCapacityKwh());
    }
}
