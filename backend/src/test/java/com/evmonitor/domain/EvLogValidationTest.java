package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EvLog validation methods: isComplete(), canBeUsedAsLogX(), hasKwhCharged().
 */
class EvLogValidationTest {

    private static final UUID CAR_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    // -------------------------------------------------------------------------
    // isComplete()
    // -------------------------------------------------------------------------

    @Test
    void isComplete_withAllRequiredFields_returnsTrue() {
        EvLog log = log(10000, new BigDecimal("45.0"), 80);
        assertTrue(log.isComplete());
    }

    @Test
    void isComplete_withNullOdometer_returnsFalse() {
        EvLog log = log(null, new BigDecimal("45.0"), 80);
        assertFalse(log.isComplete());
    }

    @Test
    void isComplete_withNullKwhCharged_returnsFalse() {
        EvLog log = log(10000, null, 80);
        assertFalse(log.isComplete());
    }

    @Test
    void isComplete_withNullSocAfterCharge_returnsFalse() {
        EvLog log = log(10000, new BigDecimal("45.0"), null);
        assertFalse(log.isComplete());
    }

    @Test
    void isComplete_withAllNullRequiredFields_returnsFalse() {
        EvLog log = log(null, null, null);
        assertFalse(log.isComplete());
    }

    // -------------------------------------------------------------------------
    // canBeUsedAsLogX()
    // -------------------------------------------------------------------------

    @Test
    void canBeUsedAsLogX_withOdometerAndSoc_returnsTrue() {
        EvLog log = log(10000, null, 80); // kwhCharged NOT required for logX
        assertTrue(log.canBeUsedAsLogX());
    }

    @Test
    void canBeUsedAsLogX_withAllFields_returnsTrue() {
        EvLog log = log(10000, new BigDecimal("45.0"), 80);
        assertTrue(log.canBeUsedAsLogX());
    }

    @Test
    void canBeUsedAsLogX_withNullOdometer_returnsFalse() {
        EvLog log = log(null, new BigDecimal("45.0"), 80);
        assertFalse(log.canBeUsedAsLogX());
    }

    @Test
    void canBeUsedAsLogX_withNullSoc_returnsFalse() {
        EvLog log = log(10000, new BigDecimal("45.0"), null);
        assertFalse(log.canBeUsedAsLogX());
    }

    @Test
    void canBeUsedAsLogX_withNullOdometerAndNullSoc_returnsFalse() {
        EvLog log = log(null, null, null);
        assertFalse(log.canBeUsedAsLogX());
    }

    // -------------------------------------------------------------------------
    // hasKwhCharged()
    // -------------------------------------------------------------------------

    @Test
    void hasKwhCharged_withKwhCharged_returnsTrue() {
        EvLog log = log(null, new BigDecimal("30.0"), null);
        assertTrue(log.hasKwhCharged());
    }

    @Test
    void hasKwhCharged_withNullKwhCharged_returnsFalse() {
        EvLog log = log(10000, null, 80);
        assertFalse(log.hasKwhCharged());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Creates an EvLog with the three fields that matter for validation.
     * All other fields are set to reasonable defaults or null.
     */
    private EvLog log(Integer odometerKm, BigDecimal kwhCharged, Integer socAfterChargePercent) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(CAR_ID)
                .kwhCharged(kwhCharged)
                .costEur(new BigDecimal("10.00"))
                .chargeDurationMinutes(60)
                .geohash("u33d1")
                .odometerKm(odometerKm)
                .maxChargingPowerKw(new BigDecimal("11.0"))
                .socAfterChargePercent(socAfterChargePercent)
                .loggedAt(NOW)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(ChargingType.UNKNOWN)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
