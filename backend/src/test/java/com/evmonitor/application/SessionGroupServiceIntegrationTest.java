package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstests für SessionGroupService.
 *
 * Prüft:
 * - Neue WALLBOX_GOE Session → Neue Gruppe wird erstellt
 * - Zweite Session innerhalb Merge-Fenster → Wird der bestehenden Gruppe hinzugefügt
 * - Session außerhalb Merge-Fenster → Neue Gruppe wird erstellt
 * - Session an einem anderen Tag → Neue Gruppe (kein Day-Crossing)
 * - Nicht-WALLBOX_GOE Sessions → werden NICHT gruppiert
 * - getSubSessions → gibt alle Sub-Sessions einer Gruppe zurück
 */
class SessionGroupServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SessionGroupService sessionGroupService;

    private User testUser;
    private Car testCar;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("session-group-" + System.nanoTime() + "@test.com");
        testCar = createAndSaveCar(testUser.getId(), CarBrand.CarModel.MODEL_3);
    }

    // ── Neue Gruppe erstellen ──────────────────────────────────────────────────

    @Test
    void processSessionForGrouping_firstGoeSession_createsNewGroup() {
        EvLog log = createAndSaveGoeLog(testCar.getId(), "5.0", LocalDateTime.now().minusHours(3));

        sessionGroupService.processSessionForGrouping(log);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(1, groups.size());
        assertEquals(new BigDecimal("5.00"), groups.get(0).totalKwhCharged());
        assertEquals(1, groups.get(0).sessionCount());
        assertEquals("WALLBOX_GOE", groups.get(0).dataSource());
    }

    // ── Zwei Sessions zusammenführen ──────────────────────────────────────────

    @Test
    void processSessionForGrouping_secondSessionWithinMergeGap_extendsExistingGroup() {
        LocalDateTime session1Start = LocalDateTime.now().minusHours(5);
        LocalDateTime session2Start = session1Start.plusMinutes(30); // 30 min Gap → innerhalb Default 90 min

        EvLog log1 = createAndSaveGoeLog(testCar.getId(), "3.0", session1Start);
        EvLog log2 = createAndSaveGoeLog(testCar.getId(), "4.0", session2Start);

        sessionGroupService.processSessionForGrouping(log1);
        sessionGroupService.processSessionForGrouping(log2);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(1, groups.size(), "Beide Sessions sollen in einer Gruppe sein");

        SessionGroupResponse group = groups.get(0);
        assertEquals(new BigDecimal("7.00"), group.totalKwhCharged());
        assertEquals(2, group.sessionCount());
    }

    // ── Gap zu groß → neue Gruppe ─────────────────────────────────────────────

    @Test
    void processSessionForGrouping_sessionOutsideMergeGap_createsNewGroup() {
        LocalDateTime session1Start = LocalDateTime.now().minusHours(5);
        // Session 1 dauert 5 min (session1End = session1Start + 5min)
        // Gap: 91 min → session2Start = session1End + 91min = session1Start + 96min
        // 96 min Gap zwischen Start der Sessions ist > DEFAULT_MERGE_GAP_MINUTES (90)
        LocalDateTime session2Start = session1Start.plusMinutes(96); // session1End(5min) + 91min gap

        EvLog log1 = createAndSaveGoeLogWithDuration(testCar.getId(), "3.0", session1Start, 5);
        EvLog log2 = createAndSaveGoeLogWithDuration(testCar.getId(), "4.0", session2Start, 5);

        sessionGroupService.processSessionForGrouping(log1);
        sessionGroupService.processSessionForGrouping(log2);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(2, groups.size(), "Sessions mit >90 min Gap sollen separate Gruppen bilden");
    }

    // ── Anderer Tag → neue Gruppe ─────────────────────────────────────────────

    @Test
    void processSessionForGrouping_sessionNextDay_createsNewGroup() {
        LocalDateTime todaySession = LocalDateTime.now().withHour(23).withMinute(0);
        LocalDateTime tomorrowSession = todaySession.plusDays(1).withHour(0).withMinute(30);

        EvLog log1 = createAndSaveGoeLog(testCar.getId(), "5.0", todaySession);
        EvLog log2 = createAndSaveGoeLog(testCar.getId(), "5.0", tomorrowSession);

        sessionGroupService.processSessionForGrouping(log1);
        sessionGroupService.processSessionForGrouping(log2);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(2, groups.size(), "Sessions an verschiedenen Tagen sollen separate Gruppen bilden");
    }

    // ── Nur WALLBOX_GOE wird gruppiert ────────────────────────────────────────

    @Test
    void processSessionForGrouping_nonGoeSession_isNotGrouped() {
        EvLog ocppLog = createAndSaveInternalLog(testCar.getId(), "10.0",
                LocalDateTime.now().minusHours(1), DataSource.WALLBOX_OCPP);

        sessionGroupService.processSessionForGrouping(ocppLog);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertTrue(groups.isEmpty(), "WALLBOX_OCPP Sessions sollen nicht gruppiert werden");
    }

    @Test
    void processSessionForGrouping_teslaSession_isNotGrouped() {
        EvLog teslaLog = createAndSaveInternalLog(testCar.getId(), "20.0",
                LocalDateTime.now().minusHours(1), DataSource.TESLA_FLEET_IMPORT);

        sessionGroupService.processSessionForGrouping(teslaLog);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertTrue(groups.isEmpty(), "Tesla Sessions sollen nicht gruppiert werden");
    }

    // ── Sub-Sessions abrufen ──────────────────────────────────────────────────

    @Test
    void getSubSessions_returnsAllSubSessionsForGroup() {
        LocalDateTime base = LocalDateTime.now().minusHours(6);

        EvLog log1 = createAndSaveGoeLog(testCar.getId(), "2.0", base);
        EvLog log2 = createAndSaveGoeLog(testCar.getId(), "3.0", base.plusMinutes(20));
        EvLog log3 = createAndSaveGoeLog(testCar.getId(), "2.5", base.plusMinutes(45));

        sessionGroupService.processSessionForGrouping(log1);
        sessionGroupService.processSessionForGrouping(log2);
        sessionGroupService.processSessionForGrouping(log3);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).sessionCount());

        List<EvLogResponse> subSessions = sessionGroupService.getSubSessions(groups.get(0).id());
        assertEquals(3, subSessions.size());
    }

    // ── Konfigurierbares Merge-Fenster ────────────────────────────────────────

    @Test
    void processSessionForGrouping_customMergeGap_respectsConfiguredThreshold() {
        LocalDateTime base = LocalDateTime.now().minusHours(4);
        // Session 1 dauert 5 min (session1End = base + 5min)
        // Gap: 20 min → session2Start = session1End + 20min = base + 25min
        // Bei Default (90 min) → zusammengeführt; bei 15 min Fenster → getrennt
        LocalDateTime session2 = base.plusMinutes(25); // session1End(5min) + 20min gap

        EvLog log1 = createAndSaveGoeLogWithDuration(testCar.getId(), "5.0", base, 5);
        EvLog log2 = createAndSaveGoeLogWithDuration(testCar.getId(), "5.0", session2, 5);

        // Merge-Fenster auf 15 min setzen → 20 min Gap ist zu groß → separate Gruppen
        sessionGroupService.processSessionForGrouping(log1, 15);
        sessionGroupService.processSessionForGrouping(log2, 15);

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(2, groups.size(), "Bei 15 min Merge-Fenster und 30 min Gap: 2 Gruppen erwartet");
    }

    // ── Gruppen-Aggregation ───────────────────────────────────────────────────

    @Test
    void processSessionForGrouping_multipleSubSessions_aggregatesKwhAndCost() {
        LocalDateTime base = LocalDateTime.now().minusHours(8);

        // 3 Sessions à 2 kWh und 0.80 EUR
        for (int i = 0; i < 3; i++) {
            EvLog log = EvLog.createFromInternal(
                    testCar.getId(),
                    new BigDecimal("2.0"),
                    30,
                    null,
                    base.plusMinutes(i * 20L),
                    null, null,
                    DataSource.WALLBOX_GOE,
                    new BigDecimal("0.80"),
                    ChargingType.AC);
            EvLog saved = evLogRepository.save(log);
            sessionGroupService.processSessionForGrouping(saved);
        }

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(1, groups.size());

        SessionGroupResponse group = groups.get(0);
        assertEquals(new BigDecimal("6.00"), group.totalKwhCharged());
        assertEquals(new BigDecimal("2.40"), group.costEur());
        assertEquals(3, group.sessionCount());
    }

    // ── groupByOdometer ───────────────────────────────────────────────────────

    @Test
    void groupByOdometer_twoLogsAtSameOdometer_createsGroup() {
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        EvLog log1 = createAndSaveSpritMonitorLog(testCar.getId(), "11.02", 113487, base);
        EvLog log2 = createAndSaveSpritMonitorLog(testCar.getId(), "40.00", 113487, base.plusSeconds(1));

        sessionGroupService.groupByOdometer(List.of(log1, log2));

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(1, groups.size());
        assertEquals(0, new BigDecimal("51.02").compareTo(groups.get(0).totalKwhCharged()));
        assertEquals(2, groups.get(0).sessionCount());
        assertEquals("SPRITMONITOR_IMPORT", groups.get(0).dataSource());

        // Sub-Sessions aus Statistiken ausgeschlossen
        List<EvLog> allLogs = evLogRepository.findAllByCarId(testCar.getId());
        assertTrue(allLogs.stream().allMatch(l -> !l.isIncludeInStatistics()));
    }

    @Test
    void groupByOdometer_threeLogsAtSameOdometer_createsGroupWithThree() {
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        EvLog log1 = createAndSaveSpritMonitorLog(testCar.getId(), "10.00", 50000, base);
        EvLog log2 = createAndSaveSpritMonitorLog(testCar.getId(), "20.00", 50000, base.plusSeconds(1));
        EvLog log3 = createAndSaveSpritMonitorLog(testCar.getId(), "15.00", 50000, base.plusSeconds(2));

        sessionGroupService.groupByOdometer(List.of(log1, log2, log3));

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(1, groups.size());
        assertEquals(0, new BigDecimal("45.00").compareTo(groups.get(0).totalKwhCharged()));
        assertEquals(3, groups.get(0).sessionCount());
    }

    @Test
    void groupByOdometer_mixedBatch_groupsOnlySameOdometer() {
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        // Zwei am gleichen Stopp (gleicher Odo) + eine separate Fahrt davor
        EvLog stopA1 = createAndSaveSpritMonitorLog(testCar.getId(), "30.00", 50000, base);
        EvLog stopA2 = createAndSaveSpritMonitorLog(testCar.getId(), "20.00", 50000, base.plusSeconds(1));
        EvLog stopB  = createAndSaveSpritMonitorLog(testCar.getId(), "25.00", 49700, base.minusHours(3));

        sessionGroupService.groupByOdometer(List.of(stopB, stopA1, stopA2));

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertEquals(1, groups.size(), "Nur die beiden Logs mit Odo 50000 sollen gruppiert werden");
        assertEquals(2, groups.get(0).sessionCount());

        // stopB bleibt standalone und in Statistiken
        List<EvLog> allLogs = evLogRepository.findAllByCarId(testCar.getId());
        EvLog standalone = allLogs.stream()
                .filter(l -> l.getSessionGroupId() == null)
                .findFirst().orElseThrow();
        assertEquals(49700, standalone.getOdometerKm());
        assertTrue(standalone.isIncludeInStatistics());
    }

    @Test
    void groupByOdometer_nullOdometer_notGrouped() {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        EvLog log1 = createAndSaveSpritMonitorLog(testCar.getId(), "20.00", null, base);
        EvLog log2 = createAndSaveSpritMonitorLog(testCar.getId(), "30.00", null, base.plusSeconds(1));

        sessionGroupService.groupByOdometer(List.of(log1, log2));

        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(testCar.getId());
        assertTrue(groups.isEmpty(), "Logs ohne Odometer dürfen nicht gruppiert werden");

        List<EvLog> allLogs = evLogRepository.findAllByCarId(testCar.getId());
        assertTrue(allLogs.stream().allMatch(l -> l.getSessionGroupId() == null));
        assertTrue(allLogs.stream().allMatch(EvLog::isIncludeInStatistics));
    }

    @Test
    void groupByOdometer_emptyList_noOp() {
        sessionGroupService.groupByOdometer(List.of());
        assertTrue(sessionGroupService.findAllByCarId(testCar.getId()).isEmpty());
    }

    @Test
    void groupByOdometer_singleLogPerOdometer_notGrouped() {
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        EvLog log1 = createAndSaveSpritMonitorLog(testCar.getId(), "30.00", 50000, base);
        EvLog log2 = createAndSaveSpritMonitorLog(testCar.getId(), "25.00", 50300, base.plusSeconds(1));

        sessionGroupService.groupByOdometer(List.of(log1, log2));

        assertTrue(sessionGroupService.findAllByCarId(testCar.getId()).isEmpty(),
                "Einzel-Logs pro Odometer-Wert dürfen keine Gruppe erzeugen");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EvLog createAndSaveGoeLog(java.util.UUID carId, String kwhCharged, LocalDateTime loggedAt) {
        return createAndSaveGoeLogWithDuration(carId, kwhCharged, loggedAt, 60);
    }

    private EvLog createAndSaveGoeLogWithDuration(java.util.UUID carId, String kwhCharged,
            LocalDateTime loggedAt, int durationMinutes) {
        EvLog log = EvLog.createFromInternal(
                carId,
                new BigDecimal(kwhCharged),
                durationMinutes,
                null,
                loggedAt,
                null, null,
                DataSource.WALLBOX_GOE,
                null,
                ChargingType.AC);
        return evLogRepository.save(log);
    }

    private EvLog createAndSaveInternalLog(java.util.UUID carId, String kwhCharged,
            LocalDateTime loggedAt, DataSource source) {
        EvLog log = EvLog.createFromInternal(
                carId,
                new BigDecimal(kwhCharged),
                60,
                null,
                loggedAt,
                null, null,
                source,
                null,
                ChargingType.AC);
        return evLogRepository.save(log);
    }

    private EvLog createAndSaveSpritMonitorLog(java.util.UUID carId, String kwhCharged,
            Integer odometerKm, LocalDateTime loggedAt) {
        EvLog log = EvLog.createNewWithSource(
                carId,
                new BigDecimal(kwhCharged),
                BigDecimal.ZERO,
                0,
                null,
                odometerKm,
                null, null,
                loggedAt,
                DataSource.SPRITMONITOR_IMPORT,
                ChargingType.UNKNOWN,
                null);
        return evLogRepository.save(log);
    }
}
