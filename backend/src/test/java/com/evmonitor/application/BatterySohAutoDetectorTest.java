package com.evmonitor.application;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EnergySource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EnergyMeasurementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixtures use a SoC hub of 80% (10 -> 90) unless a test targets the threshold or
 * the hub weighting itself. With MIN_SOC_DELTA_PERCENT = 75 the estimated capacity
 * is kwh * 100 / 80, i.e. kwh = 0.8 * capacity.
 */
class BatterySohAutoDetectorTest {

    private static final BigDecimal BATTERY_75 = new BigDecimal("75.00");

    private EvLog atVehicleLog(double kwh, int socBefore, int socAfter, LocalDateTime loggedAt) {
        return atVehicleLog(kwh, socBefore, socAfter, loggedAt, null);
    }

    private EvLog atVehicleLog(double kwh, int socBefore, int socAfter, LocalDateTime loggedAt,
            EnergySource energySource) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal(String.valueOf(kwh)))
                .socBeforeChargePercent(new BigDecimal(socBefore))
                .socAfterChargePercent(new BigDecimal(socAfter))
                .loggedAt(loggedAt)
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .dataSource(DataSource.SMARTCAR_LIVE)
                .includeInStatistics(true)
                .energySource(energySource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /** Most tests only care about the resulting percentage, not the sample size. */
    private Optional<BigDecimal> detectSoh(List<EvLog> logs, BigDecimal batteryCapacityKwh) {
        return BatterySohAutoDetector.detect(logs, batteryCapacityKwh)
                .map(BatterySohAutoDetector.Detection::sohPercent);
    }

    @Test
    void detectsSohFromSingleQualifyingLog() {
        // 55.212 / 80 * 100 = 69.015 kWh -> SoH = 69.015 / 75 * 100 = 92.02%
        EvLog log = atVehicleLog(55.212, 10, 90, LocalDateTime.now());

        Optional<BigDecimal> soh = detectSoh(List.of(log), BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.02"), soh.get());
    }

    @Test
    void ignoresAtChargerLogs() {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("55.212"))
                .socBeforeChargePercent(new BigDecimal("10")).socAfterChargePercent(new BigDecimal("90"))
                .loggedAt(LocalDateTime.now())
                .measurementType(EnergyMeasurementType.AT_CHARGER)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        assertTrue(detectSoh(List.of(log), BATTERY_75).isEmpty());
    }

    // --- SoC hub threshold --------------------------------------------------

    @Test
    void ignoresLogsWithSmallSocDelta() {
        EvLog log = atVehicleLog(3.06, 91, 96, LocalDateTime.now());

        assertTrue(detectSoh(List.of(log), BATTERY_75).isEmpty());
    }

    @Test
    void ignoresLogJustBelowThreshold() {
        // hub = 74% -> rejected. The boundary is a judgement call about how much of the
        // pack a charge has to cover before extrapolating to full capacity is defensible.
        EvLog log = atVehicleLog(51.06, 16, 90, LocalDateTime.now());

        assertTrue(detectSoh(List.of(log), BATTERY_75).isEmpty(),
                "A 74% SoC hub must not qualify");
    }

    @Test
    void acceptsLogExactlyAtThreshold() {
        // hub = 75% -> qualifies. 51.7613 / 75 * 100 = 69.0150 kWh -> 92.02%
        EvLog log = atVehicleLog(51.76125, 15, 90, LocalDateTime.now());

        Optional<BigDecimal> soh = detectSoh(List.of(log), BATTERY_75);

        assertTrue(soh.isPresent(), "A 75% SoC hub must qualify (boundary is inclusive)");
        assertEquals(new BigDecimal("92.02"), soh.get());
    }

    @Test
    void ignoresLogsWithMissingSocBefore() {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("55.212"))
                .socAfterChargePercent(new BigDecimal("90"))
                .loggedAt(LocalDateTime.now())
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .dataSource(DataSource.SMARTCAR_LIVE)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        assertTrue(detectSoh(List.of(log), BATTERY_75).isEmpty());
    }

    @Test
    void returnsEmptyForNullBatteryCapacity() {
        EvLog log = atVehicleLog(55.212, 10, 90, LocalDateTime.now());

        assertTrue(detectSoh(List.of(log), null).isEmpty());
    }

    @Test
    void returnsEmptyForNoLogs() {
        assertTrue(detectSoh(List.of(), BATTERY_75).isEmpty());
    }

    // --- median window ------------------------------------------------------

    @Test
    void takesMedianOfLast5QualifyingLogs() {
        // Equal hubs (80%) -> weights cancel out, classic median applies.
        // Capacities: 70, 68, 69, 71, 67 kWh -> sorted: 67,68,69,70,71 -> median = 69 kWh
        // SoH = 69 / 75 * 100 = 92.00%
        List<EvLog> logs = List.of(
                atVehicleLog(56.00, 10, 90, LocalDateTime.now().minusDays(5)), // 70.00 kWh
                atVehicleLog(54.40, 10, 90, LocalDateTime.now().minusDays(4)), // 68.00 kWh
                atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(3)), // 69.00 kWh
                atVehicleLog(56.80, 10, 90, LocalDateTime.now().minusDays(2)), // 71.00 kWh
                atVehicleLog(53.60, 10, 90, LocalDateTime.now().minusDays(1))  // 67.00 kWh
        );

        Optional<BigDecimal> soh = detectSoh(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.00"), soh.get());
    }

    @Test
    void usesRollingWindowOfLast5WhenMoreAvailable() {
        // Oldest 2 logs: "fresh" battery (75 kWh) - must be excluded from the window
        List<EvLog> logs = new ArrayList<>();
        logs.add(atVehicleLog(60.00, 10, 90, LocalDateTime.now().minusDays(10))); // 75.00 kWh - outside
        logs.add(atVehicleLog(60.00, 10, 90, LocalDateTime.now().minusDays(9)));  // 75.00 kWh - outside
        logs.add(atVehicleLog(56.00, 10, 90, LocalDateTime.now().minusDays(5)));  // 70.00 kWh
        logs.add(atVehicleLog(54.40, 10, 90, LocalDateTime.now().minusDays(4)));  // 68.00 kWh
        logs.add(atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(3)));  // 69.00 kWh
        logs.add(atVehicleLog(56.80, 10, 90, LocalDateTime.now().minusDays(2)));  // 71.00 kWh
        logs.add(atVehicleLog(53.60, 10, 90, LocalDateTime.now().minusDays(1)));  // 67.00 kWh

        Optional<BigDecimal> soh = detectSoh(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.00"), soh.get());
    }

    // --- hub-weighted median ------------------------------------------------

    @Test
    void weightsEstimatesBySocHub_largerHubWins() {
        // 60 kWh from a 75% hub (weight 75), 70 kWh from a 100% hub (weight 100).
        // Cumulative weight crosses half of 175 only at the 70 kWh estimate.
        // Unweighted this would land on 60 kWh (lower middle of two values).
        List<EvLog> logs = List.of(
                atVehicleLog(45.00, 15, 90, LocalDateTime.now().minusDays(2)),  // 60 kWh, hub 75
                atVehicleLog(70.00, 0, 100, LocalDateTime.now().minusDays(1))   // 70 kWh, hub 100
        );

        Optional<BigDecimal> soh = detectSoh(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("93.33"), soh.get(),
                "The estimate backed by the larger SoC hub must carry the median");
    }

    @Test
    void weightsEstimatesBySocHub_symmetricCase() {
        // Same capacities as above, hubs swapped -> the 60 kWh estimate now carries
        // the larger hub and must win. Proves the weighting drives the result, not
        // the magnitude of the capacity.
        List<EvLog> logs = List.of(
                atVehicleLog(60.00, 0, 100, LocalDateTime.now().minusDays(2)),  // 60 kWh, hub 100
                atVehicleLog(52.50, 15, 90, LocalDateTime.now().minusDays(1))   // 70 kWh, hub 75
        );

        Optional<BigDecimal> soh = detectSoh(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("80.00"), soh.get(),
                "Weighting must follow the SoC hub, not the capacity value");
    }

    @Test
    void weightedMedianStaysRobustAgainstASingleOutlier() {
        // One wildly wrong estimate (100 kWh) with the largest hub must not take over:
        // a weighted median picks a value, it does not average toward the outlier.
        List<EvLog> logs = List.of(
                atVehicleLog(52.50, 15, 90, LocalDateTime.now().minusDays(3)),  // 70 kWh, hub 75
                atVehicleLog(53.25, 15, 90, LocalDateTime.now().minusDays(2)),  // 71 kWh, hub 75
                atVehicleLog(100.00, 0, 100, LocalDateTime.now().minusDays(1))  // 100 kWh, hub 100
        );

        Optional<BigDecimal> soh = detectSoh(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("94.67"), soh.get(),
                "Outlier must not pull the result beyond the middle estimate (71 kWh)");
    }

    // --- sample size (drives the confidence badge in the UI) ----------------

    @Test
    void reportsSampleSizeCappedAtWindowSize() {
        List<EvLog> logs = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            logs.add(atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(i)));
        }

        Optional<BatterySohAutoDetector.Detection> detection =
                BatterySohAutoDetector.detect(logs, BATTERY_75);

        assertTrue(detection.isPresent());
        assertEquals(5, detection.get().sampleSize(),
                "7 qualifying logs must report the capped window size, not 7");
    }

    @Test
    void reportsActualSampleSizeBelowWindowSize() {
        List<EvLog> logs = List.of(
                atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(2)),
                atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(1)));

        Optional<BatterySohAutoDetector.Detection> detection =
                BatterySohAutoDetector.detect(logs, BATTERY_75);

        assertTrue(detection.isPresent());
        assertEquals(2, detection.get().sampleSize());
    }

    @Test
    void sampleSizeCountsOnlyQualifyingLogs() {
        // 2 qualifying (80% hub) + 3 rejected (50% hub) -> sample size 2
        List<EvLog> logs = List.of(
                atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(5)),
                atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(4)),
                atVehicleLog(34.50, 20, 70, LocalDateTime.now().minusDays(3)),
                atVehicleLog(34.50, 20, 70, LocalDateTime.now().minusDays(2)),
                atVehicleLog(34.50, 20, 70, LocalDateTime.now().minusDays(1)));

        Optional<BatterySohAutoDetector.Detection> detection =
                BatterySohAutoDetector.detect(logs, BATTERY_75);

        assertTrue(detection.isPresent());
        assertEquals(2, detection.get().sampleSize(),
                "Logs below the SoC hub threshold must not count toward the sample size");
    }

    // --- reported SoC hub ---------------------------------------------------

    @Test
    void reportsSocHubOfTheEstimateThatCarriedTheMedian() {
        // The 70 kWh estimate (hub 100) wins the weighted median, so 100 is the hub the
        // displayed value actually rests on - not the 75 of the other charge.
        List<EvLog> logs = List.of(
                atVehicleLog(45.00, 15, 90, LocalDateTime.now().minusDays(2)),  // 60 kWh, hub 75
                atVehicleLog(70.00, 0, 100, LocalDateTime.now().minusDays(1))   // 70 kWh, hub 100
        );

        Optional<BatterySohAutoDetector.Detection> detection =
                BatterySohAutoDetector.detect(logs, BATTERY_75);

        assertTrue(detection.isPresent());
        assertEquals(0, new BigDecimal("100").compareTo(detection.get().socHubPercent()));
    }

    @Test
    void reportsSocHubForASingleCharge() {
        EvLog log = atVehicleLog(55.212, 10, 90, LocalDateTime.now());

        Optional<BatterySohAutoDetector.Detection> detection =
                BatterySohAutoDetector.detect(List.of(log), BATTERY_75);

        assertTrue(detection.isPresent());
        assertEquals(0, new BigDecimal("80").compareTo(detection.get().socHubPercent()));
    }

    // --- kwhAtVehicle preference -------------------------------------------

    @Test
    void qualifiesWhenKwhAtVehicleIsSet_regardlessOfMeasurementType() {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("62.00"))       // charger-side kWh (irrelevant for SoH)
                .kwhAtVehicle(new BigDecimal("55.212"))    // user entered vehicle-side kWh
                .socBeforeChargePercent(new BigDecimal("10")).socAfterChargePercent(new BigDecimal("90"))
                .loggedAt(LocalDateTime.now())
                .measurementType(EnergyMeasurementType.AT_CHARGER)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        Optional<BigDecimal> soh = detectSoh(List.of(log), BATTERY_75);

        assertTrue(soh.isPresent(), "Log with kwhAtVehicle should qualify regardless of measurementType");
        assertEquals(new BigDecimal("92.02"), soh.get());
    }

    @Test
    void usesKwhAtVehicleInsteadOfKwhChargedWhenBothPresent() {
        // kwhAtVehicle = 55.212 -> SoH 92.02%
        // kwhCharged   = 62.00  -> would give SoH 103.33% (capped to 100%)
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("62.00"))
                .kwhAtVehicle(new BigDecimal("55.212"))
                .socBeforeChargePercent(new BigDecimal("10")).socAfterChargePercent(new BigDecimal("90"))
                .loggedAt(LocalDateTime.now())
                .measurementType(EnergyMeasurementType.AT_CHARGER)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        Optional<BigDecimal> soh = detectSoh(List.of(log), BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.02"), soh.get(), "Must use kwhAtVehicle, not kwhCharged");
    }

    @Test
    void ignoresLogsExcludedFromStatistics() {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("55.212"))
                .socBeforeChargePercent(new BigDecimal("10")).socAfterChargePercent(new BigDecimal("90"))
                .loggedAt(LocalDateTime.now())
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .dataSource(DataSource.SMARTCAR_LIVE)
                .includeInStatistics(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        assertTrue(detectSoh(List.of(log), BATTERY_75).isEmpty());
    }

    // --- energySource marker (V119) ----------------------------------------

    @Test
    void isQualifying_returnsFalse_whenEnergySourceIsSocInferred() {
        // SOC_INFERRED logs are the trigger of the SoH-AutoDetect loop:
        // kwh was computed from SoC-delta x effective_capacity, so feeding it back
        // into SoH detection produces tautological output and drift.
        EvLog log = atVehicleLog(55.212, 10, 90, LocalDateTime.now(), EnergySource.SOC_INFERRED);

        assertFalse(BatterySohAutoDetector.isQualifying(log),
                "SOC_INFERRED logs must be excluded from SoH detection");
    }

    @Test
    void isQualifying_returnsTrue_whenEnergySourceIsNull() {
        // Backwards-compat: legacy rows before V119 have NULL and must stay qualifying.
        EvLog log = atVehicleLog(55.212, 10, 90, LocalDateTime.now(), null);

        assertTrue(BatterySohAutoDetector.isQualifying(log),
                "NULL energySource must remain qualifying (backwards-compat)");
    }

    @Test
    void isQualifying_returnsTrue_whenEnergySourceIsOemMeasured() {
        assertTrue(BatterySohAutoDetector.isQualifying(
                atVehicleLog(55.212, 10, 90, LocalDateTime.now(), EnergySource.OEM_MEASURED)));
    }

    @Test
    void isQualifying_returnsTrue_whenEnergySourceIsUserInput() {
        assertTrue(BatterySohAutoDetector.isQualifying(
                atVehicleLog(55.212, 10, 90, LocalDateTime.now(), EnergySource.USER_INPUT)));
    }

    @Test
    void isQualifying_returnsTrue_whenEnergySourceIsWallbox() {
        assertTrue(BatterySohAutoDetector.isQualifying(
                atVehicleLog(55.212, 10, 90, LocalDateTime.now(), EnergySource.WALLBOX)));
    }

    @Test
    void detectSohPercent_excludesSocInferredLogs_fromMedianWindow() {
        // 5 SOC_INFERRED logs would each estimate a clean 75 kWh (SoH 100%) - if they
        // leak into the window the median jumps. They MUST be filtered out.
        List<EvLog> logs = new ArrayList<>();
        for (int i = 10; i >= 6; i--) {
            logs.add(atVehicleLog(60.00, 10, 90, LocalDateTime.now().minusDays(i),
                    EnergySource.SOC_INFERRED));
        }
        logs.add(atVehicleLog(56.00, 10, 90, LocalDateTime.now().minusDays(5),
                EnergySource.OEM_MEASURED)); // 70.00 kWh
        logs.add(atVehicleLog(54.40, 10, 90, LocalDateTime.now().minusDays(4),
                EnergySource.OEM_MEASURED)); // 68.00 kWh
        logs.add(atVehicleLog(55.20, 10, 90, LocalDateTime.now().minusDays(3),
                EnergySource.OEM_MEASURED)); // 69.00 kWh
        logs.add(atVehicleLog(56.80, 10, 90, LocalDateTime.now().minusDays(2),
                EnergySource.OEM_MEASURED)); // 71.00 kWh
        logs.add(atVehicleLog(53.60, 10, 90, LocalDateTime.now().minusDays(1),
                EnergySource.OEM_MEASURED)); // 67.00 kWh

        Optional<BigDecimal> soh = detectSoh(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.00"), soh.get(),
                "SOC_INFERRED logs must not contribute to the median window");
    }

    @Test
    void capsAtHundredPercent() {
        // 61.25 / 80 * 100 = 76.5625 kWh -> SoH = 102.08% -> capped at 100.00%
        EvLog log = atVehicleLog(61.25, 10, 90, LocalDateTime.now());

        Optional<BigDecimal> soh = detectSoh(List.of(log), BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("100.00"), soh.get());
    }
}
