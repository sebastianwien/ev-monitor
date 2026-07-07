package com.evmonitor.application.consumption;

import com.evmonitor.application.ConsumptionResult;
import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Testet das rollierende Verbrauchsfenster für Spritmonitor-Importe ohne Kilometerstand.
 *
 * Spritmonitor-User pflegen bei Teilladungen oft keinen km-Stand. Solche Logs (SPRITMONITOR_IMPORT,
 * odometer=null, kWh vorhanden) sind für die Verbrauchskette transparent: Sie werden beim
 * Rückwärtssuchen nach logX übersprungen, ihre fahrzeugseitige Energie (kWh × Ladeeffizienz)
 * wird als intermediateKwh akkumuliert. Das Ergebnis ist eine exakte Energiebilanz zwischen
 * zwei Ankerpunkten mit km-Stand und SoC.
 *
 * Die Regel ist bewusst eng gefasst (nur SPRITMONITOR_IMPORT ohne Odometer):
 *   - TESLA_FLEET_IMPORT ohne Odometer bricht die Kette weiterhin (Snapshot-Miss kann
 *     fehlende Sessions bedeuten — siehe EvLogServiceGoeConsumptionChainTest Szenario 5)
 *   - USER_LOGGED ohne Odometer bricht die Kette weiterhin
 *   - WALLBOX_GOE bleibt unverändert (eigene Transparenz-Regel, rohe kWh)
 */
class ConsumptionCalculationServiceSpritmonitorWindowTest {

    private static final BigDecimal CAPACITY = new BigDecimal("75.0");
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 5, 1, 10, 0);

    private ConsumptionCalculationService service;

    @BeforeEach
    void setUp() {
        PlausibilityProperties props = new PlausibilityProperties(); // AC 0.90, DC 0.95, minTrip 10 km
        service = new ConsumptionCalculationService(
                mock(VehicleSpecificationRepository.class), props, mock(BatterySohRepository.class));
    }

    // ── Kernszenario: Teilladungen ohne km-Stand zwischen zwei Ankern ───────────

    /**
     * Anker A (odo=10000, SoC=80) → SM1 (12 kWh AC, kein odo) → SM2 (8 kWh AC, kein odo)
     * → Anker B (odo=10300, SoC=80, 52.5 kWh DC)
     *
     * effektiv(B)      = 52.5 × 0.95 = 49.875
     * intermediateKwh  = 12 × 0.90 + 8 × 0.90 = 18.0 (fahrzeugseitig normalisiert)
     * SoC-Delta        = (80 - 80) × 75/100 = 0
     * Verbrauch        = 67.875 / 300 × 100 = 22.63 kWh/100km
     */
    @Test
    void spritmonitorLogsWithoutOdometer_areTransparent_effectiveKwhAccumulated() {
        EvLog anchorA = anchor(10000, "80", DataSource.USER_LOGGED, BASE);
        EvLog sm1 = spritmonitorNoOdo(new BigDecimal("12.0"), ChargingType.AC, BASE.plusDays(1));
        EvLog sm2 = spritmonitorNoOdo(new BigDecimal("8.0"), ChargingType.AC, BASE.plusDays(2));
        EvLog anchorB = completeLog(10300, "80", new BigDecimal("52.5"), ChargingType.DC,
                DataSource.SPRITMONITOR_IMPORT, BASE.plusDays(3));

        ConsumptionCalculationService.PerLogConsumptionResult result =
                service.calculateConsumptionPerLogDetailed(
                        List.of(anchorA, sm1, sm2, anchorB), date -> CAPACITY, null, null);

        ConsumptionResult b = result.byLogId().get(anchorB.getId());
        assertNotNull(b, "Anker B muss einen Fensterwert bekommen");
        assertEquals(new BigDecimal("22.63"), b.value());
        assertEquals(300, b.distanceKm());
        assertEquals(java.util.Set.of(sm1.getId(), sm2.getId()), result.absorbedLogIds(),
                "beide Teilladungen sind im Fenster aufgegangen");
    }

    /** Zwischenladung ohne kWh (nur Kosten erfasst) → Energie unbekannt → harter Kettenbruch. */
    @Test
    void spritmonitorIntermediateWithoutKwh_breaksChain() {
        EvLog anchorA = anchor(10000, "80", DataSource.USER_LOGGED, BASE);
        EvLog smNoKwh = spritmonitorNoOdo(BigDecimal.ZERO, ChargingType.AC, BASE.plusDays(1));
        EvLog anchorB = completeLog(10300, "80", new BigDecimal("52.5"), ChargingType.DC,
                DataSource.SPRITMONITOR_IMPORT, BASE.plusDays(2));

        ConsumptionCalculationService.PerLogConsumptionResult result =
                service.calculateConsumptionPerLogDetailed(
                        List.of(anchorA, smNoKwh, anchorB), date -> CAPACITY, null, null);

        assertFalse(result.byLogId().containsKey(anchorB.getId()),
                "Zwischenladung mit unbekannter Energie muss die Kette brechen");
        assertTrue(result.absorbedLogIds().isEmpty());
    }

    /** Spritmonitor-Log MIT km-Stand und SoC ist regulärer Anker, wird nicht übersprungen. */
    @Test
    void spritmonitorLogWithOdometer_actsAsAnchor_notTransparent() {
        EvLog anchorA = anchor(10000, "80", DataSource.USER_LOGGED, BASE);
        EvLog smMid = completeLog(10150, "70", new BigDecimal("10.0"), ChargingType.AC,
                DataSource.SPRITMONITOR_IMPORT, BASE.plusDays(1));
        EvLog anchorB = completeLog(10300, "80", new BigDecimal("52.5"), ChargingType.DC,
                DataSource.SPRITMONITOR_IMPORT, BASE.plusDays(2));

        ConsumptionCalculationService.PerLogConsumptionResult result =
                service.calculateConsumptionPerLogDetailed(
                        List.of(anchorA, smMid, anchorB), date -> CAPACITY, null, null);

        // B paart mit smMid (nächster Anker), nicht mit A:
        // effektiv(B) = 49.875, SoC-Delta = (70-80) × 75/100 = -7.5 → 42.375 / 150 × 100 = 28.25
        ConsumptionResult b = result.byLogId().get(anchorB.getId());
        assertNotNull(b);
        assertEquals(new BigDecimal("28.25"), b.value());
        assertEquals(150, b.distanceKm());
        assertTrue(result.absorbedLogIds().isEmpty(), "kein Log wurde übersprungen");
    }

    // ── Guards: kein Bleeding in andere DataSources ─────────────────────────────

    /** TESLA_FLEET_IMPORT ohne Odometer (Snapshot-Miss) bricht die Kette weiterhin. */
    @Test
    void teslaFleetImportWithoutOdometer_stillBreaksChain() {
        EvLog anchorA = anchor(10000, "80", DataSource.USER_LOGGED, BASE);
        EvLog tesla = noOdoLog(new BigDecimal("20.0"), ChargingType.DC,
                DataSource.TESLA_FLEET_IMPORT, BASE.plusDays(1));
        EvLog anchorB = completeLog(10300, "80", new BigDecimal("52.5"), ChargingType.DC,
                DataSource.USER_LOGGED, BASE.plusDays(2));

        ConsumptionCalculationService.PerLogConsumptionResult result =
                service.calculateConsumptionPerLogDetailed(
                        List.of(anchorA, tesla, anchorB), date -> CAPACITY, null, null);

        assertFalse(result.byLogId().containsKey(anchorB.getId()));
        assertTrue(result.absorbedLogIds().isEmpty());
    }

    /** Manueller Log ohne Odometer bricht die Kette weiterhin. */
    @Test
    void userLoggedWithoutOdometer_stillBreaksChain() {
        EvLog anchorA = anchor(10000, "80", DataSource.USER_LOGGED, BASE);
        EvLog manual = noOdoLog(new BigDecimal("15.0"), ChargingType.AC,
                DataSource.USER_LOGGED, BASE.plusDays(1));
        EvLog anchorB = completeLog(10300, "80", new BigDecimal("52.5"), ChargingType.DC,
                DataSource.USER_LOGGED, BASE.plusDays(2));

        ConsumptionCalculationService.PerLogConsumptionResult result =
                service.calculateConsumptionPerLogDetailed(
                        List.of(anchorA, manual, anchorB), date -> CAPACITY, null, null);

        assertFalse(result.byLogId().containsKey(anchorB.getId()));
        assertTrue(result.absorbedLogIds().isEmpty());
    }

    /** Bestehende Map-Overloads liefern dieselben Werte (Delegation, kein Verhaltensbruch). */
    @Test
    void existingOverload_returnsSameValues() {
        EvLog anchorA = anchor(10000, "80", DataSource.USER_LOGGED, BASE);
        EvLog sm1 = spritmonitorNoOdo(new BigDecimal("12.0"), ChargingType.AC, BASE.plusDays(1));
        EvLog anchorB = completeLog(10300, "80", new BigDecimal("52.5"), ChargingType.DC,
                DataSource.SPRITMONITOR_IMPORT, BASE.plusDays(2));

        Map<UUID, ConsumptionResult> map = service.calculateConsumptionPerLog(
                List.of(anchorA, sm1, anchorB), CAPACITY, null, null);

        // effektiv(B) = 49.875 + 12 × 0.90 = 60.675 → / 300 × 100 = 20.23
        assertEquals(new BigDecimal("20.23"), map.get(anchorB.getId()).value());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Anker: odometer + SoC, ohne eigene Energiedaten (reiner logX-Kandidat). */
    private EvLog anchor(int odometer, String soc, DataSource source, LocalDateTime at) {
        return base(source, at)
                .odometerKm(odometer)
                .socAfterChargePercent(new BigDecimal(soc))
                .build();
    }

    /** Vollständiger Log: odometer + SoC + kWh (isComplete, tauglich als logY und logX). */
    private EvLog completeLog(int odometer, String soc, BigDecimal kwh, ChargingType type,
            DataSource source, LocalDateTime at) {
        return base(source, at)
                .odometerKm(odometer)
                .socAfterChargePercent(new BigDecimal(soc))
                .kwhCharged(kwh)
                .chargingType(type)
                .build();
    }

    private EvLog spritmonitorNoOdo(BigDecimal kwh, ChargingType type, LocalDateTime at) {
        return noOdoLog(kwh, type, DataSource.SPRITMONITOR_IMPORT, at);
    }

    private EvLog noOdoLog(BigDecimal kwh, ChargingType type, DataSource source, LocalDateTime at) {
        return base(source, at)
                .kwhCharged(kwh)
                .socAfterChargePercent(new BigDecimal("75"))
                .chargingType(type)
                .build();
    }

    private EvLog.EvLogBuilder base(DataSource source, LocalDateTime at) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .costEur(BigDecimal.TEN)
                .dataSource(source)
                .includeInStatistics(true)
                .chargingType(ChargingType.UNKNOWN)
                .loggedAt(at)
                .createdAt(now)
                .updatedAt(now);
    }
}
