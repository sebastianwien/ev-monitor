package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: Battery degradation affects SoC-based consumption calculation.
 *
 * Formula: consumption = (prevSoc - currSoc) % * effectiveBatteryKwh / distanceKm * 100
 * With 75 kWh nominal and 10% degradation → effectiveKwh = 67.5
 * Same SoC delta and distance → lower kWh consumed → different consumption result
 */
class EvLogServiceDegradationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    /**
     * Without degradation: 75 kWh battery
     * Log1: soc=80, odometer=15000 (baseline)
     * Log2: soc=15, odometer=15300 (+300 km)
     * Energy consumed: (80-15)% * 75 kWh = 48.75 kWh
     * Consumption: 48.75 / 300 * 100 = 16.25 kWh/100km
     *
     * With 10% degradation: effective = 67.5 kWh
     * Energy consumed: (80-15)% * 67.5 kWh = 43.875 kWh
     * Consumption: 43.875 / 300 * 100 = 14.625 → rounds to 14.63 kWh/100km
     */
    @Test
    void degradationReducesCalculatedConsumption() {
        // --- Car WITHOUT degradation ---
        User userA = createAndSaveUser("degradation-a-" + System.currentTimeMillis() + "@example.com");
        Car carNoDeg = Car.createNew(
                userA.getId(), CarBrand.CarModel.MODEL_3, 2023,
                "NODEG-1", "LR", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(carNoDeg);

        // baseline log (soc=80 after charge)
        evLogRepository.save(EvLog.createNew(
                carNoDeg.getId(), new BigDecimal("30.0"), new BigDecimal("8.0"),
                60, "u33d1", 15000, null, 80,
                LocalDateTime.now().minusDays(3), ChargingType.UNKNOWN, null, null));

        // second log (soc=15 after 300 km)
        evLogRepository.save(EvLog.createNew(
                carNoDeg.getId(), new BigDecimal("52.0"), new BigDecimal("14.0"),
                45, "u33d2", 15300, null, 15,
                LocalDateTime.now().minusDays(2), ChargingType.UNKNOWN, null, null));

        // --- Car WITH 10% degradation ---
        User userB = createAndSaveUser("degradation-b-" + System.currentTimeMillis() + "@example.com");
        Car carWithDeg = Car.createNew(
                userB.getId(), CarBrand.CarModel.MODEL_3, 2023,
                "DEG10-1", "LR", new BigDecimal("75.0"), new BigDecimal("275.0"),
                new BigDecimal("10.0")); // 10% degradation → 67.5 kWh effective
        carRepository.save(carWithDeg);

        evLogRepository.save(EvLog.createNew(
                carWithDeg.getId(), new BigDecimal("30.0"), new BigDecimal("8.0"),
                60, "u33d3", 15000, null, 80,
                LocalDateTime.now().minusDays(3), ChargingType.UNKNOWN, null, null));

        evLogRepository.save(EvLog.createNew(
                carWithDeg.getId(), new BigDecimal("52.0"), new BigDecimal("14.0"),
                45, "u33d4", 15300, null, 15,
                LocalDateTime.now().minusDays(2), ChargingType.UNKNOWN, null, null));

        // --- Compare ---
        EvLogStatisticsResponse statsNoDeg = evLogService.getStatistics(carNoDeg.getId(), userA.getId(), null, null, null);
        EvLogStatisticsResponse statsWithDeg = evLogService.getStatistics(carWithDeg.getId(), userB.getId(), null, null, null);

        assertNotNull(statsNoDeg.avgConsumptionKwhPer100km());
        assertNotNull(statsWithDeg.avgConsumptionKwhPer100km());

        // Without degradation: (80-15)% * 75 / 300 * 100 = 16.25 kWh/100km
        assertEquals(new BigDecimal("16.25"), statsNoDeg.avgConsumptionKwhPer100km(),
                "No degradation: consumption should use nominal 75 kWh");

        // With 10% degradation: (80-15)% * 67.5 / 300 * 100 = 14.625 → 14.63 kWh/100km
        assertEquals(new BigDecimal("14.63"), statsWithDeg.avgConsumptionKwhPer100km(),
                "With degradation: consumption should use effective 67.5 kWh");

        // The degraded car must show LOWER consumption (same distance, less kWh counted)
        assertTrue(
                statsWithDeg.avgConsumptionKwhPer100km()
                        .compareTo(statsNoDeg.avgConsumptionKwhPer100km()) < 0,
                "Degraded car must show lower consumption than nominal");
    }

    @Test
    void noDegradationFallsBackToNominalCapacity() {
        User user = createAndSaveUser("nodeg-fallback-" + System.currentTimeMillis() + "@example.com");
        Car car = Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2023,
                "NODEG-2", "LR", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);

        evLogRepository.save(EvLog.createNew(
                car.getId(), new BigDecimal("30.0"), new BigDecimal("8.0"),
                60, "u33d5", 15000, null, 80,
                LocalDateTime.now().minusDays(2), ChargingType.UNKNOWN, null, null));

        evLogRepository.save(EvLog.createNew(
                car.getId(), new BigDecimal("48.75"), new BigDecimal("14.0"),
                45, "u33d6", 15300, null, 15,
                LocalDateTime.now().minusDays(1), ChargingType.UNKNOWN, null, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(car.getId(), user.getId(), null, null, null);

        assertNotNull(stats.avgConsumptionKwhPer100km());
        // (80-15)% * 75 / 300 * 100 = 16.25
        assertEquals(new BigDecimal("16.25"), stats.avgConsumptionKwhPer100km());
    }
}
