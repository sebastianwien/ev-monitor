package com.evmonitor.application.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoPathTest {

    @Test
    void stripsHostQueryFragmentAndTrailingSlash() {
        assertThat(SeoPath.normalize("https://ev-monitor.net/modelle/?utm_source=x#top")).isEqualTo("/modelle");
        assertThat(SeoPath.normalize("https://ev-monitor.net/")).isEqualTo("/");
        assertThat(SeoPath.normalize("https://ev-monitor.net")).isEqualTo("/");
    }

    @Test
    void keepsCaseAndMapsSpacesToUnderscore() {
        assertThat(SeoPath.normalize("https://ev-monitor.net/modelle/Tesla/Model%203")).isEqualTo("/modelle/Tesla/Model_3");
        assertThat(SeoPath.normalize("https://ev-monitor.net/modelle/Tesla/Model 3/")).isEqualTo("/modelle/Tesla/Model_3");
        assertThat(SeoPath.normalize("https://ev-monitor.net/modelle/Tesla/Model_3")).isEqualTo("/modelle/Tesla/Model_3");
    }

    @Test
    void acceptsBarePathsAndCapsLength() {
        assertThat(SeoPath.normalize("/en/models/VW/ID.3")).isEqualTo("/en/models/VW/ID.3");
        assertThat(SeoPath.normalize("/" + "a".repeat(600))).hasSize(SeoPath.MAX_LENGTH);
        assertThat(SeoPath.normalize(null)).isEqualTo("/");
    }

    @Test
    void brandRegexMatchesVariants() {
        assertThat(SeoPath.isBrand("ev monitor")).isTrue();
        assertThat(SeoPath.isBrand("EV-Monitor Login")).isTrue();
        assertThat(SeoPath.isBrand("evmonitor")).isTrue();
        assertThat(SeoPath.isBrand("tesla model 3 verbrauch")).isFalse();
    }
}
