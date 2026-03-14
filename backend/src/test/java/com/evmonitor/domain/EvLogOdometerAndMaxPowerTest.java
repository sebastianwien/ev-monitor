package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 1: Odometer and Max Charging Power tracking.
 * Tests that the new optional fields work correctly.
 */
class EvLogOdometerAndMaxPowerTest {

    @Test
    void createNew_withOdometerAndMaxPower_shouldStoreValues() {
        // Given
        UUID carId = UUID.randomUUID();
        Integer odometerKm = 50000;
        BigDecimal maxChargingPowerKw = BigDecimal.valueOf(150.0);

        // When
        EvLog evLog = EvLog.createNew(
                carId,
                BigDecimal.valueOf(45.5),
                BigDecimal.valueOf(12.75),
                90,
                "u33d1",
                odometerKm,
                maxChargingPowerKw,
                null, // socAfterChargePercent
                LocalDateTime.now(),
                ChargingType.UNKNOWN,
                null, null
        );

        // Then
        assertEquals(odometerKm, evLog.getOdometerKm());
        assertEquals(maxChargingPowerKw, evLog.getMaxChargingPowerKw());
    }

    @Test
    void createNew_withoutOptionalFields_shouldAllowNulls() {
        // Given
        UUID carId = UUID.randomUUID();

        // When
        EvLog evLog = EvLog.createNew(
                carId,
                BigDecimal.valueOf(30.0),
                BigDecimal.valueOf(8.50),
                60,
                "u33d2",
                null, // No odometer
                null, // No max power
                null, // No socAfterChargePercent
                LocalDateTime.now(),
                ChargingType.UNKNOWN,
                null, null
        );

        // Then
        assertNull(evLog.getOdometerKm());
        assertNull(evLog.getMaxChargingPowerKw());
        assertNull(evLog.getSocAfterChargePercent());
    }

    @Test
    void odometerKm_canBeZero() {
        // Given: Brand new car with 0 km (edge case)
        UUID carId = UUID.randomUUID();
        Integer odometerKm = 0;

        // When
        EvLog evLog = EvLog.createNew(
                carId,
                BigDecimal.valueOf(30.0),
                BigDecimal.valueOf(8.50),
                60,
                null,
                odometerKm,
                null,
                null, // socAfterChargePercent
                LocalDateTime.now(),
                ChargingType.UNKNOWN,
                null, null
        );

        // Then
        assertEquals(0, evLog.getOdometerKm());
    }

    @Test
    void maxChargingPowerKw_canBeFractional() {
        // Given: Charging power with decimals (e.g., 150.5 kW)
        UUID carId = UUID.randomUUID();
        BigDecimal maxChargingPowerKw = BigDecimal.valueOf(150.75);

        // When
        EvLog evLog = EvLog.createNew(
                carId,
                BigDecimal.valueOf(30.0),
                BigDecimal.valueOf(8.50),
                60,
                null,
                null,
                maxChargingPowerKw,
                null, // socAfterChargePercent
                LocalDateTime.now(),
                ChargingType.UNKNOWN,
                null, null
        );

        // Then
        assertEquals(0, maxChargingPowerKw.compareTo(evLog.getMaxChargingPowerKw()));
    }

    @Test
    void odometerTracking_scenario_multipleChargingSessions() {
        // Scenario: User logs 3 charging sessions with increasing odometer readings
        UUID carId = UUID.randomUUID();

        // Session 1: 50,000 km
        EvLog session1 = EvLog.createNew(
                carId, BigDecimal.valueOf(45.0), BigDecimal.valueOf(12.0), 90,
                "u33d1", 50000, BigDecimal.valueOf(150.0), null, LocalDateTime.now().minusDays(3),
                ChargingType.UNKNOWN, null, null
        );

        // Session 2: 50,250 km (drove 250 km)
        EvLog session2 = EvLog.createNew(
                carId, BigDecimal.valueOf(40.0), BigDecimal.valueOf(11.0), 85,
                "u33d2", 50250, BigDecimal.valueOf(145.0), null, LocalDateTime.now().minusDays(2),
                ChargingType.UNKNOWN, null, null
        );

        // Session 3: 50,500 km (drove another 250 km)
        EvLog session3 = EvLog.createNew(
                carId, BigDecimal.valueOf(38.0), BigDecimal.valueOf(10.0), 80,
                "u33d3", 50500, BigDecimal.valueOf(140.0), null, LocalDateTime.now().minusDays(1),
                ChargingType.UNKNOWN, null, null
        );

        // Then: All sessions have correct odometer readings
        assertEquals(50000, session1.getOdometerKm());
        assertEquals(50250, session2.getOdometerKm());
        assertEquals(50500, session3.getOdometerKm());

        // Verify increasing order
        assertTrue(session1.getOdometerKm() < session2.getOdometerKm());
        assertTrue(session2.getOdometerKm() < session3.getOdometerKm());
    }
}
