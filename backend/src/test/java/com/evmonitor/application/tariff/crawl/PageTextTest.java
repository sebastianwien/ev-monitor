package com.evmonitor.application.tariff.crawl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page text as a reader sees it, one block per line, without scripts and styles. Hidden CMS
 * lists stay (IONITY keeps its per-country prices there). Price images count by their alt text,
 * so a new tariff tile changes the fingerprint even when the text stays the same.
 */
class PageTextTest {

    @Test
    void blocks_becomeLines_scriptsAndStylesDisappear() {
        PageText page = PageText.of("""
                <html><head><title>Tarife</title><style>.a{}</style></head><body>
                <script>var price = "0,10";</script>
                <h2>Ladetarif   L</h2><p>DC: <b>0,39&nbsp;€/kWh</b></p>
                <div aria-hidden="true"><div>Deutschland</div><div>0.42 €/kWh</div></div>
                </body></html>""", "https://example.com/tarife");

        assertThat(page.text()).isEqualTo("Ladetarif L\nDC: 0,39 €/kWh\nDeutschland\n0.42 €/kWh");
    }

    @Test
    void priceImages_areListedWithAltAndAbsoluteSource() {
        PageText page = PageText.of("""
                <body><img alt="Tarifkachel Ladetarif S, Stand: 01.12.2025" src="/img/s.svg">
                <img alt="Logo" src="/logo.svg"><img src="/x.png"></body>""", "https://www.enbw.com/ladetarife");

        assertThat(page.priceImages()).containsExactly("Tarifkachel Ladetarif S, Stand: 01.12.2025 | https://www.enbw.com/img/s.svg");
    }

    @Test
    void fingerprint_changesWithTextOrPriceImage_notWithWhitespace() {
        String a = PageText.of("<p>0,39 €/kWh</p>", "https://x.de").fingerprint();

        assertThat(PageText.of("<p>0,39   €/kWh</p>\n", "https://x.de").fingerprint()).isEqualTo(a);
        assertThat(PageText.of("<p>0,42 €/kWh</p>", "https://x.de").fingerprint()).isNotEqualTo(a);
        assertThat(PageText.of("<p>0,39 €/kWh</p><img alt='Tarif Stand 2026' src='t.svg'>", "https://x.de").fingerprint()).isNotEqualTo(a);
    }
}
