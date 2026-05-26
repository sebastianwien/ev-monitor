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

class EvLogStatisticsChargingTypeSplitTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("split-test-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();
        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "SPLIT-1",
                "Standard", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void chargingTypeSplit_shouldSumKwhByType() {
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("30.0"), null,
                60, "u33d1", null, new BigDecimal("11.0"), null,
                LocalDateTime.now().minusDays(3), ChargingType.AC, null, null, false, null));
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("50.0"), null,
                30, "u33d2", null, new BigDecimal("150.0"), null,
                LocalDateTime.now().minusDays(2), ChargingType.DC, null, null, true, null));
        // null duration → inference can't compute avg power → stays UNKNOWN
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("20.0"), null,
                null, "u33d3", null, null, null,
                LocalDateTime.now().minusDays(1), ChargingType.UNKNOWN, null, null, false, null));

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertNotNull(stats.chargingTypeSplit());
        assertEquals(0, new BigDecimal("30.0").compareTo(stats.chargingTypeSplit().acKwh()));
        assertEquals(0, new BigDecimal("50.0").compareTo(stats.chargingTypeSplit().dcKwh()));
        assertEquals(0, new BigDecimal("20.0").compareTo(stats.chargingTypeSplit().unknownKwh()));
    }

    @Test
    void locationSplit_shouldSumKwhByPublicAndPrivate() {
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), null,
                60, "u33d1", null, null, null,
                LocalDateTime.now().minusDays(2), ChargingType.AC, null, null, true, null));
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("25.0"), null,
                45, "u33d2", null, null, null,
                LocalDateTime.now().minusDays(1), ChargingType.AC, null, null, false, null));

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertNotNull(stats.locationSplit());
        assertEquals(0, new BigDecimal("40.0").compareTo(stats.locationSplit().publicKwh()));
        assertEquals(0, new BigDecimal("25.0").compareTo(stats.locationSplit().privateKwh()));
    }

    @Test
    void splits_shouldOnlyCountLogsIncludedInStatistics() {
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("30.0"), null,
                60, "u33d1", null, new BigDecimal("11.0"), null,
                LocalDateTime.now().minusDays(2), ChargingType.AC, null, null, false, null));
        // excluded log
        EvLog excluded = EvLog.createNew(carId, new BigDecimal("99.0"), null,
                60, "u33d2", null, new BigDecimal("11.0"), null,
                LocalDateTime.now().minusDays(1), ChargingType.DC, null, null, true, null);
        evLogRepository.save(excluded.withIncludeInStatistics(false));

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertEquals(0, new BigDecimal("30.0").compareTo(stats.chargingTypeSplit().acKwh()));
        assertEquals(0, new BigDecimal("0.0").compareTo(stats.chargingTypeSplit().dcKwh()));
    }
}
