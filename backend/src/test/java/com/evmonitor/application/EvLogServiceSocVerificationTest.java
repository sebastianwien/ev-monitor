package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test to ensure correct consumption calculation between two charging sessions.
 *
 * Given: 2 logs with complete data (socAfterCharge, odometer, batteryCapacity, kwhCharged)
 * Then: Consumption should be calculated correctly using SoC-based formula
 */
class EvLogServiceSocVerificationTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("verification-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();

        // Tesla Model 3 Long Range with 75 kWh battery
        Car car = Car.createNew(
            userId,
            CarBrand.CarModel.MODEL_3,
            2023,
            "VERIFY-123",
            "Long Range",
            new BigDecimal("75.0"), // 75 kWh battery
            new BigDecimal("275.0"),
            null
        );
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void shouldCalculateCorrectConsumptionBetweenTwoCompleteLogs() {
        // ============================================
        // SCENARIO: User charges twice, drives between
        // ============================================

        // Log 1 - First charge at home
        // Charged: 45 kWh
        // SoC after charge: 80%
        // Odometer: 10000 km
        EvLog log1 = EvLog.createNew(
            carId,
            new BigDecimal("45.0"),        // kWh charged
            new BigDecimal("12.50"),        // cost
            180,                            // duration
            "u33d1",                        // geohash
            10000,                          // odometer
            new BigDecimal("11.0"),         // max power
            80,                             // SoC AFTER charge
            LocalDateTime.now().minusDays(2),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log1);

        // ============================================
        // User drives 300 km
        // ============================================

        // Log 2 - Second charge at supercharger after 300km trip
        // Charged: 52.5 kWh
        // SoC after charge: 85%
        // Odometer: 10300 km (300km driven)
        EvLog log2 = EvLog.createNew(
            carId,
            new BigDecimal("52.5"),         // kWh charged
            new BigDecimal("35.00"),        // cost
            45,                             // duration
            "u45d2",                        // geohash
            10300,                          // odometer (300km driven!)
            new BigDecimal("150.0"),        // max power
            85,                             // SoC AFTER charge
            LocalDateTime.now().minusDays(1),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log2);

        // ============================================
        // CALCULATION (manual verification)
        // ============================================

        // Step 1: Calculate SoC BEFORE each charge
        // log1 before: 80% - (45/75×100) = 80% - 60% = 20%
        // log2 before: 85% - (52.5/75×100) = 85% - 70% = 15%

        // Step 2: Energy in tank (absolute kWh)
        // log1 after:  80% × 75 kWh = 60.0 kWh
        // log2 before: 15% × 75 kWh = 11.25 kWh

        // Step 3: Energy consumed during trip
        // Consumed = 60.0 - 11.25 = 48.75 kWh

        // Step 4: Consumption per 100km
        // 48.75 kWh / 300 km × 100 = 16.25 kWh/100km

        // ============================================
        // EXPECTED: 16.25 kWh/100km
        // ============================================

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // Then
        assertNotNull(stats.avgConsumptionKwhPer100km(), "Consumption should be calculated");
        assertEquals(new BigDecimal("300"), stats.totalDistanceKm(), "Distance should be 300km");
        assertEquals(new BigDecimal("16.25"), stats.avgConsumptionKwhPer100km(),
            "Consumption should be 16.25 kWh/100km (48.75 kWh consumed over 300km)");
    }

    @Test
    void shouldCalculateCorrectConsumptionWithPartialCharges() {
        // ============================================
        // SCENARIO: Partial charging (not full battery)
        // This is the critical case where kWh-based calculation fails!
        // ============================================

        // Log 1 - Partial charge: 20% → 80%
        // Charged: 45 kWh (60% of battery = 45 kWh)
        // SoC after charge: 80%
        // Odometer: 15000 km
        EvLog log1 = EvLog.createNew(
            carId,
            new BigDecimal("45.0"),
            new BigDecimal("10.00"),
            120,
            "u33d3",
            15000,
            new BigDecimal("11.0"),
            80,
            LocalDateTime.now().minusDays(2),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log1);

        // User drives 150km

        // Log 2 - Partial charge: 50% → 90%
        // Charged: 30 kWh (40% of battery = 30 kWh)
        // SoC after charge: 90%
        // Odometer: 15150 km (150km driven)
        EvLog log2 = EvLog.createNew(
            carId,
            new BigDecimal("30.0"),
            new BigDecimal("20.00"),
            90,
            "u45d4",
            15150,
            new BigDecimal("150.0"),
            90,
            LocalDateTime.now().minusDays(1),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log2);

        // ============================================
        // CALCULATION
        // ============================================

        // OLD (WRONG) kWh-based formula:
        // (45 + 30) / 150 × 100 = 50 kWh/100km ❌ WRONG!

        // CORRECT SoC-based formula:
        // log1 after:  80% × 75 = 60.0 kWh
        // log2 before: (90% - 30/75×100) = 50% × 75 = 37.5 kWh
        // Consumed: 60.0 - 37.5 = 22.5 kWh
        // Consumption: 22.5 / 150 × 100 = 15.0 kWh/100km ✅

        // ============================================
        // EXPECTED: 15.0 kWh/100km (NOT 50!)
        // ============================================

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // Then
        assertNotNull(stats.avgConsumptionKwhPer100km());
        assertEquals(new BigDecimal("150"), stats.totalDistanceKm());
        assertEquals(new BigDecimal("15.00"), stats.avgConsumptionKwhPer100km(),
            "SoC-based calculation should give 15.0 kWh/100km, NOT the incorrect kWh-based 50.0");
    }

    @Test
    void shouldHandleBatteryCapacityChange() {
        // ============================================
        // SCENARIO: User corrects battery capacity AFTER logging
        // On-the-fly calculation should use NEW capacity
        // ============================================

        // Log with ASSUMED 75 kWh battery
        EvLog log1 = EvLog.createNew(
            carId,
            new BigDecimal("40.0"),
            BigDecimal.TEN,
            100,
            "u33d5",
            20000,
            new BigDecimal("11.0"),
            80,
            LocalDateTime.now().minusDays(2),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log1);

        EvLog log2 = EvLog.createNew(
            carId,
            new BigDecimal("45.0"),
            BigDecimal.TEN,
            90,
            "u45d6",
            20200,
            new BigDecimal("11.0"),
            85,
            LocalDateTime.now().minusDays(1),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log2);

        // When: Calculate with original 75 kWh
        EvLogStatisticsResponse statsOld = evLogService.getStatistics(carId, userId, null, null, null);
        BigDecimal consumptionWith75kWh = statsOld.avgConsumptionKwhPer100km();

        // User discovers battery is actually 78 kWh
        Car car = carRepository.findById(carId).orElseThrow();
        Car updatedCar = new Car(
            car.getId(), car.getUserId(), car.getModel(), car.getYear(),
            car.getLicensePlate(), car.getTrim(),
            new BigDecimal("78.0"), // Changed from 75 to 78 kWh
            car.getPowerKw(), car.getRegistrationDate(), car.getDeregistrationDate(),
            car.getStatus(), car.getCreatedAt(), LocalDateTime.now(),
            car.getImagePath(), car.isImagePublic(), car.isPrimary()
        );
        carRepository.save(updatedCar);

        // When: Calculate with corrected 78 kWh
        EvLogStatisticsResponse statsNew = evLogService.getStatistics(carId, userId, null, null, null);
        BigDecimal consumptionWith78kWh = statsNew.avgConsumptionKwhPer100km();

        // Then: Consumption values should differ (because battery capacity changed)
        assertNotNull(consumptionWith75kWh);
        assertNotNull(consumptionWith78kWh);
        assertNotEquals(consumptionWith75kWh, consumptionWith78kWh,
            "Consumption should recalculate with new battery capacity on-the-fly");

        // With larger battery, same kWh charged represents smaller SoC% drop
        // Example: 45 kWh / 75 = 60% drop vs 45 kWh / 78 = 57.7% drop
        // Smaller SoC drop → less consumed → lower consumption
        assertTrue(consumptionWith78kWh.compareTo(consumptionWith75kWh) < 0,
            "Larger battery capacity should result in lower calculated consumption (same kWh charged = smaller SoC% drop)");
    }
}
