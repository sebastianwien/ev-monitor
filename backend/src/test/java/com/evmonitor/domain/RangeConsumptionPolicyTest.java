package com.evmonitor.domain;

import com.evmonitor.domain.RangeConsumptionPolicy.PeerFallback;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Vier Stufen der Reichweiten-Basis, Variante vor Modell, Grenzen der Schätzung, eine Reichweitenformel. */
class RangeConsumptionPolicyTest {

    private static final BigDecimal OWN_DRIVE = new BigDecimal("23.8");
    private static final BigDecimal CHARGES = new BigDecimal("49.5");
    private static final BigDecimal WLTP = new BigDecimal("15.0");
    private static final PeerDrive PEER_VARIANT = new PeerDrive(new BigDecimal("17.0"), 2, PeerDrive.Scope.VARIANT);
    private static final PeerDrive PEER_MODEL = new PeerDrive(new BigDecimal("18.0"), 4, PeerDrive.Scope.MODEL);

    // --- Stufen ---

    @Test
    void stufe1_eigeneFahrtenGehenVor() {
        RangeBasis b = RangeConsumptionPolicy.decide(OWN_DRIVE, PEER_VARIANT, CHARGES, new PeerFallback(new BigDecimal("16.0"), WLTP, new BigDecimal("1.21")));
        assertEquals(RangeBasis.Source.OWN_DRIVE, b.source());
        assertNull(b.scope());
        assertEquals(0, OWN_DRIVE.compareTo(b.consumptionKwhPer100km()));
    }

    @Test
    void stufe2_fahrtenAndererFahrer_auchWennLadungenKleinerSind() {
        RangeBasis b = RangeConsumptionPolicy.decide(null, PEER_VARIANT, new BigDecimal("16.5"), new PeerFallback(new BigDecimal("16.0"), WLTP, new BigDecimal("1.21")));
        assertEquals(RangeBasis.Source.PEER_DRIVE, b.source());
        assertEquals(PeerDrive.Scope.VARIANT, b.scope());
        assertEquals(0, new BigDecimal("17.0").compareTo(b.consumptionKwhPer100km()));
    }

    @Test
    void stufe3_eigeneLadungen_vorDenLadungenAnderer() {
        RangeBasis b = RangeConsumptionPolicy.decide(null, null, CHARGES, new PeerFallback(new BigDecimal("16.0"), WLTP, new BigDecimal("1.21")));
        assertEquals(RangeBasis.Source.OWN_CHARGES, b.source());
        assertEquals(0, CHARGES.compareTo(b.consumptionKwhPer100km()));
    }

    @Test
    void stufe4_ladungenAnderer_ohneDeckel_sonstWltpSchaetzung() {
        // Ladungen anderer 26 über WLTP × 1,21 = 18,15: trotzdem die Messung
        RangeBasis charges = RangeConsumptionPolicy.decide(null, null, null, new PeerFallback(new BigDecimal("26.0"), WLTP, new BigDecimal("1.21")));
        assertEquals(RangeBasis.Source.PEER_CHARGES, charges.source());
        assertEquals(0, new BigDecimal("26.0").compareTo(charges.consumptionKwhPer100km()));
        assertTrue(charges.source().isMeasured());

        RangeBasis estimate = RangeConsumptionPolicy.decide(null, null, null, new PeerFallback(null, WLTP, new BigDecimal("1.21")));
        assertEquals(RangeBasis.Source.WLTP_ESTIMATE, estimate.source());
        assertEquals(0, new BigDecimal("18.15").compareTo(estimate.consumptionKwhPer100km()));
        assertFalse(estimate.source().isMeasured());
    }

    @Test
    void nullwertLadungen_zaehlenNicht_undOhneAllesNull() {
        assertEquals(RangeBasis.Source.PEER_CHARGES,
                RangeConsumptionPolicy.decide(null, null, BigDecimal.ZERO, new PeerFallback(new BigDecimal("18.2"), null, null)).source());
        assertNull(RangeConsumptionPolicy.decide(null, null, null, PeerFallback.NONE));
        assertNull(RangeConsumptionPolicy.decide(null, PeerDrive.NONE, BigDecimal.ZERO, new PeerFallback(BigDecimal.ZERO, WLTP, null)));
    }

    // --- Öffentliche Seiten: kein Fragender, kein Deckel ---

    @Test
    void oeffentlich_fahrtenAnderer_sonstIhreLadungenOhneDeckel_sonstWltpSchaetzung() {
        RangeBasis drive = RangeConsumptionPolicy.decidePublic(PEER_VARIANT, new BigDecimal("16.0"), WLTP, new BigDecimal("1.21"));
        assertEquals(RangeBasis.Source.PEER_DRIVE, drive.source());
        assertEquals(2, drive.users());

        // Ladungen 26 über WLTP × 1,21 = 18,15: trotzdem die Messung
        RangeBasis charges = RangeConsumptionPolicy.decidePublic(PeerDrive.NONE, new BigDecimal("26.0"), WLTP, new BigDecimal("1.21"));
        assertEquals(RangeBasis.Source.PEER_CHARGES, charges.source());
        assertEquals(0, new BigDecimal("26.0").compareTo(charges.consumptionKwhPer100km()));
        assertTrue(charges.source().isMeasured());

        RangeBasis estimate = RangeConsumptionPolicy.decidePublic(null, null, WLTP, new BigDecimal("1.21"));
        assertEquals(RangeBasis.Source.WLTP_ESTIMATE, estimate.source());
        assertEquals(0, new BigDecimal("18.15").compareTo(estimate.consumptionKwhPer100km()));
        assertFalse(estimate.source().isMeasured());

        assertNull(RangeConsumptionPolicy.decidePublic(null, BigDecimal.ZERO, null, new BigDecimal("1.21")));
    }

    // --- WLTP-Schätzung: Herstellerangabe mal Flottenfaktor, Grenzen 10 bis 40 ---

    @Test
    void wltpSchaetzung_nurMitBeidemUndBegrenzt() {
        assertEquals(0, new BigDecimal("18.15").compareTo(RangeConsumptionPolicy.wltpEstimate(WLTP, new BigDecimal("1.21"))));
        assertNull(RangeConsumptionPolicy.wltpEstimate(WLTP, null));
        assertNull(RangeConsumptionPolicy.wltpEstimate(null, new BigDecimal("1.21")));
        assertEquals(0, new BigDecimal("10").compareTo(RangeConsumptionPolicy.wltpEstimate(new BigDecimal("4.0"), BigDecimal.ONE)));
        assertEquals(0, new BigDecimal("40").compareTo(RangeConsumptionPolicy.wltpEstimate(new BigDecimal("50.0"), new BigDecimal("1.21"))));
    }

    // --- Variante vor Modell, je ab 2 anderen Fahrern ---

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
    void modellschnitt_abZweiAnderenNutzern() {
        assertNull(ModelDriveAverage.of(users(1, 17), UUID.randomUUID()).consumptionKwhPer100km());
        ModelDriveAverage avg = ModelDriveAverage.of(users(2, 17), UUID.randomUUID());
        assertEquals(0, new BigDecimal("17.00").compareTo(avg.consumptionKwhPer100km()));
        assertEquals(2, avg.users());
    }

    @Test
    void modellschnitt_derFragendeZaehltNicht() {
        UUID me = UUID.randomUUID();
        List<ModelDriveAverage.CarDrive> cars = users(1, 17);
        cars.add(car(me, 30));
        cars.add(car(me, 30));
        assertNull(ModelDriveAverage.of(cars, me).consumptionKwhPer100km());
    }

    @Test
    void modellschnitt_zaehltNutzerNichtAutos_undNurVerlaesslicheAutos() {
        UUID one = UUID.randomUUID();
        List<ModelDriveAverage.CarDrive> cars = new ArrayList<>();
        for (int i = 0; i < 3; i++) cars.add(car(one, 17));
        assertNull(ModelDriveAverage.of(cars, UUID.randomUUID()).consumptionKwhPer100km());

        List<ModelDriveAverage.CarDrive> mixed = users(2, 16);
        mixed.add(car(UUID.randomUUID(), 45)); // über 40: zählt nicht
        ModelDriveAverage avg = ModelDriveAverage.of(mixed, UUID.randomUUID());
        assertEquals(0, new BigDecimal("16.00").compareTo(avg.consumptionKwhPer100km()));
        assertEquals(2, avg.users());
    }

    @Test
    void peerDrive_varianteVorModell_sonstModell_sonstNull() {
        ModelDriveAverage variant = ModelDriveAverage.of(users(2, 17), null);
        ModelDriveAverage model = ModelDriveAverage.of(users(5, 19), null);
        PeerDrive p = PeerDrive.of(variant, model);
        assertEquals(PeerDrive.Scope.VARIANT, p.scope());
        assertEquals(0, new BigDecimal("17.00").compareTo(p.consumptionKwhPer100km()));
        assertEquals(2, p.users());

        PeerDrive m = PeerDrive.of(ModelDriveAverage.of(users(1, 17), null), model);
        assertEquals(PeerDrive.Scope.MODEL, m.scope());
        assertEquals(5, m.users());

        PeerDrive none = PeerDrive.of(ModelDriveAverage.NONE, ModelDriveAverage.of(users(1, 19), null));
        assertNull(none.consumptionKwhPer100km());
        assertEquals(PeerDrive.Scope.MODEL, none.scope());
        assertNull(RangeConsumptionPolicy.decide(null, none, null, PeerFallback.NONE));
    }

    // --- Reichweite: 0,9 × Kapazität × 100 / Verbrauch ---

    @Test
    void rangeMath_fenster100Auf10() {
        assertEquals(300, RangeMath.rangeKm(new BigDecimal("60"), new BigDecimal("18")));
        assertEquals(227, RangeMath.rangeKm(new BigDecimal("67.7"), new BigDecimal("26.9"))); // 226,5 aufgerundet
        assertNull(RangeMath.rangeKm(null, new BigDecimal("18")));
        assertNull(RangeMath.rangeKm(new BigDecimal("60"), null));
        assertNull(RangeMath.rangeKm(new BigDecimal("60"), BigDecimal.ZERO));
        assertNull(RangeMath.rangeKm(BigDecimal.ZERO, new BigDecimal("18")));
    }

    @Test
    void rangeEstimate_ausBasisUndKapazitaet() {
        RangeBasis basis = RangeConsumptionPolicy.decide(null, PEER_MODEL, null, PeerFallback.NONE);
        RangeEstimate e = RangeEstimate.of(new BigDecimal("60"), basis);
        assertEquals(300, e.rangeKm());
        assertEquals(RangeBasis.Source.PEER_DRIVE, e.source());
        assertEquals(PeerDrive.Scope.MODEL, e.scope());
        assertEquals(0, new BigDecimal("18.0").compareTo(e.consumptionKwhPer100km()));

        assertNull(RangeEstimate.of(new BigDecimal("60"), null));
        assertNull(RangeEstimate.of(null, basis).rangeKm());
    }
}
