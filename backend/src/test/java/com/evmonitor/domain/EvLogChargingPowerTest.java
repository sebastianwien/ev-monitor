package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Geschätzte Ladeleistung einer Ladung: Grundlage für die Ladeverlust-Klasse (unter/ab 4 kW).
 * Reihenfolge wie bei der AC/DC-Ableitung: maxChargingPowerKw, sonst kWh je Stunde, sonst unbekannt.
 */
class EvLogChargingPowerTest {

    @Test
    void maxPower_wins_overDuration() {
        EvLog log = build(new BigDecimal("11"), new BigDecimal("5"), null, 300);
        assertEquals(0, new BigDecimal("11").compareTo(log.estimatedChargingPowerKw()));
    }

    @Test
    void kwhPerHour_whenNoMaxPower() {
        EvLog log = build(null, new BigDecimal("20"), null, 120);
        assertEquals(0, new BigDecimal("10").compareTo(log.estimatedChargingPowerKw()));
    }

    @Test
    void nettoOnlyLog_usesKwhAtVehicle() {
        EvLog log = build(null, null, new BigDecimal("6"), 180);
        assertEquals(0, new BigDecimal("2").compareTo(log.estimatedChargingPowerKw()));
    }

    @Test
    void null_whenDurationBelowOneMinute() {
        assertNull(build(null, new BigDecimal("20"), null, 0).estimatedChargingPowerKw());
    }

    @Test
    void null_whenNoEnergyAndNoPower() {
        assertNull(build(null, null, null, 60).estimatedChargingPowerKw());
    }

    private EvLog build(BigDecimal maxPowerKw, BigDecimal kwhCharged, BigDecimal kwhAtVehicle, Integer durationMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(kwhCharged)
                .kwhAtVehicle(kwhAtVehicle)
                .costEur(BigDecimal.TEN)
                .chargeDurationMinutes(durationMinutes)
                .maxChargingPowerKw(maxPowerKw)
                .dataSource(DataSource.USER_LOGGED)
                .chargingType(ChargingType.AC)
                .includeInStatistics(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
