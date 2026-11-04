package com.evmonitor.application.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoPageTypeTest {

    @Test
    void classifiesPublicPagesByPath() {
        assertThat(SeoPageType.of("/")).isEqualTo(SeoPageType.HOME);
        assertThat(SeoPageType.of("/modelle")).isEqualTo(SeoPageType.MODEL_LIST);
        assertThat(SeoPageType.of("/modelle/vergleich")).isEqualTo(SeoPageType.MODEL_LIST);
        assertThat(SeoPageType.of("/modelle/Tesla")).isEqualTo(SeoPageType.BRAND);
        assertThat(SeoPageType.of("/modelle/Tesla/Model_3")).isEqualTo(SeoPageType.MODEL);
        assertThat(SeoPageType.of("/blog/verbrauch")).isEqualTo(SeoPageType.CONTENT);
        assertThat(SeoPageType.of("/stories/mein-id3")).isEqualTo(SeoPageType.CONTENT);
        assertThat(SeoPageType.of("/register")).isEqualTo(SeoPageType.CONVERSION);
        assertThat(SeoPageType.of("/preise")).isEqualTo(SeoPageType.CONVERSION);
        assertThat(SeoPageType.of("/impressum")).isEqualTo(SeoPageType.LEGAL);
        assertThat(SeoPageType.of("/en/models/Tesla")).isEqualTo(SeoPageType.OTHER);
        assertThat(SeoPageType.of("/irgendwas")).isEqualTo(SeoPageType.OTHER);
    }

    @Test
    void appAndTechnicalPagesAreNotLandingpages() {
        assertThat(SeoPageType.of("/dashboard")).isEqualTo(SeoPageType.APP);
        assertThat(SeoPageType.of("/cars/39c4/bewerten")).isEqualTo(SeoPageType.APP);
        assertThat(SeoPageType.of("/story")).isEqualTo(SeoPageType.APP);
        assertThat(SeoPageType.of("/stories/edit/12")).isEqualTo(SeoPageType.APP);
        assertThat(SeoPageType.of("/swagger-ui/index.html")).isEqualTo(SeoPageType.APP);
        assertThat(SeoPageType.of("/fahrzeug/abc")).isEqualTo(SeoPageType.APP);
        assertThat(SeoPageType.APP.isLandingpage()).isFalse();
        assertThat(SeoPageType.OTHER.isLandingpage()).isTrue();
        // Sprachvarianten: Präfix ab, dann dieselbe Einordnung
        assertThat(SeoPageType.of("/en/forgot-password")).isEqualTo(SeoPageType.APP);
        assertThat(SeoPageType.of("/en/login")).isEqualTo(SeoPageType.CONVERSION);
        assertThat(SeoPageType.of("/en")).isEqualTo(SeoPageType.HOME);
        // Präfix gilt nur ganz: /logsammlung wäre keine App-Seite
        assertThat(SeoPageType.of("/logsammlung")).isEqualTo(SeoPageType.OTHER);
    }
}
