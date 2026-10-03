package com.evmonitor.application.voice;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Bias-Liste fuer die Transkription. Gemessen am 03.10.2026: ein Begriff mit Leerzeichen oder
 * Komma laesst den ganzen Request mit 400 scheitern, Unterstrich-Phrasen landen woertlich im Transkript.
 */
class BiasTermBuilderTest {

    /** Anbieter- und Tarifnamen aus Prod (letzte 180 Tage, Stand 03.10.2026). */
    static final List<String> PROD_CPO = List.of("EnBW", "Tesla", "IONITY", "Tesla Supercharger", "Kaufland",
            "Tesla Supercharger Graz, Austria - Webling", "Lidl", "EWE Go", "ALDI SE & Co. KG Ebersberg", "Allego",
            "ALDI Süd", "Electra", "Aral Pulse", "Energie Steiermark", "Fastned", "Vattenfall InCharge", "Shell Recharge",
            "ORLEN Charge", "LichtBlick", "Greenway", "Verbund", "Mer", "ChargePoint", "SWS", "TankE", "EDEKA");
    static final List<String> PROD_TARIFF = List.of("EnBW mobility+", "EWE Go", "IONITY Passport", "Zuhause (eigene Wallbox)",
            "Tesla", "Stadtwerke", "Aral Pulse", "ADAC e-Charge", "Elli (VW)", "Shell Recharge", "Vattenfall", "Electra",
            "Maingau Energie", "Vattenfall InCharge", "Octopus", "Plugsurfing", "Lidl", "DKV", "Allego", "E.ON Drive",
            "SMATRICS EnBW (AT)", "Kaufland", "Mercedes me Charge", "Electroverse", "Wien Energie", "Tiwag", "Fastned Gold",
            "eze.network");

    @Test
    void prodNamesYieldOnlySingleWordsTheApiAccepts() {
        List<String> terms = BiasTermBuilder.build(PROD_CPO, PROD_TARIFF, List.of());

        assertThat(terms).isNotEmpty().hasSizeLessThanOrEqualTo(BiasTermBuilder.MAX_TERMS);
        assertThat(terms).allSatisfy(t -> {
            assertThat(t).doesNotContain(" ", ",", "_");
            assertThat(t).matches("[\\p{L}\\p{N}.+\\-]{2,}");
        });
    }

    @Test
    void stripsBracketsLegalFormsAndEverythingAfterTheFirstComma() {
        List<String> terms = BiasTermBuilder.build(
                List.of("Tesla Supercharger Graz, Austria - Webling", "ALDI SE & Co. KG Ebersberg", "Elli (VW)"),
                List.of(), List.of());

        assertThat(terms).startsWith("Tesla", "Supercharger", "Graz", "ALDI", "Ebersberg", "Elli");
        assertThat(terms).doesNotContain("Austria", "Webling", "SE", "Co", "KG", "VW", "-");
    }

    @Test
    void keepsUmlautsAndAllowedPunctuation() {
        List<String> terms = BiasTermBuilder.build(List.of("ALDI Süd", "E.ON Drive"), List.of("EnBW mobility+", "ADAC e-Charge"), List.of());

        assertThat(terms).contains("Süd", "E.ON", "mobility+", "e-Charge");
    }

    @Test
    void dropsStopWordsAndDeduplicatesCaseInsensitive() {
        List<String> terms = BiasTermBuilder.build(List.of("EnBW", "Mercedes me Charge"), List.of("ENBW"), List.of("enbw"));

        assertThat(terms.stream().filter(t -> t.equalsIgnoreCase("enbw"))).containsExactly("EnBW");
        assertThat(terms).doesNotContain("me");
    }

    @Test
    void ordersCandidatesBeforeTariffsBeforeOperatorsBeforeDomainTerms() {
        List<String> terms = BiasTermBuilder.build(List.of("Fastned"), List.of("Octopus"), List.of("Allego"));

        assertThat(terms).startsWith("Fastned", "Octopus", "Allego", "kWh");
        assertThat(terms).contains("Tacho", "SoC", "Wallbox");
    }

    @Test
    void ignoresNullAndBlankNames() {
        List<String> names = new ArrayList<>();
        names.add(null);
        names.add("  ");
        names.add("Lidl");

        assertThat(BiasTermBuilder.build(names, null, List.of())).startsWith("Lidl");
    }

    @Test
    void capsAtMaxTerms() {
        List<String> many = IntStream.range(0, 150).mapToObj(i -> "Betreiber" + i).toList();

        List<String> terms = BiasTermBuilder.build(many, List.of(), List.of());

        assertThat(terms).hasSize(BiasTermBuilder.MAX_TERMS);
        assertThat(terms.get(0)).isEqualTo("Betreiber0");
    }
}
