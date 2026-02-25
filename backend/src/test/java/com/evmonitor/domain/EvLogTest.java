package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EvLogTest {

    @Test
    void createNew_withAllFields_shouldCreateValidEvLog() {
        // Given
        UUID carId = UUID.randomUUID();
        BigDecimal kwhCharged = BigDecimal.valueOf(45.5);
        BigDecimal costEur = BigDecimal.valueOf(12.75);
        Integer chargeDurationMinutes = 90;
        String geohash = "u33d1";
        Integer odometerKm = 50000;
        BigDecimal maxChargingPowerKw = BigDecimal.valueOf(150.0);
        LocalDateTime loggedAt = LocalDateTime.of(2026, 2, 25, 14, 30);

        // When
        EvLog evLog = EvLog.createNew(carId, kwhCharged, costEur, chargeDurationMinutes,
                geohash, odometerKm, maxChargingPowerKw, loggedAt);

        // Then
        assertNotNull(evLog.getId());
        assertEquals(carId, evLog.getCarId());
        assertEquals(kwhCharged, evLog.getKwhCharged());
        assertEquals(costEur, evLog.getCostEur());
        assertEquals(chargeDurationMinutes, evLog.getChargeDurationMinutes());
        assertEquals(geohash, evLog.getGeohash());
        assertEquals(odometerKm, evLog.getOdometerKm());
        assertEquals(maxChargingPowerKw, evLog.getMaxChargingPowerKw());
        assertEquals(loggedAt, evLog.getLoggedAt());
        assertNotNull(evLog.getCreatedAt());
        assertNotNull(evLog.getUpdatedAt());
    }

    @Test
    void createNew_withNullOptionalFields_shouldCreateValidEvLog() {
        // Given
        UUID carId = UUID.randomUUID();
        BigDecimal kwhCharged = BigDecimal.valueOf(30.0);
        BigDecimal costEur = BigDecimal.valueOf(8.50);
        Integer chargeDurationMinutes = 60;
        String geohash = "u33d2";
        Integer odometerKm = null; // Optional
        BigDecimal maxChargingPowerKw = null; // Optional
        LocalDateTime loggedAt = null; // Should default to now

        // When
        EvLog evLog = EvLog.createNew(carId, kwhCharged, costEur, chargeDurationMinutes,
                geohash, odometerKm, maxChargingPowerKw, loggedAt);

        // Then
        assertNotNull(evLog.getId());
        assertEquals(carId, evLog.getCarId());
        assertNull(evLog.getOdometerKm());
        assertNull(evLog.getMaxChargingPowerKw());
        assertNotNull(evLog.getLoggedAt()); // Should default to now
    }

    @Test
    void createNew_withNullGeohash_shouldAllowNullGeohash() {
        // Given
        UUID carId = UUID.randomUUID();
        String geohash = null; // Location tracking is optional

        // When
        EvLog evLog = EvLog.createNew(carId, BigDecimal.TEN, BigDecimal.ONE, 30,
                geohash, null, null, LocalDateTime.now());

        // Then
        assertNull(evLog.getGeohash());
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        // Given
        UUID id = UUID.randomUUID();
        UUID carId = UUID.randomUUID();
        BigDecimal kwhCharged = BigDecimal.valueOf(50.0);
        BigDecimal costEur = BigDecimal.valueOf(15.0);
        Integer chargeDurationMinutes = 120;
        String geohash = "u33d3";
        Integer odometerKm = 75000;
        BigDecimal maxChargingPowerKw = BigDecimal.valueOf(175.5);
        LocalDateTime loggedAt = LocalDateTime.now();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        // When
        EvLog evLog = new EvLog(id, carId, kwhCharged, costEur, chargeDurationMinutes,
                geohash, odometerKm, maxChargingPowerKw, loggedAt, createdAt, updatedAt);

        // Then
        assertEquals(id, evLog.getId());
        assertEquals(carId, evLog.getCarId());
        assertEquals(kwhCharged, evLog.getKwhCharged());
        assertEquals(costEur, evLog.getCostEur());
        assertEquals(chargeDurationMinutes, evLog.getChargeDurationMinutes());
        assertEquals(geohash, evLog.getGeohash());
        assertEquals(odometerKm, evLog.getOdometerKm());
        assertEquals(maxChargingPowerKw, evLog.getMaxChargingPowerKw());
        assertEquals(loggedAt, evLog.getLoggedAt());
        assertEquals(createdAt, evLog.getCreatedAt());
        assertEquals(updatedAt, evLog.getUpdatedAt());
    }
}
