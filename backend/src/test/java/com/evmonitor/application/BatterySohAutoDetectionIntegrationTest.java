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

        // 55.212 / 80 * 100 = 69.015 kWh → SoH = 92.02%
        evLogRepository.save(smartcarLog(car.getId(), 55.212, 10, 90, 2));
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

        evLogRepository.save(smartcarLog(car.getId(), 55.212, 10, 90, 1));

        batterySohService.autoDetectAndPersist(car);
        batterySohService.autoDetectAndPersist(car); // second call same day

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size(), "Should not create duplicate entry for same day");
    }

    // S2a: change ≤ 2% → skip (56.10 kWh / 80% hub = 70.125 kWh capacity → 93.50% SoH → 1.5% from 92%)
    @Test
    void autoDetectAndPersist_skipsWhenChangeIsWithinTwoPercent() {
        User user = createAndSaveUser("soh-threshold-skip-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "TH-SK-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.addMeasurement(car.getId(), user.getId(),
                new BatterySohRequest(new BigDecimal("92.00"), LocalDate.now().minusDays(1)));

        evLogRepository.save(smartcarLog(car.getId(), 56.10, 10, 90, 2));

        batterySohService.autoDetectAndPersist(car);

        assertEquals(1, batterySohService.getHistory(car.getId(), user.getId()).size(),
                "Change of 1.5% is within 2% threshold - no new entry expected");
    }

    // S2b: change > 2% → create (57.60 kWh / 80% hub = 72.00 kWh capacity → 96.00% SoH → 4% from 92%)
    @Test
    void autoDetectAndPersist_createsEntryWhenChangeExceedsTwoPercent() {
        User user = createAndSaveUser("soh-threshold-create-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "TH-CR-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.addMeasurement(car.getId(), user.getId(),
                new BatterySohRequest(new BigDecimal("92.00"), LocalDate.now().minusDays(1)));

        evLogRepository.save(smartcarLog(car.getId(), 57.60, 10, 90, 2));

        batterySohService.autoDetectAndPersist(car);

        assertEquals(2, batterySohService.getHistory(car.getId(), user.getId()).size(),
                "Change of 4% exceeds 2% threshold — new entry expected");
    }

    // S3: SoH-Erkennung wird über den realen Trigger-Pfad createInternalLog ausgelöst
    @Test
    void createInternalLog_triggersAutoDetect_createsSohEntry() {
        User user = createAndSaveUser("soh-wallbox-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "WB-SH-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // 55.212 kWh / 80% hub = 69.015 kWh capacity → SoH ≈ 92.02%
        evLogService.createInternalLog(new InternalEvLogRequest(
                car.getId(), user.getId(),
                new BigDecimal("55.212"), 60,
                LocalDateTime.now().minusDays(1),
                null, null, null,
                "SMARTCAR_LIVE", null, "AC", false,
                60000, new BigDecimal("10"), new BigDecimal("90"), null, null, null, null, null, null,
                null, null));

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size(), "createInternalLog should trigger SoH auto-detection");
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
        // 55.212 kWh / 80% hub = 69.015 kWh capacity → SoH ≈ 92.02%
        evLogService.updateLog(log.getId(), user.getId(),
                new EvLogUpdateRequest(null, null, null, null, null, null, null,
                        null, new BigDecimal("10"), new BigDecimal("55.212"),
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

    @Test
    void persistBmsDerived_clampsSohTo100_whenBmsReportsSlightlyAbove() {
        User user = createAndSaveUser("soh-bms-clamp-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "BM-CL-P01", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        // 75.01 / 75.00 * 100 = 100.01% - passes plausibility guard (<= 105%) but violates DB constraint (<= 100%)
        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("75.01"));

        List<BatterySohResponse> history = batterySohService.getHistory(car.getId(), user.getId());
        assertEquals(1, history.size(), "Entry should be saved despite BMS reporting slightly above 100%");
        assertEquals(0, new BigDecimal("100.00").compareTo(history.get(0).sohPercent()),
                "SoH must be clamped to 100.00");
    }

    // --- provenance (V147) ---

    @Test
    void autoDetectedEntry_isMarkedAsChargeLog_withSampleSize() {
        User user = createAndSaveUser("soh-src-log-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "SR-CL-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        evLogRepository.save(smartcarLog(car.getId(), 55.212, 10, 90, 2));
        evLogRepository.save(smartcarLog(car.getId(), 55.212, 10, 90, 3));

        batterySohService.autoDetectAndPersist(car);

        BatterySohResponse entry = batterySohService.getHistory(car.getId(), user.getId()).get(0);
        assertEquals(BatterySohSource.CHARGE_LOG, entry.source());
        assertEquals(2, entry.sampleSize(), "Sample size must reflect the charges behind the estimate");
        assertEquals(0, new BigDecimal("80").compareTo(entry.socHubPercent()),
                "The SoC hub behind the estimate must be persisted");
    }

    @Test
    void manualEntry_isMarkedAsManual_withoutSampleSize() {
        User user = createAndSaveUser("soh-src-man-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "SR-MA-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.addMeasurement(car.getId(), user.getId(),
                new BatterySohRequest(new BigDecimal("92.00"), LocalDate.now()));

        BatterySohResponse entry = batterySohService.getHistory(car.getId(), user.getId()).get(0);
        assertEquals(BatterySohSource.MANUAL, entry.source());
        assertNull(entry.sampleSize());
        assertNull(entry.socHubPercent());
    }

    @Test
    void bmsEntry_isMarkedAsVehicleBms() {
        User user = createAndSaveUser("soh-src-bms-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "SR-BM-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        batterySohService.persistBmsDerived(car.getId(), new BigDecimal("69.375"));

        BatterySohResponse entry = batterySohService.getHistory(car.getId(), user.getId()).get(0);
        assertEquals(BatterySohSource.VEHICLE_BMS, entry.source());
        assertNull(entry.sampleSize(), "A single BMS reading has no hub and no sample window");
        assertNull(entry.socHubPercent());
    }

    @Test
    void editingAnAutoDetectedEntry_turnsItIntoAManualOne() {
        User user = createAndSaveUser("soh-src-edit-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "SR-ED-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        evLogRepository.save(smartcarLog(car.getId(), 55.212, 10, 90, 2));
        batterySohService.autoDetectAndPersist(car);
        BatterySohResponse auto = batterySohService.getHistory(car.getId(), user.getId()).get(0);
        assertEquals(BatterySohSource.CHARGE_LOG, auto.source());

        batterySohService.updateLatest(auto.id(), car.getId(), user.getId(),
                new BatterySohRequest(new BigDecimal("88.00"), LocalDate.now()));

        BatterySohResponse edited = batterySohService.getHistory(car.getId(), user.getId()).get(0);
        assertEquals(BatterySohSource.MANUAL, edited.source(),
                "A user-corrected value must not keep claiming to be an estimate");
        assertNull(edited.sampleSize());
        assertNull(edited.socHubPercent(), "The hub belonged to the estimate, not to the correction");
    }

    // --- detection status (drives the empty state in the UI) ---

    @Test
    void detectionStatus_reportsLargestHub_whenNoChargeQualifies() {
        User user = createAndSaveUser("soh-stat-no-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "ST-NO-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        evLogRepository.save(smartcarLog(car.getId(), 30.00, 20, 70, 2)); // hub 50
        evLogRepository.save(smartcarLog(car.getId(), 38.00, 25, 83, 3)); // hub 58

        BatterySohStatusResponse status = batterySohService.getDetectionStatus(car.getId(), user.getId());

        assertEquals(75, status.requiredSocHubPercent());
        assertEquals(0, new BigDecimal("58").compareTo(status.largestSocHubPercent()));
        assertEquals(0, status.qualifyingChargeCount());
        assertTrue(status.capacityKnown());
    }

    @Test
    void detectionStatus_reportsNullHub_whenCarHasNoUsableLog() {
        User user = createAndSaveUser("soh-stat-emp-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "ST-EM-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        BatterySohStatusResponse status = batterySohService.getDetectionStatus(car.getId(), user.getId());

        assertNull(status.largestSocHubPercent());
        assertEquals(0, status.qualifyingChargeCount());
    }

    @Test
    void detectionStatus_countsQualifyingCharges() {
        User user = createAndSaveUser("soh-stat-ok-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                user.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "ST-OK-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        evLogRepository.save(smartcarLog(car.getId(), 55.212, 10, 90, 2));
        evLogRepository.save(smartcarLog(car.getId(), 55.212, 10, 90, 3));
        evLogRepository.save(smartcarLog(car.getId(), 30.00, 20, 70, 4)); // hub 50, ignored

        BatterySohStatusResponse status = batterySohService.getDetectionStatus(car.getId(), user.getId());

        assertEquals(2, status.qualifyingChargeCount());
        assertEquals(0, new BigDecimal("80").compareTo(status.largestSocHubPercent()));
    }

    @Test
    void detectionStatus_rejectsForeignCar() {
        User owner = createAndSaveUser("soh-stat-own-" + System.currentTimeMillis() + "@test.com");
        User stranger = createAndSaveUser("soh-stat-str-" + System.currentTimeMillis() + "@test.com");
        Car car = carRepository.save(Car.createNew(
                owner.getId(), CarBrand.CarModel.MODEL_3, 2019,
                "ST-FO-001", "LR", new BigDecimal("75.00"), new BigDecimal("280.0"), null));

        assertThrows(IllegalArgumentException.class,
                () -> batterySohService.getDetectionStatus(car.getId(), stranger.getId()),
                "Detection status must not leak across users");
    }
}
