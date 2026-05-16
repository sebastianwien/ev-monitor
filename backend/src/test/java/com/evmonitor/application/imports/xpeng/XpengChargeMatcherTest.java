package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.xpeng.DetectedChargingSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Match-Strategie (siehe XpengChargeMatcher Javadoc):
 *  - Odometer ±1 km
 *  - ev_log.logged_at liegt im Tagesbereich der Session
 *    (BETWEEN session.startedAt - 1d AND session.endedAt + 1d)
 *  - n XPeng-Sessions am selben Odo werden auf 1 ev_log aggregiert (Summe kwh_at_vehicle)
 *  - skipBranch (kwhAtVehicle bereits gesetzt) zaehlt nicht als unmatched
 */
@ExtendWith(MockitoExtension.class)
class XpengChargeMatcherTest {

    @Mock EvLogRepository evLogRepository;
    @InjectMocks XpengChargeMatcher matcher;

    private static final UUID CAR = UUID.randomUUID();

    private DetectedChargingSession session(BigDecimal odoKm, BigDecimal kwh,
                                            LocalDateTime startedAt, LocalDateTime endedAt) {
        return new DetectedChargingSession(
                startedAt, endedAt,
                new BigDecimal("50"), new BigDecimal("90"), kwh,
                null,
                new BigDecimal("11.0"),
                odoKm,
                "AC",
                null);
    }

    private DetectedChargingSession session(BigDecimal odoKm, BigDecimal kwh) {
        return session(odoKm, kwh,
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 13, 30));
    }

    private EvLog.EvLogBuilder logBuilder(int odoKm, LocalDateTime loggedAt) {
        return EvLog.builder().id(UUID.randomUUID()).carId(CAR)
                .loggedAt(loggedAt)
                .odometerKm(odoKm)
                .dataSource(DataSource.API_UPLOAD)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now());
    }

    // -- Szenario A: Heimladung uebernacht, 1 User-Log + n XPeng-Sub-Sessions am selben Odo
    @Test
    void aggregatesMultipleSessionsAtSameOdometerIntoOneLog() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 6, 10, 0);
        DetectedChargingSession s1 = session(new BigDecimal("14230"),
                new BigDecimal("0.379"), base.plusMinutes(6), base.plusMinutes(11));
        DetectedChargingSession s2 = session(new BigDecimal("14230"),
                new BigDecimal("29.938"), base.plusMinutes(16), base.plusHours(3));

        EvLog manual = logBuilder(14230, LocalDateTime.of(2026, 5, 6, 9, 44))
                .kwhCharged(new BigDecimal("31.13"))
                .costEur(new BigDecimal("9.49"))
                .build();
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(manual));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s1, s2));

        assertEquals(1, result.enriched(), "beide Sessions auf 1 Log -> 1 enriched");
        assertTrue(result.unmatched().isEmpty(), "beide Sessions matchten");
        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        EvLog saved = captor.getValue();
        // Summe der zwei Session-kWh in kwh_at_vehicle
        assertEquals(0, saved.getKwhAtVehicle().compareTo(new BigDecimal("30.317")));
        // Brutto + Preis unangetastet
        assertEquals(0, saved.getKwhCharged().compareTo(new BigDecimal("31.13")));
        assertEquals(0, saved.getCostEur().compareTo(new BigDecimal("9.49")));
    }

    // -- Szenario B: AC morgens (odo 14000) + DC nachmittags (odo 14150) am gleichen Tag
    @Test
    void differentOdometersGoToDifferentLogs() {
        LocalDateTime day = LocalDateTime.of(2026, 5, 10, 0, 0);
        DetectedChargingSession ac = session(new BigDecimal("14000"),
                new BigDecimal("20.0"), day.plusHours(8), day.plusHours(11));
        DetectedChargingSession dc = session(new BigDecimal("14150"),
                new BigDecimal("30.0"), day.plusHours(15), day.plusHours(15).plusMinutes(30));

        EvLog morningLog = logBuilder(14000, day.plusHours(8)).kwhCharged(new BigDecimal("22.0")).build();
        EvLog afternoonLog = logBuilder(14150, day.plusHours(15)).kwhCharged(new BigDecimal("32.0")).build();

        when(evLogRepository.findChargeMatchCandidates(eq(CAR), eq(13999), eq(14001),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(morningLog));
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), eq(14149), eq(14151),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(afternoonLog));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(ac, dc));

        assertEquals(2, result.enriched());
        assertTrue(result.unmatched().isEmpty());
        verify(evLogRepository, times(2)).save(any());
    }

    // -- Szenario C: n User-Logs + m XPeng-Sessions am gleichen Odo -> Tie-Breaker
    @Test
    void tieBreaksOnClosestStartedAtWhenMultipleCandidatesAtSameOdometer() {
        LocalDateTime day = LocalDateTime.of(2026, 5, 5, 0, 0);
        DetectedChargingSession s = session(new BigDecimal("14207"),
                new BigDecimal("0.74"), day.plusHours(19).plusMinutes(50),
                day.plusHours(20).plusMinutes(50));

        EvLog farLog = logBuilder(14207, day.plusHours(8)).kwhCharged(new BigDecimal("0.30")).build();
        EvLog closeLog = logBuilder(14207, day.plusHours(20)).kwhCharged(new BigDecimal("0.74")).build();
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), eq(14206), eq(14208),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(farLog, closeLog));

        matcher.matchAndEnrich(CAR, List.of(s));

        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        assertEquals(closeLog.getId(), captor.getValue().getId(),
                "der zeitlich naechste zur Session sollte gewinnen");
    }

    // -- Szenario E: User-Log hat keinen Odometer -> Fallback ueber Zeit
    @Test
    void matchesViaTimeWhenLogHasNoOdometer() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 10, 12, 0);
        DetectedChargingSession s = session(new BigDecimal("14000"),
                new BigDecimal("25.50"), base, base.plusHours(1));

        EvLog logNoOdo = logBuilder(14000, base.plusMinutes(5))
                .kwhCharged(new BigDecimal("27.00"))
                .build();
        // Repository liefert Logs auch ohne Odo zurueck (eq oder NULL filter in der Query)
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(logNoOdo));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(1, result.enriched());
        verify(evLogRepository).save(any());
    }

    // -- Szenario H: kein Match -> Session bleibt unmatched
    @Test
    void leavesSessionUnmatchedWhenNoCandidate() {
        DetectedChargingSession s = session(new BigDecimal("14000"), new BigDecimal("20.0"));
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(0, result.enriched());
        assertEquals(1, result.unmatched().size());
        assertSame(s, result.unmatched().get(0));
        verify(evLogRepository, never()).save(any());
    }

    // -- Bug-Fix-Test: bestehender Log hat schon kwh_at_vehicle -> skip, kein unmatched, kein doppelter Eintrag
    @Test
    void skipsWhenKwhAtVehicleAlreadyPresentAndDoesNotMarkUnmatched() {
        DetectedChargingSession s = session(new BigDecimal("14000"), new BigDecimal("25.0"));

        EvLog alreadyEnriched = logBuilder(14000, LocalDateTime.of(2026, 5, 10, 12, 0))
                .kwhCharged(new BigDecimal("27.00"))
                .kwhAtVehicle(new BigDecimal("24.80"))
                .build();
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(alreadyEnriched));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(0, result.enriched());
        assertTrue(result.unmatched().isEmpty(),
                "skipBranch darf nicht als unmatched zaehlen - sonst doppelt im Feed");
        verify(evLogRepository, never()).save(any());
    }

    // -- Falls mehrere XPeng-Sessions am Odo matchten + 1. Save fuellt kwhAtVehicle
    // muessen die folgenden Sessions am selben Odo summiert weiterleiten,
    // statt vom Skip-Branch verschluckt zu werden.
    @Test
    void aggregatesAcrossMultipleSessionsEvenWhenIntermediateSaveSetsKwhAtVehicle() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 6, 10, 0);
        DetectedChargingSession s1 = session(new BigDecimal("14230"),
                new BigDecimal("10.0"), base, base.plusHours(1));
        DetectedChargingSession s2 = session(new BigDecimal("14230"),
                new BigDecimal("15.0"), base.plusHours(2), base.plusHours(3));

        EvLog log = logBuilder(14230, base).kwhCharged(new BigDecimal("26.0")).build();
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(log));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s1, s2));

        assertEquals(1, result.enriched());
        assertTrue(result.unmatched().isEmpty());
        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getKwhAtVehicle().compareTo(new BigDecimal("25.0")),
                "Summe der Sub-Sessions: 10+15");
    }

    // -- Power + ChargingType uebernehmen wie gehabt
    @Test
    void enrichmentFillsMissingMaxPowerAndChargingType() {
        DetectedChargingSession s = new DetectedChargingSession(
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 13, 30),
                new BigDecimal("50"), new BigDecimal("90"),
                new BigDecimal("25.50"),
                null,
                new BigDecimal("125.0"),
                new BigDecimal("13508"),
                "DC",
                java.util.Map.of("battery", java.util.Map.of("pack_temp_max_c", 42.3)));
        EvLog manual = logBuilder(13508, LocalDateTime.of(2026, 5, 10, 13, 25))
                .kwhCharged(new BigDecimal("27.00")).build();
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(manual));

        matcher.matchAndEnrich(CAR, List.of(s));

        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        EvLog saved = captor.getValue();
        assertEquals(0, saved.getMaxChargingPowerKw().compareTo(new BigDecimal("125.0")));
        assertEquals(ChargingType.DC, saved.getChargingType());
        verify(evLogRepository).updateTelemetryExtrasById(eq(manual.getId()), anyString());
    }

    @Test
    void preservesExistingMaxPowerWhenAlreadySet() {
        DetectedChargingSession s = session(new BigDecimal("13508"), new BigDecimal("25.50"));
        EvLog manual = logBuilder(13508, LocalDateTime.of(2026, 5, 10, 13, 25))
                .kwhCharged(new BigDecimal("27.00"))
                .maxChargingPowerKw(new BigDecimal("22.0"))
                .chargingType(ChargingType.AC).build();
        when(evLogRepository.findChargeMatchCandidates(eq(CAR), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(manual));

        matcher.matchAndEnrich(CAR, List.of(s));

        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        EvLog saved = captor.getValue();
        assertEquals(0, saved.getMaxChargingPowerKw().compareTo(new BigDecimal("22.0")));
        assertEquals(ChargingType.AC, saved.getChargingType());
    }

    @Test
    void emptySessionListReturnsEmptyResult() {
        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, new ArrayList<>());
        assertEquals(0, result.enriched());
        assertTrue(result.unmatched().isEmpty());
        verifyNoInteractions(evLogRepository);
    }

    // -- Session ohne Odometer -> kein Match (Service-Logik: bei null kein Odo-Match)
    @Test
    void sessionWithoutOdometerDoesNotMatch() {
        DetectedChargingSession s = session(null, new BigDecimal("25.50"));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(0, result.enriched());
        assertEquals(1, result.unmatched().size());
        verifyNoInteractions(evLogRepository);
    }
}
