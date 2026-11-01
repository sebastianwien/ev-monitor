package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DriveConsumptionTest {

    private static final BigDecimal CAP = new BigDecimal("60");
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-15T10:00:00Z");

    private static DriveTripSample kwh(double km, Double kwh) {
        return new DriveTripSample(UUID.randomUUID(), UUID.randomUUID(), "TESLA_LIVE", AT,
                BigDecimal.valueOf(km), kwh == null ? null : BigDecimal.valueOf(kwh), null, null);
    }

    private static DriveTripSample soc(double km, double start, double end) {
        return new DriveTripSample(UUID.randomUUID(), UUID.randomUUID(), "TESSIE", AT,
                BigDecimal.valueOf(km), null, BigDecimal.valueOf(start), BigDecimal.valueOf(end));
    }

    @Test
    void summeKwhDurchSummeKm_ueberFahrtenMitEnergie() {
        DriveConsumption d = DriveConsumption.of(List.of(kwh(60, 12.0), kwh(60, 18.0)), CAP);
        // 30 kWh / 120 km
        assertEquals(0, new BigDecimal("25.00").compareTo(d.kwhPer100km()));
    }

    @Test
    void fahrtenUnter20km_zaehlenNicht() {
        List<DriveTripSample> trips = new ArrayList<>(List.of(kwh(60, 12.0), kwh(60, 12.0)));
        trips.add(kwh(19.9, 10.0)); // 50 kWh/100 km Kurzstrecke im Frost
        DriveConsumption d = DriveConsumption.of(trips, CAP);
        assertEquals(0, new BigDecimal("120").compareTo(d.kmTotal()));
        assertEquals(0, new BigDecimal("20.00").compareTo(d.kwhPer100km()));
    }

    @Test
    void socAbfallMitVorzeichen_mitEffektiverKapazitaet() {
        // 40 km mit 2 % Anstieg (Gefälle) ergibt -1,2 kWh: unter 5 kWh/100 km, fällt als Messfehler heraus.
        // 80 km mit 10 % Abfall zählen mit 6 kWh, dazu 10 kWh aus dem Fahrzeugwert
        DriveConsumption d = DriveConsumption.of(List.of(soc(80, 60, 50), soc(40, 50, 52), kwh(50, 10.0)), CAP);
        assertEquals(0, new BigDecimal("130").compareTo(d.kmWithEnergy()));
        assertEquals(0, new BigDecimal("16").compareTo(d.kwh()));
    }

    @Test
    void socAbfallKleinPositiv_bleibtDrin_auchWennEinzelnNiedrig() {
        // 10 % auf 100 km = 6 kWh/100 km: einzeln niedrig, aber plausibel, zählt in die Summe
        DriveConsumption d = DriveConsumption.of(List.of(soc(100, 60, 50), kwh(100, 20.0)), CAP);
        assertEquals(0, new BigDecimal("13.00").compareTo(d.kwhPer100km()));
    }

    @Test
    void fahrtAusserhalb5bis50_zaehltAlsOhneEnergiedaten() {
        DriveConsumption d = DriveConsumption.of(List.of(kwh(100, 20.0), kwh(25, 13.0)), CAP); // 52 kWh/100 km
        assertEquals(0, new BigDecimal("100").compareTo(d.kmWithEnergy()));
        assertEquals(0, new BigDecimal("125").compareTo(d.kmTotal()));
        assertEquals(0, new BigDecimal("0.80").compareTo(d.coverage()));
        assertNotNull(d.kwhPer100km());
    }

    @Test
    void grenzen5und50_geltenEinschliesslich() {
        DriveConsumption d = DriveConsumption.of(List.of(kwh(100, 5.0), kwh(100, 50.0)), CAP);
        assertEquals(0, new BigDecimal("200").compareTo(d.kmWithEnergy()));
    }

    @Test
    void unter100kmMitEnergie_keinWert() {
        DriveConsumption d = DriveConsumption.of(List.of(kwh(99, 20.0)), CAP);
        assertNull(d.kwhPer100km());
        assertEquals(0, new BigDecimal("100").compareTo(
                DriveConsumption.of(List.of(kwh(100, 20.0)), CAP).kmWithEnergy()));
        assertNotNull(DriveConsumption.of(List.of(kwh(100, 20.0)), CAP).kwhPer100km());
    }

    @Test
    void abdeckungUnter80Prozent_keinWert() {
        DriveConsumption d = DriveConsumption.of(List.of(kwh(100, 20.0), kwh(30, null)), CAP); // 100/130 = 77 %
        assertNull(d.kwhPer100km());
    }

    @Test
    void ergebnisAusserhalb10bis40_keinWert() {
        assertNull(DriveConsumption.of(List.of(kwh(100, 9.0), kwh(100, 9.0)), CAP).kwhPer100km());
        assertNull(DriveConsumption.of(List.of(kwh(100, 45.0), kwh(100, 41.0)), CAP).kwhPer100km());
        assertNotNull(DriveConsumption.of(List.of(kwh(100, 40.0)), CAP).kwhPer100km());
        assertNotNull(DriveConsumption.of(List.of(kwh(100, 10.0)), CAP).kwhPer100km());
    }

    @Test
    void ohneKapazitaet_socFahrtOhneEnergiedaten() {
        DriveConsumption d = DriveConsumption.of(List.of(soc(120, 60, 40)), null);
        assertEquals(0, BigDecimal.ZERO.compareTo(d.kmWithEnergy()));
        assertNull(d.kwhPer100km());
    }

    @Test
    void enyaqFall_allesUnsinn_keinWert() {
        // 1,3 kWh/100 km je Fahrt: jede Fahrt fällt heraus, Abdeckung 0
        DriveConsumption d = DriveConsumption.of(List.of(kwh(200, 2.6), kwh(150, 1.95)), CAP);
        assertNull(d.kwhPer100km());
    }

    @Test
    void plus_addiertSummen() {
        DriveConsumption a = DriveConsumption.of(List.of(kwh(100, 20.0)), CAP);
        DriveConsumption b = DriveConsumption.of(List.of(kwh(100, 30.0), kwh(50, null)), CAP);
        DriveConsumption sum = a.plus(b);
        assertEquals(0, new BigDecimal("50").compareTo(sum.kwh()));
        assertEquals(0, new BigDecimal("200").compareTo(sum.kmWithEnergy()));
        assertEquals(0, new BigDecimal("250").compareTo(sum.kmTotal()));
    }
}
