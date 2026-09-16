package com.evmonitor.application.imports.xpeng;

import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.xpeng.DetectedChargingSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mapping-Tests fuer {@link XpengImportService#toSessionEntry(DetectedChargingSession)}.
 *
 * <p>Hintergrund: {@code ldcu_chrgpwr} ist die AC-Leistung am Onboard-Charger -
 * also brutto (Wallbox-aequivalent), <b>nicht</b> die in den Pack gehende Netto-Energie.
 * Die echte Netto-Energie kommt aus {@code -(bms_battvolt x bms_battcurr)}.
 * Vor dem Fix wurde {@code chrgpwr} faelschlich als {@code AT_VEHICLE} gespeichert.
 */
class XpengSessionEntryMappingTest {

    private static final Instant START = Instant.parse("2026-05-01T12:00:00Z");
    private static final Instant END = Instant.parse("2026-05-01T13:30:00Z");

    @Test
    void writesStartAsUtcOffsetTimestamp() {
        // Regression: der Sessionstart ist ein absoluter Zeitpunkt. Der Public-API-Sink
        // speichert UTC - eine Wanduhrzeit mit UTC-Label fuehrte zu +1h/+2h Versatz.
        Instant start = Instant.parse("2026-08-02T12:00:00Z"); // = 14:00 CEST
        DetectedChargingSession s = new DetectedChargingSession(
                start, start.plusSeconds(3600),
                new BigDecimal("20"), new BigDecimal("60"),
                new BigDecimal("11.00"), null, new BigDecimal("11.00"),
                new BigDecimal("13508"), "AC", null);

        PublicApiSessionRequest.SessionEntry e = XpengImportService.toSessionEntry(s);

        assertEquals("2026-08-02T12:00Z", e.date());
        assertEquals(60, e.durationMin());
    }

    @Test
    void writesGrossKwhAsKwhAndMarksAsAtCharger() {
        DetectedChargingSession s = session(new BigDecimal("17.50"), new BigDecimal("16.50"));

        PublicApiSessionRequest.SessionEntry e = XpengImportService.toSessionEntry(s);

        assertEquals(17.50, e.kwh(), 0.0001, "brutto (chrgpwr) gehoert ins kwh-Feld");
        assertEquals(16.50, e.kwhAtVehicle(), 0.0001, "netto (U*I) gehoert ins kwh_at_vehicle-Feld");
        assertEquals("AT_CHARGER", e.measurementType(),
                "XPeng chrgpwr ist AC-side, also AT_CHARGER - nicht AT_VEHICLE");
    }

    @Test
    void leavesKwhAtVehicleNullWhenPackEnergyUnavailable() {
        // Fallback: keine validen U/I-Samples -> Detector liefert kwhAtVehicle=null.
        // Mapper darf den null nicht in 0.0 verwandeln, sonst denkt die Public-API
        // "Netto = 0 kWh" und SoH-Berechnungen kollabieren.
        DetectedChargingSession s = session(new BigDecimal("11.00"), null);

        PublicApiSessionRequest.SessionEntry e = XpengImportService.toSessionEntry(s);

        assertEquals(11.00, e.kwh(), 0.0001);
        assertNull(e.kwhAtVehicle(), "null muss propagiert werden, nicht zu 0.0 mutiert");
        assertEquals("AT_CHARGER", e.measurementType());
    }

    @Test
    void preservesChargingTypeAndMaxPower() {
        DetectedChargingSession s = new DetectedChargingSession(
                START, END,
                new BigDecimal("20"), new BigDecimal("80"),
                new BigDecimal("50.00"), new BigDecimal("47.50"),
                new BigDecimal("150.00"),
                new BigDecimal("13508"),
                "DC", null);

        PublicApiSessionRequest.SessionEntry e = XpengImportService.toSessionEntry(s);

        assertEquals("DC", e.chargingType());
        assertEquals(150.00, e.maxChargingPowerKw(), 0.0001);
    }

    private DetectedChargingSession session(BigDecimal kwhGross, BigDecimal kwhNet) {
        return new DetectedChargingSession(
                START, END,
                new BigDecimal("20"), new BigDecimal("60"),
                kwhGross, kwhNet,
                new BigDecimal("11.00"),
                new BigDecimal("13508"),
                "AC", null);
    }
}
