package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for ChargingType inference in EvLog constructor.
 *
 * Priority:
 * 1. Explicit type (AC/DC) → always kept as-is
 * 2. maxChargingPowerKw: >22 kW = DC, ≤22 kW = AC
 * 3. kWh / duration (if duration ≥ 5 min): >22 kW avg = DC, ≤22 kW avg = AC
 * 4. No data → UNKNOWN
 */
class EvLogChargingTypeInferenceTest {

    @Test
    void explicitAc_neverOverridden() {
        EvLog log = buildLog(ChargingType.AC, new BigDecimal("150"), null, null);
        assertEquals(ChargingType.AC, log.getChargingType());
    }

    @Test
    void explicitDc_neverOverridden() {
        EvLog log = buildLog(ChargingType.DC, new BigDecimal("7"), null, null);
        assertEquals(ChargingType.DC, log.getChargingType());
    }

    @Test
    void maxPower_above22_infersDc() {
        EvLog log = buildLog(ChargingType.UNKNOWN, new BigDecimal("50"), null, null);
        assertEquals(ChargingType.DC, log.getChargingType());
    }

    @Test
    void maxPower_exactly22_infersAc() {
        EvLog log = buildLog(ChargingType.UNKNOWN, new BigDecimal("22"), null, null);
        assertEquals(ChargingType.AC, log.getChargingType());
    }

    @Test
    void maxPower_below22_infersAc() {
        EvLog log = buildLog(ChargingType.UNKNOWN, new BigDecimal("11"), null, null);
        assertEquals(ChargingType.AC, log.getChargingType());
    }

    @Test
    void maxPower_takesPreferenceOverKwhDuration() {
        // maxPowerKw=11 (AC) but kWh/duration would suggest DC (50kWh/0.5h = 100kW)
        EvLog log = buildLog(ChargingType.UNKNOWN, new BigDecimal("11"), new BigDecimal("50"), 30);
        assertEquals(ChargingType.AC, log.getChargingType());
    }

    @Test
    void kwhDuration_above22kwAvg_infersDc() {
        // 50 kWh / 1h = 50 kW → DC
        EvLog log = buildLog(ChargingType.UNKNOWN, null, new BigDecimal("50"), 60);
        assertEquals(ChargingType.DC, log.getChargingType());
    }

    @Test
    void kwhDuration_below22kwAvg_infersAc() {
        // 11 kWh / 1h = 11 kW → AC
        EvLog log = buildLog(ChargingType.UNKNOWN, null, new BigDecimal("11"), 60);
        assertEquals(ChargingType.AC, log.getChargingType());
    }

    @Test
    void kwhDuration_exactly22kwAvg_infersAc() {
        // 22 kWh / 1h = 22 kW → AC (boundary: not strictly >22)
        EvLog log = buildLog(ChargingType.UNKNOWN, null, new BigDecimal("22"), 60);
        assertEquals(ChargingType.AC, log.getChargingType());
    }

    @Test
    void kwhDuration_shortSession_belowMinDuration_remainsUnknown() {
        // 4 minutes is below the 5-minute minimum → can't reliably infer
        EvLog log = buildLog(ChargingType.UNKNOWN, null, new BigDecimal("50"), 4);
        assertEquals(ChargingType.UNKNOWN, log.getChargingType());
    }

    @Test
    void noData_remainsUnknown() {
        EvLog log = buildLog(ChargingType.UNKNOWN, null, null, null);
        assertEquals(ChargingType.UNKNOWN, log.getChargingType());
    }

    @Test
    void nullType_treatedAsUnknown_inferredFromPower() {
        EvLog log = buildLog(null, new BigDecimal("50"), null, null);
        assertEquals(ChargingType.DC, log.getChargingType());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private EvLog buildLog(ChargingType chargingType, BigDecimal maxPowerKw,
                            BigDecimal kwhCharged, Integer durationMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(
                UUID.randomUUID(), UUID.randomUUID(),
                kwhCharged, new BigDecimal("10.00"), durationMinutes, "u33d1",
                10000, maxPowerKw, 80, null,
                now, DataSource.USER_LOGGED, true,
                null, null, null, chargingType, null,
                now, now, null, null, null, null,
                false, null);
    }
}
