package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractServiceTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for odometer-based distance calculation in EvLogStatisticsService.
 *
 * Strategy: find oldest + newest odometer-bearing datapoint inside the period,
 * interpolate the boundary gaps using the adjacent datapoints outside the period.
 *
 * Sources: ev_log (odometerKm) and ev_trip (odometerEndKm) are treated equally.
 */
@Transactional
class EvLogStatisticsDistanceTest extends AbstractServiceTest {

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    @Autowired
    private EvTripRepository evTripRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("dist-test@ev-monitor.net");
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    // --- helpers ---

    private EvLog saveLog(int odometerKm, LocalDate date) {
        EvLog log = EvLog.createNew(car.getId(), new BigDecimal("30"), null,
                60, null, null, null, null,
                date.atStartOfDay(), null, null, null, false, null);
        log = log.toBuilder().odometerKm(odometerKm).includeInStatistics(true).build();
        return evLogRepository.save(log);
    }

    private void saveTrip(int odometerEndKm, LocalDate startDate) {
        evTripRepository.saveAndFlush(EvTrip.builder()
                .userId(user.getId())
                .carId(car.getId())
                .dataSource("TESLA_LIVE")
                .tripStartedAt(startDate.atStartOfDay().atOffset(ZoneOffset.UTC))
                .tripEndedAt(startDate.atStartOfDay().plusHours(1).atOffset(ZoneOffset.UTC))
                .odometerEndKm(new BigDecimal(odometerEndKm))
                .distanceKm(new BigDecimal("10"))
                .status("COMPLETED")
                .userCreated(false)
                .build());
    }

    private BigDecimal getDistanceKm(LocalDate from, LocalDate to) {
        entityManager.flush();
        entityManager.clear();
        return evLogStatisticsService
                .getStatistics(car.getId(), user.getId(), from, to, "MONTH")
                .totalDistanceKm();
    }

    // --- tests ---

    @Test
    void fullPeriodCoveredByLogs_returnsDelta() {
        // April: odo 62000, Mai: odo 62100 + 62300, Juni: odo 62400
        saveLog(62000, LocalDate.of(2026, 4, 28));
        saveLog(62100, LocalDate.of(2026, 5, 2));
        saveLog(62300, LocalDate.of(2026, 5, 28));
        saveLog(62400, LocalDate.of(2026, 6, 3));

        BigDecimal dist = getDistanceKm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // Kern: 62300 - 62100 = 200 km
        // Untere Interpolation: 100 km in 4 Tagen (28.4 00:00 bis 2.5 00:00),
        //   Lücke = 1.5 00:00 bis 2.5 00:00 = 1 Tag → 100/4 * 1 = 25 km
        // Obere Interpolation: 100 km in 6 Tagen (28.5 00:00 bis 3.6 00:00),
        //   Lücke = 28.5 00:00 bis 1.6 00:00 (periodEnd = 1.6 00:00) = 4 Tage → 100/6 * 4 ≈ 66.7 km
        // Total: ~291.7 km
        assertNotNull(dist);
        assertTrue(dist.compareTo(new BigDecimal("289")) >= 0 && dist.compareTo(new BigDecimal("294")) <= 0,
                "Expected ~291.7 km but was: " + dist);
    }

    @Test
    void noOdometerDataInPeriod_returnsNull() {
        // Keine Datenpunkte mit Odometer im Mai
        saveLog(62000, LocalDate.of(2026, 4, 28));
        saveLog(62400, LocalDate.of(2026, 6, 3));

        BigDecimal dist = getDistanceKm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertNull(dist);
    }

    @Test
    void onlyOneDatepointInPeriod_interpolatesBothBoundaries() {
        // Nur ein Datenpunkt im Mai
        saveLog(62000, LocalDate.of(2026, 4, 21));
        saveLog(62200, LocalDate.of(2026, 5, 11)); // 10 Tage nach Monatsstart
        saveLog(62400, LocalDate.of(2026, 6, 10)); // 10 Tage nach Monatsende

        BigDecimal dist = getDistanceKm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // Kern: 0 (nur 1 Punkt)
        // Untere Interpolation: 200 km in 20 Tagen (21.4 - 11.5), anteil 10 Tage → 100 km
        // Obere Interpolation: 200 km in 30 Tagen (11.5 - 10.6), anteil 20 Tage → ~133.3 km
        // Total: ~233 km
        assertNotNull(dist);
        assertTrue(dist.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void noPredecessor_noLowerInterpolation() {
        // Kein Datenpunkt vor Mai
        saveLog(62100, LocalDate.of(2026, 5, 10));
        saveLog(62300, LocalDate.of(2026, 5, 25));
        saveLog(62400, LocalDate.of(2026, 6, 5));

        BigDecimal dist = getDistanceKm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // Kern: 200 km, keine untere Interpolation, obere: 100/11 * 6 ≈ 54.5 km
        assertNotNull(dist);
        assertTrue(dist.compareTo(new BigDecimal("200")) > 0);
    }

    @Test
    void noSuccessor_noUpperInterpolation() {
        // Kein Datenpunkt nach Mai
        saveLog(62000, LocalDate.of(2026, 4, 28));
        saveLog(62100, LocalDate.of(2026, 5, 5));
        saveLog(62300, LocalDate.of(2026, 5, 25));

        BigDecimal dist = getDistanceKm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // Kern: 200 km + untere Interpolation, keine obere
        assertNotNull(dist);
        assertTrue(dist.compareTo(new BigDecimal("200")) > 0);
    }

    @Test
    void tripOdometerUsedAsDatapoint() {
        // Ev_log im April, ev_trip im Mai, ev_log im Juni
        saveLog(62000, LocalDate.of(2026, 4, 28));
        saveTrip(62200, LocalDate.of(2026, 5, 15)); // trip mit odometerEndKm
        saveLog(62400, LocalDate.of(2026, 6, 5));

        BigDecimal dist = getDistanceKm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // Trip wird als Datenpunkt erkannt, Interpolation beider Grenzen
        assertNotNull(dist);
        assertTrue(dist.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void mixedLogsAndTrips_kernFromOldestToNewest() {
        saveLog(62000, LocalDate.of(2026, 4, 28));
        saveLog(62100, LocalDate.of(2026, 5, 3));
        saveTrip(62250, LocalDate.of(2026, 5, 20));
        saveLog(62300, LocalDate.of(2026, 5, 27));
        saveLog(62450, LocalDate.of(2026, 6, 4));

        BigDecimal dist = getDistanceKm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // Kern: 62300 - 62100 = 200 km + beide Interpolationen
        assertNotNull(dist);
        assertTrue(dist.compareTo(new BigDecimal("200")) > 0);
    }
}
