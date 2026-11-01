package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RangeBasisPolicyTest {

    private static final BigDecimal OWN_DRIVE = new BigDecimal("23.8");
    private static final BigDecimal MODEL = new BigDecimal("17.0");
    private static final BigDecimal CHARGES = new BigDecimal("49.5");

    @Test
    void eigeneFahrten_gehenVor() {
        RangeBasis b = RangeBasisPolicy.decide(OWN_DRIVE, MODEL, CHARGES, true);
        assertEquals(RangeBasis.Source.OWN_DRIVE, b.source());
        assertEquals(0, OWN_DRIVE.compareTo(b.consumptionKwhPer100km()));
    }

    @Test
    void ohneEigeneFahrten_modellschnitt_wennKleinerAlsLadungen() {
        RangeBasis b = RangeBasisPolicy.decide(null, MODEL, CHARGES, true);
        assertEquals(RangeBasis.Source.MODEL_DRIVE, b.source());
        assertEquals(0, MODEL.compareTo(b.consumptionKwhPer100km()));
    }

    @Test
    void ohneEigeneFahrten_ladungen_wennKleinerAlsModellschnitt() {
        BigDecimal lowCharges = new BigDecimal("16.5");
        RangeBasis b = RangeBasisPolicy.decide(null, MODEL, lowCharges, true);
        assertEquals(RangeBasis.Source.OWN_CHARGES, b.source());
        assertEquals(0, lowCharges.compareTo(b.consumptionKwhPer100km()));
    }

    @Test
    void gleichstand_nimmtModellschnitt() {
        assertEquals(RangeBasis.Source.MODEL_DRIVE, RangeBasisPolicy.decide(null, MODEL, MODEL, true).source());
    }

    @Test
    void modellschnittOhneLadungen() {
        assertEquals(RangeBasis.Source.MODEL_DRIVE, RangeBasisPolicy.decide(null, MODEL, null, true).source());
    }

    @Test
    void nurLadungen() {
        RangeBasis b = RangeBasisPolicy.decide(null, null, CHARGES, true);
        assertEquals(RangeBasis.Source.OWN_CHARGES, b.source());
    }

    @Test
    void nichts_null() {
        assertNull(RangeBasisPolicy.decide(null, null, null, true));
    }

    @Test
    void schalterAus_immerLadungenWieFrueher() {
        RangeBasis b = RangeBasisPolicy.decide(OWN_DRIVE, MODEL, CHARGES, false);
        assertEquals(RangeBasis.Source.OWN_CHARGES, b.source());
        assertEquals(0, CHARGES.compareTo(b.consumptionKwhPer100km()));
        assertNull(RangeBasisPolicy.decide(OWN_DRIVE, MODEL, null, false));
    }

    @Test
    void nullOderNullwertLadungen_zaehltNicht() {
        assertNull(RangeBasisPolicy.decide(null, null, BigDecimal.ZERO, true));
    }

    // --- Modellschnitt ---

    private static final BigDecimal CAP = new BigDecimal("60");

    private static ModelDriveAverage.CarDrive car(UUID userId, double kwhPer100km) {
        DriveConsumption d = DriveConsumption.of(List.of(new DriveTripSample(UUID.randomUUID(), userId, "TESLA_LIVE",
                java.time.OffsetDateTime.parse("2026-10-15T10:00:00Z"), new BigDecimal("200"),
                BigDecimal.valueOf(kwhPer100km * 2), null, null)), CAP);
        return new ModelDriveAverage.CarDrive(userId, d);
    }

    private static List<ModelDriveAverage.CarDrive> users(int n, double kwhPer100km) {
        List<ModelDriveAverage.CarDrive> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(car(UUID.randomUUID(), kwhPer100km));
        return list;
    }

    @Test
    void modellschnitt_abFuenfNutzern() {
        assertNull(ModelDriveAverage.of(users(4, 17), UUID.randomUUID()).consumptionKwhPer100km());
        ModelDriveAverage avg = ModelDriveAverage.of(users(5, 17), UUID.randomUUID());
        assertEquals(0, new BigDecimal("17.00").compareTo(avg.consumptionKwhPer100km()));
        assertEquals(5, avg.users());
    }

    @Test
    void modellschnitt_zaehltNutzerNichtAutos() {
        UUID one = UUID.randomUUID();
        List<ModelDriveAverage.CarDrive> cars = new ArrayList<>();
        for (int i = 0; i < 5; i++) cars.add(car(one, 17));
        assertNull(ModelDriveAverage.of(cars, UUID.randomUUID()).consumptionKwhPer100km());
    }

    @Test
    void modellschnitt_ohneEigeneAutos() {
        UUID me = UUID.randomUUID();
        List<ModelDriveAverage.CarDrive> cars = users(4, 17);
        cars.add(car(me, 30));
        cars.add(car(me, 30));
        assertNull(ModelDriveAverage.of(cars, me).consumptionKwhPer100km());
    }

    @Test
    void modellschnitt_nurAutosNachRegel1_streckengewichtet() {
        List<ModelDriveAverage.CarDrive> cars = users(5, 16);
        cars.add(car(UUID.randomUUID(), 45)); // Ergebnis über 40: zählt nicht
        ModelDriveAverage avg = ModelDriveAverage.of(cars, UUID.randomUUID());
        assertEquals(0, new BigDecimal("16.00").compareTo(avg.consumptionKwhPer100km()));
        assertEquals(5, avg.users());
    }
}
