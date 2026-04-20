package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testet das Verhalten von go-e Sub-Sessions in der SoC-basierten Verbrauchskette.
 *
 * WALLBOX_GOE ist als transparentForConsumptionChain markiert: Sub-Sessions ohne SoC/Odometer
 * werden beim Rückwärtssuchen nach logX übersprungen, ihre kWh aber akkumuliert und zur
 * SoC-Delta-Energie addiert. Das Ergebnis: die Kette bleibt intakt, der Verbrauch wird
 * korrekt mit den zwischenzeitlich geladenen kWh berechnet.
 *
 * Verglichen mit _isLadegruppe (gleicher odometerKm) und go-e mit manuellem SoC.
 */
class EvLogServiceGoeConsumptionChainTest extends AbstractIntegrationTest {

    @Autowired private EvLogStatisticsService evLogService;

    private UUID userId;
    private UUID carId;

    // Referenz-Szenario: 300 km Trip, logX SoC=80%, logY 52.5 kWh, SoC=85%, Battery=75kWh
    //   socBefore(logY) = 85 - (52.5/75*100) = 15%
    //   energyConsumed  = (80 - 15) * 75/100 = 48.75 kWh
    //   consumption     = 48.75/300*100 = 16.25 kWh/100km
    private static final BigDecimal LOG_B_KWH     = new BigDecimal("52.5");
    private static final BigDecimal LOG_B_COST    = new BigDecimal("15.00");
    private static final int        LOG_A_ODOMETER = 10000;
    private static final int        LOG_B_ODOMETER = 10300;
    private static final int        LOG_A_SOC      = 80;
    private static final int        LOG_B_SOC      = 85;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("goe-chain-" + System.nanoTime() + "@test.com");
        userId = user.getId();
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3); // 75 kWh
        carId = car.getId();
    }

    // ── Szenario 1: go-e Sub-Sessions ohne SoC/Odometer ────────────────────────

    /**
     * Aufbau:
     *   Manual Log A (odometer=10000, SoC=80%)
     *   go-e Sub-Session 1  (kein Odometer, kein SoC) → transparent, kWh wird akkumuliert
     *   go-e Sub-Session 2  (kein Odometer, kein SoC) → transparent, kWh wird akkumuliert
     *   Manual Log B (odometer=10300, SoC=85%)
     *
     * findPreviousLog(Log B):
     *   j=goe2 → transparent → intermediateKwh += 5.2
     *   j=goe1 → transparent → intermediateKwh += 8.5
     *   j=logA → canBeUsedAsLogX() ✓ → return PreviousLogResult(logA, 13.7)
     *
     * calculateConsumption(logA, logB, 75, intermediateKwh=13.7):
     *   socBefore(B) = 85 - (52.5/75*100) = 15%
     *   energyConsumed = (80 - 15) * 75/100 + 13.7 = 48.75 + 13.7 = 62.45 kWh
     *   consumption = 62.45/300*100 = 20.82 kWh/100km
     */
    @Test
    void goeSubSessions_transparentForChain_kwhAccumulatedInConsumption() {
        LocalDateTime base = LocalDateTime.now().minusDays(10).withHour(8);

        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("11.00"),
                180, null, LOG_A_ODOMETER, null, LOG_A_SOC,
                base, ChargingType.AC, null, null,
                false, null));

        evLogRepository.save(EvLog.createFromInternal(
                carId, new BigDecimal("8.5"), 90, null,
                base.plusDays(1).withHour(10),
                null, null, DataSource.WALLBOX_GOE, new BigDecimal("2.38"), ChargingType.AC,
                null, null, null, null, null));

        evLogRepository.save(EvLog.createFromInternal(
                carId, new BigDecimal("5.2"), 60, null,
                base.plusDays(1).withHour(10).plusMinutes(45),
                null, null, DataSource.WALLBOX_GOE, new BigDecimal("1.46"), ChargingType.AC,
                null, null, null, null, null));

        evLogRepository.save(EvLog.createNew(carId, LOG_B_KWH, LOG_B_COST,
                90, null, LOG_B_ODOMETER, null, LOG_B_SOC,
                base.plusDays(2), ChargingType.AC, null, null,
                false, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // SoC-basierter Verbrauch — nicht geschätzt
        assertEquals(0, stats.estimatedConsumptionCount(),
                "WALLBOX_GOE Sub-Sessions sind transparent — SoC-Kette bleibt intakt");
        assertEquals(new BigDecimal("20.82"), stats.avgConsumptionKwhPer100km(),
                "Verbrauch = (48.75 SoC-Delta + 13.7 go-e kWh) / 300km * 100 = 20.82 kWh/100km");

        // go-e kWh + Kosten in Statistiken (alle Logs haben include=true)
        assertEquals(0, new BigDecimal("106.20").compareTo(stats.totalKwhCharged()),
                "40.0 (A) + 8.5 + 5.2 (go-e) + 52.5 (B) = 106.2 kWh");
        BigDecimal expectedCost = new BigDecimal("11.00").add(new BigDecimal("2.38"))
                .add(new BigDecimal("1.46")).add(LOG_B_COST);
        assertEquals(0, expectedCost.compareTo(stats.totalCostEur()));
    }

    // ── Szenario 2: _isLadegruppe (gleicher Odometer) — Kette NICHT unterbrochen ─

    /**
     * Aufbau:
     *   Manual Log A  (odometer=10000, SoC=80%)
     *   Nachlader A2  (odometer=10000, SoC=80%) ← gleicher Odometer wie A → _isLadegruppe
     *   Manual Log B  (odometer=10300, SoC=85%)
     *
     * findPreviousLog(Log B) → A2 (direkter Vorgänger)
     * A2.canBeUsedAsLogX(): odometerKm=10000 ✓, socAfter=80 ✓ → true
     * → SoC-basierter Verbrauch für Log B funktioniert normal: 16.25 kWh/100km
     */
    @Test
    void ladegruppe_sameOdometer_doesNotBreakSocConsumptionChain() {
        LocalDateTime base = LocalDateTime.now().minusDays(10).withHour(8);

        // Manual Log A
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("11.00"),
                180, null, LOG_A_ODOMETER, null, LOG_A_SOC,
                base, ChargingType.AC, null, null,
                false, null));

        // Nachlader — gleicher Odometer, SoC auch 80 (spielt keine Rolle für die Kette)
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("5.0"), new BigDecimal("1.50"),
                30, null, LOG_A_ODOMETER, null, LOG_A_SOC,
                base.plusDays(1), ChargingType.AC, null, null,
                false, null));

        // Manual Log B — direkter Vorgänger ist der Nachlader mit odometerKm+SoC
        evLogRepository.save(EvLog.createNew(carId, LOG_B_KWH, LOG_B_COST,
                90, null, LOG_B_ODOMETER, null, LOG_B_SOC,
                base.plusDays(2), ChargingType.AC, null, null,
                false, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // SoC-basierter Verbrauch ist aktiv — NICHT geschätzt
        assertEquals(0, stats.estimatedConsumptionCount(),
                "Ladegruppe mit gleichem Odometer unterbricht die SoC-Kette NICHT");
        assertNotNull(stats.avgConsumptionKwhPer100km());
        assertEquals(new BigDecimal("16.25"), stats.avgConsumptionKwhPer100km(),
                "SoC-basierter Verbrauch: 48.75 kWh / 300 km = 16.25 kWh/100km");
    }

    // ── Szenario 4: nur go-e Sessions mit Odometer/SoC ──────────────────────────

    /**
     * Stellt sicher dass Verbrauch angezeigt wird wenn alle logY-Kandidaten WALLBOX_GOE sind.
     * Seit V77 haben GOE-Logs include=true, kein Session-Group-Mechanismus mehr nötig.
     *
     * Aufbau:
     *   GOE A (odometer=10000, SoC=80%)
     *   GOE B (odometer=10300, SoC=85%, 52.5 kWh)
     *
     * Erwartung: avgConsumptionKwhPer100km = 16.25 kWh/100km (SoC-basiert, nicht geschätzt)
     */
    @Test
    void goeOnlySessionsWithSocAndOdometer_consumptionVisible() {
        LocalDateTime base = LocalDateTime.now().minusDays(5).withHour(8);

        evLogRepository.save(EvLog.createFromInternal(
                carId, new BigDecimal("40.0"), 180, null,
                base,
                null, null, DataSource.WALLBOX_GOE, new BigDecimal("11.00"), ChargingType.AC,
                LOG_A_ODOMETER, null, LOG_A_SOC, null, null));

        evLogRepository.save(EvLog.createFromInternal(
                carId, LOG_B_KWH, 90, null,
                base.plusDays(2),
                null, null, DataSource.WALLBOX_GOE, LOG_B_COST, ChargingType.AC,
                LOG_B_ODOMETER, null, LOG_B_SOC, null, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        assertNotNull(stats.avgConsumptionKwhPer100km(),
                "Verbrauch muss bei reinen GOE-Sessions sichtbar sein");
        assertEquals(new BigDecimal("16.25"), stats.avgConsumptionKwhPer100km(),
                "SoC-basierter Verbrauch: 48.75 kWh / 300 km = 16.25 kWh/100km");
        assertEquals(0, stats.estimatedConsumptionCount(),
                "SoC-Daten vorhanden — kein Fallback-Schätzer");
    }

    // ── Szenario 5: gemischte DataSources — TESLA_FLEET_IMPORT + GOE + USER_LOGGED ─

    /**
     * Reproduziert den gemeldeten Production-Bug von florianbehr0.
     *
     * TESLA_FLEET_IMPORT Logs erhalten ihr Odometer NICHT aus der Fleet API (Charging History
     * enthält kein Odometer), sondern aus tesla_charging_snapshot — dem 5-Minuten-Polling
     * während des Ladevorgangs. Wenn das Polling keinen Snapshot erwischt, bleibt odometer=null.
     * Das ist KEIN Bug im Import, sondern erwartetes Verhalten (Snapshot-Lookup miss).
     *
     * TESLA_FLEET_IMPORT ist NICHT transparent für die Consumption Chain (nur WALLBOX_GOE ist es).
     * Ein TESLA_FLEET_IMPORT ohne Odometer bricht die Kette — korrekt, weil er ein unbekanntes
     * Energieereignis darstellt das den SoC-Delta korrumpieren würde.
     *
     * Aufbau:
     *   T1: TESLA_FLEET_IMPORT (odometer=5000, soc=90)
     *   T2: TESLA_FLEET_IMPORT (kein odometer, soc=75) — Snapshot miss → KETTENBRUCH für G1
     *   G1: WALLBOX_GOE (odometer=10000, soc=80) → kein Verbrauch berechenbar (T2 davor bricht die Kette)
     *   G2: WALLBOX_GOE (odometer=10300, soc=85, 52.5 kWh) → logX=G1 → 16.25 kWh/100km
     */
    @Test
    void mixedSources_teslaFleetImportWithoutOdometerBreaksChain_goeSubChainStillVisible() {
        LocalDateTime base = LocalDateTime.now().minusDays(10).withHour(8);

        // T1 — TESLA_FLEET_IMPORT mit Odometer
        evLogRepository.save(EvLog.createFromInternal(
                carId, new BigDecimal("40.0"), 120, null,
                base,
                null, null, DataSource.TESLA_FLEET_IMPORT, null, ChargingType.DC,
                5000, null, 90, null, null));

        // T2 — TESLA_FLEET_IMPORT ohne Odometer (Snapshot-Lookup miss)
        // Bricht die Kette: canBeUsedAsLogX()=false, nicht transparent → Kettenbruch für G1
        evLogRepository.save(EvLog.createFromInternal(
                carId, new BigDecimal("20.0"), 45, null,
                base.plusDays(1),
                null, null, DataSource.TESLA_FLEET_IMPORT, null, ChargingType.DC,
                null, null, 75, null, null));

        // G1 — GOE mit Odometer+SoC, kein Verbrauch berechenbar (T2 davor bricht die Kette)
        evLogRepository.save(EvLog.createFromInternal(
                carId, new BigDecimal("30.0"), 90, null,
                base.plusDays(2),
                null, null, DataSource.WALLBOX_GOE, new BigDecimal("8.40"), ChargingType.AC,
                LOG_A_ODOMETER, null, LOG_A_SOC, null, null));

        // G2 — logX=G1 → 300 km Trip → 16.25 kWh/100km
        evLogRepository.save(EvLog.createFromInternal(
                carId, LOG_B_KWH, 90, null,
                base.plusDays(4),
                null, null, DataSource.WALLBOX_GOE, LOG_B_COST, ChargingType.AC,
                LOG_B_ODOMETER, null, LOG_B_SOC, null, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        assertNotNull(stats.avgConsumptionKwhPer100km(),
                "GOE Sub-Kette muss sichtbar sein — auch wenn TESLA_FLEET_IMPORT davor die Kette gebrochen hat");
        assertEquals(new BigDecimal("16.25"), stats.avgConsumptionKwhPer100km(),
                "Nur G1→G2 Trip berechenbar: 48.75 kWh / 300 km = 16.25 kWh/100km");
        assertEquals(new BigDecimal("300"), stats.totalDistanceKm(),
                "Nur die 300 km des G1→G2 Trips sind bekannt — T2→G1 hat keinen Odometer-Anker");
        assertEquals(0, stats.estimatedConsumptionCount(),
                "SoC-basiert — kein Fallback-Schätzer");
    }

    // ── Szenario 3: go-e Sub-Sessions MIT Odometer/SoC (User pflegt manuell) ───

    /**
     * Wenn der User Odometer und SoC auf den go-e Sub-Sessions pflegt,
     * ist canBeUsedAsLogX()=true und die Kette bleibt intakt.
     */
    @Test
    void goeSubSessions_withSocAndOdometer_doNotBreakConsumptionChain() {
        LocalDateTime base = LocalDateTime.now().minusDays(10).withHour(8);

        // Manual Log A
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("11.00"),
                180, null, LOG_A_ODOMETER, null, LOG_A_SOC,
                base, ChargingType.AC, null, null,
                false, null));

        // go-e Sub-Sessions MIT Odometer + SoC (User hat manuell befüllt)
        evLogRepository.save(EvLog.createFromInternal(
                carId, new BigDecimal("8.5"), 90, null,
                base.plusDays(1).withHour(10),
                null, null, DataSource.WALLBOX_GOE, new BigDecimal("2.38"), ChargingType.AC,
                LOG_A_ODOMETER, null, LOG_A_SOC, null, null)); // odometerKm + socAfter gesetzt

        // Manual Log B — direkter Vorgänger ist jetzt go-e mit SoC → canBeUsedAsLogX()=true
        evLogRepository.save(EvLog.createNew(carId, LOG_B_KWH, LOG_B_COST,
                90, null, LOG_B_ODOMETER, null, LOG_B_SOC,
                base.plusDays(2), ChargingType.AC, null, null,
                false, null));

        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, null);

        // SoC-basiert weil go-e Sub-Session canBeUsedAsLogX()=true hat
        assertEquals(0, stats.estimatedConsumptionCount(),
                "go-e Sub-Sessions mit Odometer+SoC unterbrechen die Kette NICHT");
        assertNotNull(stats.avgConsumptionKwhPer100km());
    }
}
