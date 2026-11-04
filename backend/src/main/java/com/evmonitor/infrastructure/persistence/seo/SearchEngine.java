package com.evmonitor.infrastructure.persistence.seo;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Suchmaschinen für "Organic Search": wie sie im Verweis eines Kontos stehen (referrer_source, LIKE-Muster)
 * und wie Plausible sie als Quelle nennt: als Namen ("Yahoo!") oder als Domain ("startpage.com", "search.brave.com"),
 * Domains nach demselben Muster wie der Verweis.
 */
public enum SearchEngine {
    GOOGLE("%google.%"),
    BING("%bing.%"),
    DUCKDUCKGO("%duckduckgo.%"),
    ECOSIA("%ecosia.%"),
    YAHOO("%yahoo.%"),
    QWANT("%qwant.%"),
    STARTPAGE("%startpage.%"),
    BRAVE("%search.brave.%"),
    YANDEX("%yandex.%"),
    BAIDU("%baidu.%");

    private final String referrerLike;

    SearchEngine(String referrerLike) {
        this.referrerLike = referrerLike;
    }

    /** Festes Muster aus dem Enum, keine Nutzereingabe: darf direkt ins SQL. */
    String referrerLike() {
        return referrerLike;
    }

    public static Optional<SearchEngine> ofPlausibleSource(String source) {
        if (source == null) return Optional.empty();
        String s = source.trim().toLowerCase(Locale.ROOT);
        if (s.contains(".")) {
            return Arrays.stream(values()).filter(e -> s.contains(e.referrerLike.replace("%", ""))).findFirst();
        }
        String key = s.replace("!", "");
        return Arrays.stream(values()).filter(e -> e.name().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }
}
