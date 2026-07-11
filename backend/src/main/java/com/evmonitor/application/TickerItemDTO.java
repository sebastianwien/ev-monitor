package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * A single item in the community ticker.
 *
 * <p>Localisation boundary: the backend emits an i18n {@code messageKey} plus raw
 * {@code params} (numbers as plain strings, category as its enum name, month as 1-12).
 * The frontend renders the localised sentence via vue-i18n. This keeps all four
 * locales (de/en/nb/sv) in one place and avoids locale-dependent server caching.
 *
 * <p>NEWS items are the exception: their {@code text} comes from an external RSS feed
 * and is passed through verbatim (no messageKey/params).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TickerItemDTO(
        String type,                 // LEADER, STAT, NEWS
        String messageKey,           // i18n key for LEADER/STAT (e.g. "co2_saved"); null for NEWS
        Map<String, String> params,  // interpolation params; null for NEWS
        String variant,              // icon/colour hint: leader | eco | money | energy | news
        String text,                 // localised text ONLY for NEWS; null otherwise
        String url                   // article link ONLY for NEWS; null otherwise
) {

    /** A category leader item (trophy, yellow). */
    public static TickerItemDTO leader(Map<String, String> params) {
        return new TickerItemDTO("LEADER", "leader", params, "leader", null, null);
    }

    /** A community statistic. {@code variant} drives icon/colour on the frontend. */
    public static TickerItemDTO stat(String messageKey, String variant, Map<String, String> params) {
        return new TickerItemDTO("STAT", messageKey, params, variant, null, null);
    }

    /** An external news headline (passed through, no translation). */
    public static TickerItemDTO news(String title, String url) {
        return new TickerItemDTO("NEWS", null, null, "news", title, url);
    }
}
