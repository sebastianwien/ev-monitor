package com.evmonitor.application.tariff.crawl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Quote plus surrounding page text, so the admin sees where a price stands; stored in one text column. */
class EvidenceSnippetTest {

    private static final String PAGE = """
            Klassik & Extra-Tarif
            DC-Ladesäule mit über 50 kW Ladeleistung
            0,79 €/kWh
            0,62 €/kWh
            0,54 €/kWh
            Monatliche Grundgebühr
            2,99 €""";

    @Test
    void findsTheQuote_likeTheEvidenceCheck_andKeepsTheExactPageText() {
        EvidenceSnippet s = EvidenceSnippet.find(PAGE, "0,62 € / KWH", 40, null);

        assertThat(s.match()).isEqualTo("0,62 €/kWh");
        assertThat(s.before()).endsWith("Ladeleistung · 0,79 €/kWh · ").doesNotContain("\n");
        assertThat(s.after()).startsWith(" · 0,54 €/kWh");
    }

    @Test
    void contextIsCutAtWordBoundaries_andMarkedWhenShortened() {
        EvidenceSnippet s = EvidenceSnippet.find(PAGE, "0,62 €/kWh", 20, null);

        assertThat(s.before()).startsWith("…").doesNotContain("Klassik");
        assertThat(s.after()).endsWith("…");
    }

    @Test
    void quoteNotOnThePage_staysAsItIs_withoutContext() {
        EvidenceSnippet s = EvidenceSnippet.find(PAGE, "0,99 €/kWh", 40, null);

        assertThat(s.match()).isEqualTo("0,99 €/kWh");
        assertThat(s.before()).isEmpty();
        assertThat(s.after()).isEmpty();
    }

    @Test
    void samePriceTwiceOnThePage_takesThePlaceNearTheTariffName() {
        String page = "Klassik-Tarif\nAC 0,47 €/kWh\nDC 0,62 €/kWh\n" + "Hinweise zur Abrechnung. ".repeat(8)
                + "\nExtra-Tarif\nAC 0,41 €/kWh\nDC 0,62 €/kWh\nGrundgebühr 2,99 €";

        assertThat(EvidenceSnippet.find(page, "DC 0,62 €/kWh", 60, "Extra").before()).contains("Extra-Tarif");
        assertThat(EvidenceSnippet.find(page, "DC 0,62 €/kWh", 60, "Klassik").before()).contains("Klassik-Tarif");
        assertThat(EvidenceSnippet.find(page, "DC 0,62 €/kWh", 60, null).before()).as("without a name: first place").contains("Klassik-Tarif");
    }

    @Test
    void separatorInTheQuote_isDropped_soTheStoredValueStaysReadable() {
        EvidenceSnippet s = EvidenceSnippet.find(PAGE, "0,99\u001F €", 40, null);

        assertThat(EvidenceSnippet.decode(s.encode())).isEqualTo(new EvidenceSnippet("", "0,99 €", ""));
    }

    @Test
    void encodeDecode_roundTrip_andPlainOldEvidence() {
        EvidenceSnippet s = EvidenceSnippet.find(PAGE, "0,62 €/kWh", 40, null);

        assertThat(EvidenceSnippet.decode(s.encode())).isEqualTo(s);
        assertThat(EvidenceSnippet.decode("go-kwh-price 0.68 €/kWh")).isEqualTo(new EvidenceSnippet("", "go-kwh-price 0.68 €/kWh", ""));
        assertThat(EvidenceSnippet.decode(null)).isNull();
    }
}
