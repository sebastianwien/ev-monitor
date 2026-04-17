package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: SoH auto-detection from AT_VEHICLE logs.
 *
 * Validates that BatterySohService.autoDetectAndPersist() correctly
 * derives and persists SoH entries from Smartcar/Tesla Live charging logs.
 */
class BatterySohAutoDetectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BatterySohService batterySohService;

    private EvLog smartcarLog(UUID carId, double kwh, int socBefore, int socAfter, int daysAgo) {
        return EvLog.createFromInternal(
                carId, new BigDecimal(String.valueOf(kwh)),
                60, null,
                LocalDateTime.now().minusDays(daysAgo),
                null, null,
                DataSource.SMARTCAR_LIVE, null, ChargingType.AC,
                60000 + daysAgo, socBefore, socAfter, null);
    }

    @Test
    void autoDetectAndPersist_createsSohEntryFromQualifyingLogs() {
        User user = createAndSaveUser("soh-detect-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "AB-CD-123", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // 44.17 / 64 * 100 = 69.02 kWh → SoH = 92.02%
        evLogRepository.save(smartcarLog(car.getId(), 44.17, 26, 90, 2));
        // 54.06 / 78 * 100 = 69.31 kWh → SoH = 92.41%
        evLogRepository.save(smartcarLog(car.getId(), 54.06, 13, 91, 5));

        batterySohService.autoDetectAndPersist(car);

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size(), "Exactly one SoH entry should be created");
        // median of [69.02, 69.31] → upper-middle = 69.31 kWh → 92.41%
        assertTrue(history.get(0).sohPercent().compareTo(new BigDecimal("90")) > 0,
                "SoH should be above 90%");
        assertTrue(history.get(0).sohPercent().compareTo(new BigDecimal("100")) <= 0,
                "SoH must not exceed 100%");
    }

    @Test
    void autoDetectAndPersist_doesNotCreateSecondEntryForSameDay() {
        User user = createAndSaveUser("soh-dedup-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "DE-DU-P01", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        evLogRepository.save(smartcarLog(car.getId(), 44.17, 26, 90, 1));

        batterySohService.autoDetectAndPersist(car);
        batterySohService.autoDetectAndPersist(car); // second call same day

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size(), "Should not create duplicate entry for same day");
    }

    @Test
    void autoDetectAndPersist_doesNothingWhenNoQualifyingLogs() {
        User user = createAndSaveUser("soh-noop-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "NO-OP-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // AT_CHARGER log (USER_LOGGED) → not qualifying
        evLogRepository.save(EvLog.createNew(
                car.getId(), new BigDecimal("44.0"), null, 60, null, 61000,
                null, 90, LocalDateTime.now().minusDays(1),
                ChargingType.AC, null, null, false, null));

        batterySohService.autoDetectAndPersist(car);

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertTrue(history.isEmpty(), "No SoH entry should be created from AT_CHARGER logs");
    }
}
