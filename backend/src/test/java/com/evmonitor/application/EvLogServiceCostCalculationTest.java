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
 * Integration tests for cost calculation in EvLogService.
 * Verifies that logs with 0€ cost are excluded from avgCostPerKwh.
 */
class EvLogServiceCostCalculationTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("cost-test-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();

        Car car = Car.createNew(
                userId,
                CarBrand.CarModel.MODEL_3,
                2023,
                "COST-123",
                "Standard",
                new BigDecimal("75.0"),
                new BigDecimal("275.0"),
                null
        );
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void avgCostPerKwh_shouldExcludeZeroCostLogs() {
        // 30 kWh @ 0.30 €/kWh = 9.00 € → valid
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("30.0"), new BigDecimal("9.00"),
                60, "u33d1", 10000, new BigDecimal("50.0"), null,
                LocalDateTime.now().minusDays(2), ChargingType.UNKNOWN, null, null));

        // 20 kWh @ 0.00 € → should be excluded
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("20.0"), BigDecimal.ZERO,
                60, "u33d2", 10100, new BigDecimal("50.0"), null,
                LocalDateTime.now().minusDays(1), ChargingType.UNKNOWN, null, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // totalCostEur = 9.00 (0€ log excluded from cost sum)
        // avgCostPerKwh = 9.00 / 50 kWh total = 0.18 (totalKwhCharged includes all logs)
        assertEquals(0, new BigDecimal("9.00").compareTo(stats.totalCostEur()),
                "totalCostEur should only count logs with cost > 0");
        assertNotNull(stats.avgCostPerKwh());
        assertEquals(0, new BigDecimal("0.18").compareTo(stats.avgCostPerKwh()),
                "avgCostPerKwh should be 9.00 / 50 kWh = 0.18 (0€ log excluded from cost, but kWh counted)");
    }

    @Test
    void avgCostPerKwh_shouldBeZeroWhenAllLogsHaveZeroCost() {
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("30.0"), BigDecimal.ZERO,
                60, "u33d1", 10000, new BigDecimal("50.0"), null,
                LocalDateTime.now().minusDays(2), ChargingType.UNKNOWN, null, null));

        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("20.0"), BigDecimal.ZERO,
                60, "u33d2", 10100, new BigDecimal("50.0"), null,
                LocalDateTime.now().minusDays(1), ChargingType.UNKNOWN, null, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(stats.totalCostEur()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.avgCostPerKwh()));
    }

    @Test
    void avgCostPerKwh_shouldIncludeNullCostLogsInKwhButNotInCost() {
        // 40 kWh @ 12.00 € → valid
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("12.00"),
                60, "u33d1", 10000, new BigDecimal("50.0"), null,
                LocalDateTime.now().minusDays(2), ChargingType.UNKNOWN, null, null));

        // 20 kWh @ null cost → no cost contribution
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("20.0"), null,
                60, "u33d2", 10100, new BigDecimal("50.0"), null,
                LocalDateTime.now().minusDays(1), ChargingType.UNKNOWN, null, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        assertEquals(0, new BigDecimal("12.00").compareTo(stats.totalCostEur()),
                "null-cost logs should not affect totalCostEur");
    }
}
