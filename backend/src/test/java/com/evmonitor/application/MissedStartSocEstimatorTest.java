package com.evmonitor.application;

import com.evmonitor.application.MissedStartSocEstimator.Charge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R15-Herleitung: wenn Smartcar den Ladestart verpasst hat, ist der Start-SoC unbekannt.
 * Rueckrechnung ueber den kWh-Bedarf pro SoC-Prozentpunkt aus den vollstaendigen Ladungen
 * desselben Autos, Fallback Nominalkapazitaet.
 */
class MissedStartSocEstimatorTest {

    private static Charge charge(String socBefore, String socAfter, String kwh) {
        return new Charge(new BigDecimal(socBefore), new BigDecimal(socAfter), new BigDecimal(kwh));
    }

    @Test
    void derivesStartFromCarsOwnCleanCharges() {
        // apog/ID.7: eigene Ladungen liegen bei ~0.85 kWh/Punkt.
        List<Charge> clean = List.of(
                charge("20", "80", "51"),   // 0.85
                charge("30", "80", "42.5"), // 0.85
                charge("14", "80", "56.1"));// 0.85
        BigDecimal socStart = MissedStartSocEstimator.estimateSocStart(
                new BigDecimal("80"), new BigDecimal("53.5"), clean, new BigDecimal("86"));

        // 80 - round(53.5 / 0.85) = 80 - 63 = 17
        assertThat(socStart).isEqualByComparingTo("17");
    }

    @Test
    void fallsBackToNominalCapacityWhenNoCleanCharges() {
        BigDecimal socStart = MissedStartSocEstimator.estimateSocStart(
                new BigDecimal("80"), new BigDecimal("53.5"), List.of(), new BigDecimal("86"));

        // 80 - round(53.5 / 0.86) = 80 - 62 = 18
        assertThat(socStart).isEqualByComparingTo("18");
    }

    @Test
    void ignoresImplausibleChargesAndUsesNominalFallback() {
        // Ladungen mit unphysikalischem kWh/Punkt (z.B. selbst missed-start) werden verworfen.
        List<Charge> polluted = List.of(charge("76", "80", "53.5")); // 13.4 kWh/Punkt - raus
        BigDecimal socStart = MissedStartSocEstimator.estimateSocStart(
                new BigDecimal("80"), new BigDecimal("53.5"), polluted, new BigDecimal("86"));

        assertThat(socStart).isEqualByComparingTo("18"); // Fallback Nominal, nicht verseucht
    }

    @Test
    void clampsToZeroWhenEnergyExceedsFullBattery() {
        BigDecimal socStart = MissedStartSocEstimator.estimateSocStart(
                new BigDecimal("80"), new BigDecimal("200"), List.of(), new BigDecimal("86"));

        assertThat(socStart).isEqualByComparingTo("0");
    }

    @Test
    void returnsNullWithoutAnyBasis() {
        assertThat(MissedStartSocEstimator.estimateSocStart(
                new BigDecimal("80"), new BigDecimal("53.5"), List.of(), null)).isNull();
    }

    @Test
    void returnsNullWhenInputsMissing() {
        assertThat(MissedStartSocEstimator.estimateSocStart(
                null, new BigDecimal("53.5"), List.of(), new BigDecimal("86"))).isNull();
        assertThat(MissedStartSocEstimator.estimateSocStart(
                new BigDecimal("80"), null, List.of(), new BigDecimal("86"))).isNull();
    }
}
