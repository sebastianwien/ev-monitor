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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XpengChargeMatcherTest {

    @Mock EvLogRepository evLogRepository;
    @InjectMocks XpengChargeMatcher matcher;

    private static final UUID CAR = UUID.randomUUID();

    private DetectedChargingSession session(BigDecimal odoKm, BigDecimal kwh, BigDecimal socStart, BigDecimal socEnd) {
        return new DetectedChargingSession(
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 13, 30),
                socStart, socEnd, kwh,
                new BigDecimal("11.0"),
                odoKm,
                "AC",
                null);
    }

    private EvLog existingLog(EvLog.EvLogBuilder b) {
        return b.id(UUID.randomUUID()).carId(CAR)
                .loggedAt(LocalDateTime.of(2026, 5, 10, 13, 25))
                .odometerKm(13508)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void enrichesManualLogWithNettoKwh() {
        DetectedChargingSession s = session(new BigDecimal("13508"),
                new BigDecimal("25.50"), new BigDecimal("50"), new BigDecimal("90"));
        EvLog manual = existingLog(EvLog.builder()
                .kwhCharged(new BigDecimal("27.00"))           // Brutto da
                .costEur(new BigDecimal("9.45"))                // Preis da
                .socBeforeChargePercent(new BigDecimal("50"))
                .socAfterChargePercent(new BigDecimal("90")));
        when(evLogRepository.findChargeMatchCandidate(eq(CAR), eq(13508), any(LocalDateTime.class)))
                .thenReturn(Optional.of(manual));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(1, result.enriched());
        assertTrue(result.unmatched().isEmpty());
        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        EvLog saved = captor.getValue();
        assertEquals(0, saved.getKwhAtVehicle().compareTo(new BigDecimal("25.50")));
        // Brutto + Preis bleiben unangetastet
        assertEquals(0, saved.getKwhCharged().compareTo(new BigDecimal("27.00")));
        assertEquals(0, saved.getCostEur().compareTo(new BigDecimal("9.45")));
    }

    @Test
    void skipsWhenKwhAtVehicleAlreadyPresent() {
        DetectedChargingSession s = session(new BigDecimal("13508"),
                new BigDecimal("25.50"), new BigDecimal("50"), new BigDecimal("90"));
        EvLog manual = existingLog(EvLog.builder()
                .kwhCharged(new BigDecimal("27.00"))
                .kwhAtVehicle(new BigDecimal("24.80"))   // Netto schon da -> nicht überschreiben
                .costEur(new BigDecimal("9.45")));
        when(evLogRepository.findChargeMatchCandidate(eq(CAR), eq(13508), any(LocalDateTime.class)))
                .thenReturn(Optional.of(manual));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(0, result.enriched());
        assertTrue(result.unmatched().isEmpty(), "match existed - nicht als new-log durchreichen");
        verify(evLogRepository, never()).save(any());
    }

    @Test
    void leavesSessionUnmatchedWhenNoCandidate() {
        DetectedChargingSession s = session(new BigDecimal("13508"),
                new BigDecimal("25.50"), new BigDecimal("50"), new BigDecimal("90"));
        when(evLogRepository.findChargeMatchCandidate(eq(CAR), eq(13508), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(0, result.enriched());
        assertEquals(1, result.unmatched().size());
        assertSame(s, result.unmatched().get(0));
        verify(evLogRepository, never()).save(any());
    }

    @Test
    void matchesViaTimeWindowWhenSessionHasNoOdometer() {
        DetectedChargingSession s = session(null,
                new BigDecimal("25.50"), new BigDecimal("50"), new BigDecimal("90"));
        EvLog manual = existingLog(EvLog.builder()
                .kwhCharged(new BigDecimal("27.00")));
        when(evLogRepository.findChargeMatchCandidate(eq(CAR), eq(null), any(LocalDateTime.class)))
                .thenReturn(Optional.of(manual));

        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of(s));

        assertEquals(1, result.enriched());
        verify(evLogRepository).save(any());
    }

    @Test
    void enrichmentFillsMissingMaxPowerAndChargingType() {
        DetectedChargingSession s = new DetectedChargingSession(
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 13, 30),
                new BigDecimal("50"), new BigDecimal("90"),
                new BigDecimal("25.50"),
                new BigDecimal("125.0"),       // DC fast charge
                new BigDecimal("13508"),
                "DC",
                java.util.Map.of("battery", java.util.Map.of("pack_temp_max_c", 42.3)));
        EvLog manual = existingLog(EvLog.builder()
                .kwhCharged(new BigDecimal("27.00")));   // kein maxPower, kein chargingType
        when(evLogRepository.findChargeMatchCandidate(eq(CAR), eq(13508), any(LocalDateTime.class)))
                .thenReturn(Optional.of(manual));

        matcher.matchAndEnrich(CAR, List.of(s));

        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        EvLog saved = captor.getValue();
        assertEquals(0, saved.getMaxChargingPowerKw().compareTo(new BigDecimal("125.0")));
        assertEquals(ChargingType.DC, saved.getChargingType());

        // telemetry_extras geht NICHT via save() - direkt via Update auf die Log-ID.
        // ID-basierter Update vermeidet Mismatches bei nicht-eindeutigem Composite-Key
        // (z.B. wenn loggedAt nachtraeglich angepasst wird).
        verify(evLogRepository).updateTelemetryExtrasById(eq(manual.getId()), anyString());
    }

    @Test
    void preservesExistingMaxPowerWhenAlreadySet() {
        DetectedChargingSession s = session(new BigDecimal("13508"),
                new BigDecimal("25.50"), new BigDecimal("50"), new BigDecimal("90"));
        EvLog manual = existingLog(EvLog.builder()
                .kwhCharged(new BigDecimal("27.00"))
                .maxChargingPowerKw(new BigDecimal("22.0"))    // User hat schon eingetragen
                .chargingType(ChargingType.AC));
        when(evLogRepository.findChargeMatchCandidate(eq(CAR), eq(13508), any(LocalDateTime.class)))
                .thenReturn(Optional.of(manual));

        matcher.matchAndEnrich(CAR, List.of(s));

        ArgumentCaptor<EvLog> captor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(captor.capture());
        EvLog saved = captor.getValue();
        assertEquals(0, saved.getMaxChargingPowerKw().compareTo(new BigDecimal("22.0")),
                "User-Wert nicht ueberschreiben");
        assertEquals(ChargingType.AC, saved.getChargingType());
    }

    @Test
    void emptySessionListReturnsEmptyResult() {
        XpengChargeMatcher.MatchResult result = matcher.matchAndEnrich(CAR, List.of());
        assertEquals(0, result.enriched());
        assertTrue(result.unmatched().isEmpty());
        verifyNoInteractions(evLogRepository);
    }
}
