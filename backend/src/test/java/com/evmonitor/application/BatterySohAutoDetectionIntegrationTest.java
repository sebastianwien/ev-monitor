package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Autowired
    private EvLogService evLogService;

    private EvLog smartcarLog(UUID carId, double kwh, int socBefore, int socAfter, int daysAgo) {
        return EvLog.createFromInternal(
                carId, new BigDecimal(String.valueOf(kwh)),
                60, null,
                LocalDateTime.now().minusDays(daysAgo),
                null, null,
                DataSource.SMARTCAR_LIVE, null, ChargingType.AC,
                60000 + daysAgo, new BigDecimal(socBefore), new BigDecimal(socAfter), null, null);
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

    // S2a: change ≤ 2% → skip (45.58125 kWh / 65% delta = 70.125 kWh capacity → 93.50% SoH → 1.5% from 92%)
    @Test
    void autoDetectAndPersist_skipsWhenChangeIsWithinTwoPercent() {
        User user = createAndSaveUser("soh-threshold-skip-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "TH-SK-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.addMeasurement(car.getId(), user.getId(),
                new BatterySohRequest(new BigDecimal("92.00"), LocalDate.now().minusDays(1)));

        evLogRepository.save(smartcarLog(car.getId(), 45.58125, 25, 90, 2));

        batterySohService.autoDetectAndPersist(car);

        assertEquals(1, batterySohService.getHistory(car.getId(), user.getId()).size(),
                "Change of 1.5% is within 2% threshold - no new entry expected");
    }

    // S2b: change > 2% → create (46.80 kWh / 65% delta = 72.00 kWh capacity → 96.00% SoH → 4% from 92%)
    @Test
    void autoDetectAndPersist_createsEntryWhenChangeExceedsTwoPercent() {
        User user = createAndSaveUser("soh-threshold-create-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "TH-CR-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.addMeasurement(car.getId(), user.getId(),
                new BatterySohRequest(new BigDecimal("92.00"), LocalDate.now().minusDays(1)));

        evLogRepository.save(smartcarLog(car.getId(), 46.80, 25, 90, 2));

        batterySohService.autoDetectAndPersist(car);

        assertEquals(2, batterySohService.getHistory(car.getId(), user.getId()).size(),
                "Change of 4% exceeds 2% threshold — new entry expected");
    }

    // S3: SoH-Erkennung wird über den realen Trigger-Pfad createWallboxLog ausgelöst
    @Test
    void createWallboxLog_triggersAutoDetect_createsSohEntry() {
        User user = createAndSaveUser("soh-wallbox-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "WB-SH-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // 44.17 kWh / 64% delta = 69.02 kWh capacity → SoH ≈ 92.02%
        evLogService.createWallboxLog(new InternalEvLogRequest(
                car.getId(), user.getId(),
                new BigDecimal("44.17"), 60,
                LocalDateTime.now().minusDays(1),
                null, null, null,
                "SMARTCAR_LIVE", null, "AC", false,
                60000, new BigDecimal("26"), new BigDecimal("90"), null, null));

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size(), "createWallboxLog should trigger SoH auto-detection");
        assertTrue(history.get(0).sohPercent().compareTo(new BigDecimal("90")) > 0,
                "Detected SoH should be above 90%");
    }

    // S4: kwhAtVehicle nachträglich via updateLog gesetzt → triggert SoH-Detection
    @Test
    void updateLog_triggersAutoDetect_whenKwhAtVehicleIsSet() {
        User user = createAndSaveUser("soh-update-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "UL-SH-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // Create a manual log without kwhAtVehicle (AT_CHARGER, no SoH detection yet)
        EvLog log = evLogRepository.save(EvLog.createNew(
                car.getId(), new BigDecimal("50.00"), null, 60, null, 61000,
                null, new BigDecimal("90"), LocalDateTime.now().minusDays(1),
                ChargingType.AC, null, null, false, null));

        assertTrue(batterySohService.getHistory(car.getId(), user.getId()).isEmpty(),
                "No SoH entry before update");

        // Add kwhAtVehicle and socBefore via update → should trigger SoH detection
        // 44.17 kWh / 64% delta = 69.02 kWh capacity → SoH ≈ 92.02%
        evLogService.updateLog(log.getId(), user.getId(),
                new EvLogUpdateRequest(null, null, null, null, null, null, null,
                        null, new BigDecimal("26"), new BigDecimal("44.17"),
                        null, null, null, null, null, null, null, null, null));

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size(), "updateLog with kwhAtVehicle should trigger SoH auto-detection");
        assertTrue(history.get(0).sohPercent().compareTo(new BigDecimal("90")) > 0,
                "Detected SoH should be above 90%");
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
                null, new BigDecimal("90"), LocalDateTime.now().minusDays(1),
                ChargingType.AC, null, null, false, null));

        batterySohService.autoDetectAndPersist(car);

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertTrue(history.isEmpty(), "No SoH entry should be created from AT_CHARGER logs");
    }

    // --- BMS-derived SoH ---

    @Test
    void persistBmsDerived_savesSohEntry_whenPlausible() {
        User user = createAndSaveUser("soh-bms-ok-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "BM-SO-K01", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // 69.375 / 75.0 * 100 = 92.50% SoH
        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("69.375"));

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size());
        assertEquals(0, new BigDecimal("92.50").compareTo(history.get(0).sohPercent()));
    }

    @Test
    void persistBmsDerived_rejects_whenOutOfRange() {
        User user = createAndSaveUser("soh-bms-oor-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "BM-OO-R01", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("40.00")); // 53.3% → below 60%

        assertTrue(batterySohService.getHistory(car.getId(), user.getId()).isEmpty());
    }

    @Test
    void persistBmsDerived_rejects_whenDeviatesMoreThan5PercentFromLastSoh() {
        User user = createAndSaveUser("soh-bms-dev-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "BM-DE-V01", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.addMeasurement(car.getId(), user.getId(),
                new BatterySohRequest(new BigDecimal("92.00"), LocalDate.now().minusDays(1)));

        // 98.5% SoH → 6.5% deviation from 92% → rejected
        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("73.875"));

        assertEquals(1, batterySohService.getHistory(car.getId(), user.getId()).size(),
                "No new entry - deviation too large");
    }

    @Test
    void persistBmsDerived_skips_whenAlreadyStoredThisMonth() {
        User user = createAndSaveUser("soh-bms-mth-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "BM-MT-H01", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("69.375")); // first - saved
        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("69.000")); // same month - skipped

        assertEquals(1, batterySohService.getHistory(car.getId(), user.getId()).size());
    }

    @Test
    void persistBmsDerived_accepts_whenNoExistingSoh() {
        User user = createAndSaveUser("soh-bms-new-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "BM-NE-W01", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // No existing SoH - no deviation check, accept directly
        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("69.375")); // 92.5%

        assertEquals(1, batterySohService.getHistory(car.getId(), user.getId()).size());
    }
}
