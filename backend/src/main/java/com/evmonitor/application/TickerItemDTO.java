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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TickerItemDTO(
        String type,                 // LEADER, STAT, PERSONAL
        String messageKey,           // i18n key (e.g. "co2_saved")
        Map<String, String> params,  // interpolation params
        String variant               // icon/colour hint: leader | eco | money | energy | personal
) {

    /** A category leader item (trophy, yellow). */
    public static TickerItemDTO leader(Map<String, String> params) {
        return new TickerItemDTO("LEADER", "leader", params, "leader");
    }

    /** A community statistic. {@code variant} drives icon/colour on the frontend. */
    public static TickerItemDTO stat(String messageKey, String variant, Map<String, String> params) {
        return new TickerItemDTO("STAT", messageKey, params, variant);
    }

    /** An entry built from the requesting user's own data. Never cached across users. */
    public static TickerItemDTO personal(String messageKey, Map<String, String> params) {
        return new TickerItemDTO("PERSONAL", messageKey, params, "personal");
    }
}
