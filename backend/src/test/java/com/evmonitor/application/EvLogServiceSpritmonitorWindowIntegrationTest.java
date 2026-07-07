package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End-Test für das rollierende Verbrauchsfenster bei Spritmonitor-Importen
 * ohne Kilometerstand (Szenario: Teilladungen wie bei Spritmonitor-Nutzern üblich).
 *
 * Erwartung in getLogsForCar():
 *   - Anker-Log (odometer + SoC + kWh) bekommt den Fensterwert als KWH_PRIMARY
 *   - Teilladungen ohne Odometer bekommen keinen eigenen Verbrauch, aber das Flag
 *     kwhCountedInNextConsumption=true (UI-Hinweis)
 *   - kein KWH_ESTIMATED-Fallback mehr für den Anker (vorher: kWh/Distanz ohne
 *     die Zwischenladungen → implausibel niedrige Ausreißer)
 */
class EvLogServiceSpritmonitorWindowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EvLogService evLogService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("sm-window-" + System.nanoTime() + "@test.com");
        userId = user.getId();
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3); // 75 kWh
        carId = car.getId();
    }

    /**
     * Aufbau (angelehnt an reale Spritmonitor-Daten):
     *   Log A  USER_LOGGED          odometer=10000, SoC=80
     *   SM1    SPRITMONITOR_IMPORT  12.0 kWh AC, kein Odometer, SoC=76
     *   SM2    SPRITMONITOR_IMPORT   8.0 kWh AC, kein Odometer, SoC=66
     *   Log B  SPRITMONITOR_IMPORT  52.5 kWh DC, odometer=10300, SoC=80
     *
     * Fenster A→B (Test-Profil: Ladeeffizienz 1.0, siehe application-test.yml):
     *   effektiv(B)=52.5, intermediate=12+8=20, SoC-Delta=0
     *   → 72.5 / 300 × 100 = 24.17 kWh/100km
     * Die Effizienz-Normalisierung der Zwischenladungen testet
     * ConsumptionCalculationServiceSpritmonitorWindowTest mit echten Faktoren.
     */
    @Test
    void spritmonitorPartialChargesWithoutOdometer_windowValueAtAnchor_flagAtIntermediates() {
        LocalDateTime base = LocalDateTime.now().minusDays(10).withHour(8);

        EvLog logA = evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("11.00"),
                180, null, 10000, null, new BigDecimal("80"),
                base, ChargingType.AC, null, null,
                false, null));

        EvLog sm1 = evLogRepository.save(EvLog.createNewWithSource(carId, new BigDecimal("12.0"), new BigDecimal("3.60"),
                196, null, null, new BigDecimal("3.8"), new BigDecimal("76"),
                base.plusDays(1), DataSource.SPRITMONITOR_IMPORT, ChargingType.AC, null));

        EvLog sm2 = evLogRepository.save(EvLog.createNewWithSource(carId, new BigDecimal("8.0"), new BigDecimal("2.40"),
                0, null, null, null, new BigDecimal("66"),
                base.plusDays(2), DataSource.SPRITMONITOR_IMPORT, ChargingType.AC, null));

        EvLog logB = evLogRepository.save(EvLog.createNewWithSource(carId, new BigDecimal("52.5"), new BigDecimal("16.63"),
                0, null, 10300, new BigDecimal("150"), new BigDecimal("80"),
                base.plusDays(3), DataSource.SPRITMONITOR_IMPORT, ChargingType.DC, null));

        List<EvLogResponse> logs = evLogService.getLogsForCar(carId, userId);

        EvLogResponse b = byId(logs, logB.getId());
        assertEquals(new BigDecimal("24.17"), b.consumptionKwhPer100km(),
                "Anker bekommt den Fensterwert inkl. Zwischenladungs-kWh");
        assertEquals("KWH_PRIMARY", b.consumptionQuality(), "kein Fallback-Schätzer am Anker");
        assertEquals(Boolean.FALSE, b.consumptionIsEstimated());
        assertEquals(300, b.distanceSinceLastChargeKm());

        EvLogResponse r1 = byId(logs, sm1.getId());
        EvLogResponse r2 = byId(logs, sm2.getId());
        assertNull(r1.consumptionKwhPer100km(), "Teilladung ohne km-Stand hat keinen eigenen Verbrauch");
        assertNull(r2.consumptionKwhPer100km());
        assertEquals(Boolean.TRUE, r1.kwhCountedInNextConsumption(),
                "UI-Hinweis: kWh sind im Fensterwert des Ankers enthalten");
        assertEquals(Boolean.TRUE, r2.kwhCountedInNextConsumption());

        EvLogResponse a = byId(logs, logA.getId());
        assertNotEquals(Boolean.TRUE, a.kwhCountedInNextConsumption(), "Anker-Logs bekommen kein Flag");
        assertTrue(logs.stream().noneMatch(l -> "KWH_ESTIMATED".equals(l.consumptionQuality())),
                "keine geschätzten Ausreißer-Werte mehr in diesem Szenario");
    }

    /** Ohne späteren Anker (Kette endet offen) gibt es keinen Fensterwert und kein Flag. */
    @Test
    void spritmonitorPartialCharge_withoutLaterAnchor_noFlag() {
        LocalDateTime base = LocalDateTime.now().minusDays(5).withHour(8);

        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("11.00"),
                180, null, 10000, null, new BigDecimal("80"),
                base, ChargingType.AC, null, null,
                false, null));

        EvLog sm1 = evLogRepository.save(EvLog.createNewWithSource(carId, new BigDecimal("12.0"), new BigDecimal("3.60"),
                0, null, null, null, new BigDecimal("76"),
                base.plusDays(1), DataSource.SPRITMONITOR_IMPORT, ChargingType.AC, null));

        List<EvLogResponse> logs = evLogService.getLogsForCar(carId, userId);

        EvLogResponse r1 = byId(logs, sm1.getId());
        assertNull(r1.consumptionKwhPer100km());
        assertNotEquals(Boolean.TRUE, r1.kwhCountedInNextConsumption(),
                "ohne erfolgreich berechnetes Fenster kein Hinweis-Flag");
    }

    private EvLogResponse byId(List<EvLogResponse> logs, UUID id) {
        return logs.stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }
}
