package com.evmonitor.application.tariff.crawl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A value from the language model counts only if its quote stands on the page and the quote
 * contains the price, in any of the ways German pages write it. That catches invented prices
 * and prices copied from the wrong line.
 */
class EvidenceCheckTest {

    private static final String PAGE = """
            Niedrigpreis
            AC : 0,52 €/kWh DC : 0,62 €/kWh
            EnBW-Ladestationen 39 ct/kWh
            Subscription price € 0.49 (€ 0.49 per kWh in Germany)""";

    @Test
    void quoteOnThePage_withThePrice_counts() {
        assertThat(EvidenceCheck.supports(PAGE, "AC : 0,52 €/kWh DC : 0,62 €/kWh", new BigDecimal("0.62"))).isTrue();
        assertThat(EvidenceCheck.supports(PAGE, "EnBW-Ladestationen 39 ct/kWh", new BigDecimal("0.39"))).isTrue();
        assertThat(EvidenceCheck.supports(PAGE, "€ 0.49 per kWh in Germany", new BigDecimal("0.490"))).isTrue();
    }

    @Test
    void quoteAcrossALineBreak_andWithOtherSpacing_counts() {
        assertThat(EvidenceCheck.supports(PAGE, "Niedrigpreis AC: 0,52 €/kWh", new BigDecimal("0.52"))).isTrue();
    }

    @Test
    void quoteNotOnThePage_doesNotCount() {
        assertThat(EvidenceCheck.supports(PAGE, "Hochpreis AC/DC: 0,82 €/kWh", new BigDecimal("0.82"))).isFalse();
    }

    @Test
    void quoteWithoutThePrice_doesNotCount() {
        assertThat(EvidenceCheck.supports(PAGE, "AC : 0,52 €/kWh", new BigDecimal("0.62"))).isFalse();
        assertThat(EvidenceCheck.supports(PAGE, "39 ct/kWh", new BigDecimal("0.3"))).isFalse();
    }

    @Test
    void emptyQuote_doesNotCount() {
        assertThat(EvidenceCheck.supports(PAGE, " ", new BigDecimal("0.52"))).isFalse();
        assertThat(EvidenceCheck.supports(PAGE, null, new BigDecimal("0.52"))).isFalse();
    }

    @Test
    void operatorName_mustStandOnThePage() {
        assertThat(EvidenceCheck.mentions(PAGE, "EnBW")).isTrue();
        assertThat(EvidenceCheck.mentions(PAGE, "IONITY")).isFalse();
    }
}
