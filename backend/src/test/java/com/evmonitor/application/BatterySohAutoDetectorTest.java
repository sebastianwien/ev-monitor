package com.evmonitor.application;

import com.evmonitor.domain.DataSource;
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

class BatterySohAutoDetectorTest {

    private static final BigDecimal BATTERY_75 = new BigDecimal("75.00");

    private EvLog atVehicleLog(double kwh, int socBefore, int socAfter, LocalDateTime loggedAt) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal(String.valueOf(kwh)))
                .socBeforeChargePercent(socBefore)
                .socAfterChargePercent(socAfter)
                .loggedAt(loggedAt)
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .dataSource(DataSource.SMARTCAR_LIVE)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void detectsSohFromSingleQualifyingLog() {
        // 44.17 / (90-26) * 100 = 69.0156 kWh → SoH = 69.0156 / 75 * 100 = 92.02%
        EvLog log = atVehicleLog(44.17, 26, 90, LocalDateTime.now());

        Optional<BigDecimal> soh = BatterySohAutoDetector.detectSohPercent(List.of(log), BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.02"), soh.get());
    }

    @Test
    void ignoresAtChargerLogs() {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("36.26"))
                .socBeforeChargePercent(20).socAfterChargePercent(87)
                .loggedAt(LocalDateTime.now())
                .measurementType(EnergyMeasurementType.AT_CHARGER)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        assertTrue(BatterySohAutoDetector.detectSohPercent(List.of(log), BATTERY_75).isEmpty());
    }

    @Test
    void ignoresLogsWithSmallSocDelta() {
        // delta = 5% → below threshold of 20%
        EvLog log = atVehicleLog(3.06, 91, 96, LocalDateTime.now());

        assertTrue(BatterySohAutoDetector.detectSohPercent(List.of(log), BATTERY_75).isEmpty());
    }

    @Test
    void ignoresLogsWithMissingSocBefore() {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("44.00"))
                .socAfterChargePercent(90)
                .loggedAt(LocalDateTime.now())
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .dataSource(DataSource.SMARTCAR_LIVE)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        assertTrue(BatterySohAutoDetector.detectSohPercent(List.of(log), BATTERY_75).isEmpty());
    }

    @Test
    void returnsEmptyForNullBatteryCapacity() {
        EvLog log = atVehicleLog(44.17, 26, 90, LocalDateTime.now());

        assertTrue(BatterySohAutoDetector.detectSohPercent(List.of(log), null).isEmpty());
    }

    @Test
    void returnsEmptyForNoLogs() {
        assertTrue(BatterySohAutoDetector.detectSohPercent(List.of(), BATTERY_75).isEmpty());
    }

    @Test
    void takesMedianOfLast5QualifyingLogs() {
        // Capacities: 70, 68, 69, 71, 67 kWh → sorted: 67,68,69,70,71 → median(idx 2) = 69 kWh
        // SoH = 69 / 75 * 100 = 92.00%
        List<EvLog> logs = List.of(
                atVehicleLog(44.80, 26, 90, LocalDateTime.now().minusDays(5)), // 70.00 kWh
                atVehicleLog(43.52, 26, 90, LocalDateTime.now().minusDays(4)), // 68.00 kWh
                atVehicleLog(44.16, 26, 90, LocalDateTime.now().minusDays(3)), // 69.00 kWh
                atVehicleLog(45.44, 26, 90, LocalDateTime.now().minusDays(2)), // 71.00 kWh
                atVehicleLog(42.88, 26, 90, LocalDateTime.now().minusDays(1))  // 67.00 kWh
        );

        Optional<BigDecimal> soh = BatterySohAutoDetector.detectSohPercent(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.00"), soh.get());
    }

    @Test
    void usesRollingWindowOfLast5WhenMoreAvailable() {
        // Oldest 2 logs: "fresh" battery (~75 kWh) — must be excluded from window
        // Last 5: 70, 68, 69, 71, 67 kWh → median 69 → SoH 92.00%
        List<EvLog> logs = new ArrayList<>();
        logs.add(atVehicleLog(48.00, 26, 90, LocalDateTime.now().minusDays(10))); // 75.00 kWh - outside window
        logs.add(atVehicleLog(48.00, 26, 90, LocalDateTime.now().minusDays(9)));  // 75.00 kWh - outside window
        logs.add(atVehicleLog(44.80, 26, 90, LocalDateTime.now().minusDays(5)));  // 70.00 kWh
        logs.add(atVehicleLog(43.52, 26, 90, LocalDateTime.now().minusDays(4)));  // 68.00 kWh
        logs.add(atVehicleLog(44.16, 26, 90, LocalDateTime.now().minusDays(3)));  // 69.00 kWh
        logs.add(atVehicleLog(45.44, 26, 90, LocalDateTime.now().minusDays(2)));  // 71.00 kWh
        logs.add(atVehicleLog(42.88, 26, 90, LocalDateTime.now().minusDays(1)));  // 67.00 kWh

        Optional<BigDecimal> soh = BatterySohAutoDetector.detectSohPercent(logs, BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("92.00"), soh.get());
    }

    @Test
    void capsAtHundredPercent() {
        // 49.00 / 64 * 100 = 76.5625 kWh → SoH = 102.08% → capped at 100.00%
        EvLog log = atVehicleLog(49.00, 26, 90, LocalDateTime.now());

        Optional<BigDecimal> soh = BatterySohAutoDetector.detectSohPercent(List.of(log), BATTERY_75);

        assertTrue(soh.isPresent());
        assertEquals(new BigDecimal("100.00"), soh.get());
    }
}
