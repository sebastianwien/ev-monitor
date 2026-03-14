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
 * Integration test for SoC-based consumption calculation in EvLogService.
 */
class EvLogServiceSocIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        // Create test user
        User user = createAndSaveUser("soc-test-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();

        // Create test car with battery capacity
        Car car = Car.createNew(
            userId,
            CarBrand.CarModel.MODEL_3,
            2023,
            "SOC-123",
            "Long Range",
            new BigDecimal("75.0"), // 75 kWh battery
            new BigDecimal("275.0")
        );
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void shouldFallbackToKwhBasedCalculation_whenSocMissing() {
        // Given: Two logs WITHOUT SoC data
        EvLog log1 = EvLog.createNew(
            carId,
            new BigDecimal("20.0"),
            BigDecimal.TEN,
            60,
            "u33d1",
            10000,
            new BigDecimal("50.0"),
            null, // No SoC
            LocalDateTime.now().minusDays(2),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log1);

        EvLog log2 = EvLog.createNew(
            carId,
            new BigDecimal("20.0"),
            BigDecimal.TEN,
            60,
            "u33d2",
            10100,
            new BigDecimal("50.0"),
            null, // No SoC
            LocalDateTime.now().minusDays(1),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log2);

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // Then: Should fallback to kWh-based calculation
        assertNotNull(stats.avgConsumptionKwhPer100km());
        // Fallback formula: 20 kWh / 100 km × 100 = 20 kWh/100km
        assertEquals(new BigDecimal("20.00"), stats.avgConsumptionKwhPer100km());
    }

    @Test
    void shouldHandleRealisticDrivingScenario() {
        // Given: Realistic Tesla Model 3 Long Range scenario
        // Log 1: Home charge from 20% to 80% (45 kWh)
        EvLog log1 = EvLog.createNew(
            carId,
            new BigDecimal("45.0"),
            new BigDecimal("12.50"),
            180,
            "u33d1",
            15000,
            new BigDecimal("11.0"),
            80,
            LocalDateTime.now().minusDays(3),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log1);

        // Log 2: DC fast charge from 15% to 85% (52.5 kWh) after 300km highway trip
        //   Energy consumed: (80% - 15%) × 75 = 48.75 kWh
        //   Consumption: 48.75 / 300 × 100 = 16.25 kWh/100km
        EvLog log2 = EvLog.createNew(
            carId,
            new BigDecimal("52.5"),
            new BigDecimal("35.00"),
            45,
            "u45d2",
            15300,
            new BigDecimal("150.0"),
            85,
            LocalDateTime.now().minusDays(2),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log2);

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // Then
        assertNotNull(stats.avgConsumptionKwhPer100km());
        assertEquals(new BigDecimal("16.25"), stats.avgConsumptionKwhPer100km());
        assertEquals(new BigDecimal("300"), stats.totalDistanceKm());
    }

    @Test
    void shouldHandleMultipleLogs() {
        // Given: Three consecutive logs with all SoC data
        // Log1: 80% after charge, Odometer 20000
        EvLog log1 = EvLog.createNew(
            carId,
            new BigDecimal("45.0"),
            BigDecimal.TEN,
            120,
            "u33d1",
            20000,
            new BigDecimal("11.0"),
            80,
            LocalDateTime.now().minusDays(5),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log1);

        // Log2: 90% after charge, Odometer 20250 (250km driven)
        EvLog log2 = EvLog.createNew(
            carId,
            new BigDecimal("50.0"),
            new BigDecimal("15.0"),
            60,
            "u33d2",
            20250, // 250km driven
            new BigDecimal("150.0"),
            90,
            LocalDateTime.now().minusDays(3),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log2);

        // Log3: 75% after charge, Odometer 20400 (150km driven)
        EvLog log3 = EvLog.createNew(
            carId,
            new BigDecimal("40.0"),
            new BigDecimal("12.0"),
            75,
            "u33d3",
            20400, // 150km driven
            new BigDecimal("50.0"),
            75,
            LocalDateTime.now().minusDays(1),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log3);

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // Then: Should calculate average across both trips
        // Trip1 (log1→log2): Start 80% = 60kWh, End 23.33% = 17.5kWh → 42.5kWh / 250km
        // Trip2 (log2→log3): Start 90% = 67.5kWh, End 21.67% = 16.25kWh → 51.25kWh / 150km
        // Total: 93.75kWh / 400km × 100 = 23.44 kWh/100km
        assertNotNull(stats.avgConsumptionKwhPer100km());
        assertEquals(new BigDecimal("400"), stats.totalDistanceKm());
        assertEquals(new BigDecimal("23.44"), stats.avgConsumptionKwhPer100km());
    }

    @Test
    void shouldHandleComplexMixOfLogsWithAndWithoutOdometer() {
        // Given: Complex scenario with multiple logs with/without odometer
        // Log1: NO odometer, 30 kWh charged → 70% (ignored, before first anchor)
        EvLog log1 = EvLog.createNew(
            carId,
            new BigDecimal("30.0"),
            BigDecimal.TEN,
            90,
            "u33d1",
            null, // No odometer
            new BigDecimal("11.0"),
            70,
            LocalDateTime.now().minusDays(5),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log1);

        // Log2: Anchor Y - Odometer 10000, 40 kWh charged → 80%
        EvLog log2 = EvLog.createNew(
            carId,
            new BigDecimal("40.0"),
            BigDecimal.TEN,
            100,
            "u33d2",
            10000,
            new BigDecimal("11.0"),
            80,
            LocalDateTime.now().minusDays(4),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log2);

        // Log3: NO odometer, 25 kWh charged → 75% (included in calculation)
        EvLog log3 = EvLog.createNew(
            carId,
            new BigDecimal("25.0"),
            BigDecimal.TEN,
            80,
            "u33d3",
            null, // No odometer
            new BigDecimal("50.0"),
            75,
            LocalDateTime.now().minusDays(3),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log3);

        // Log4: Anchor X - Odometer 10350, 45 kWh charged → 85%
        EvLog log4 = EvLog.createNew(
            carId,
            new BigDecimal("45.0"),
            new BigDecimal("15.0"),
            90,
            "u33d4",
            10350, // 350km from log2
            new BigDecimal("150.0"),
            85,
            LocalDateTime.now().minusDays(2),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log4);

        // Log5: NO odometer, 20 kWh charged → 70% (ignored, after last anchor)
        EvLog log5 = EvLog.createNew(
            carId,
            new BigDecimal("20.0"),
            BigDecimal.TEN,
            60,
            "u33d5",
            null, // No odometer
            new BigDecimal("50.0"),
            70,
            LocalDateTime.now().minusDays(1),
            ChargingType.UNKNOWN,
            null, null
        );
        evLogRepository.save(log5);

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // Then: findPreviousLog() is strict — only the DIRECTLY preceding log (by loggedAt) may
        // be used as logX. Any log in between, even if incomplete, represents an unknown energy
        // event that would corrupt the SoC-delta calculation.
        //
        // For log2 (isComplete): directly preceding = log1 (no odometer) → canBeUsedAsLogX=false → skip
        // For log4 (isComplete): directly preceding = log3 (no odometer) → canBeUsedAsLogX=false → skip
        //
        // → SoC-based calculation returns null → fallback (kWh/km) with only log4 in distance set:
        //   45 kWh / 350 km × 100 = 12.86 kWh/100km
        assertNotNull(stats.avgConsumptionKwhPer100km());
        assertEquals(new BigDecimal("12.86"), stats.avgConsumptionKwhPer100km());
        assertEquals(new BigDecimal("350"), stats.totalDistanceKm());
    }

}
