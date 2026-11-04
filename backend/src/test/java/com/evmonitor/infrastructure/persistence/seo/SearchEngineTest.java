package com.evmonitor.infrastructure.persistence.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchEngineTest {

    @Test
    void mapsPlausibleSourceNamesToEngines() {
        assertThat(SearchEngine.ofPlausibleSource("Google")).contains(SearchEngine.GOOGLE);
        assertThat(SearchEngine.ofPlausibleSource("Bing")).contains(SearchEngine.BING);
        assertThat(SearchEngine.ofPlausibleSource("DuckDuckGo")).contains(SearchEngine.DUCKDUCKGO);
        assertThat(SearchEngine.ofPlausibleSource("Yahoo!")).contains(SearchEngine.YAHOO);
        assertThat(SearchEngine.ofPlausibleSource("startpage.com")).contains(SearchEngine.STARTPAGE);
        assertThat(SearchEngine.ofPlausibleSource("search.brave.com")).contains(SearchEngine.BRAVE);
        assertThat(SearchEngine.ofPlausibleSource("ecosia.org")).contains(SearchEngine.ECOSIA);
    }

    @Test
    void otherSourcesAreNoSearchEngine() {
        assertThat(SearchEngine.ofPlausibleSource("Google Gemini")).isEmpty();
        assertThat(SearchEngine.ofPlausibleSource("Direct / None")).isEmpty();
        assertThat(SearchEngine.ofPlausibleSource("vw-forum")).isEmpty();
        assertThat(SearchEngine.ofPlausibleSource("brave.com")).isEmpty();
        assertThat(SearchEngine.ofPlausibleSource(null)).isEmpty();
    }
}
