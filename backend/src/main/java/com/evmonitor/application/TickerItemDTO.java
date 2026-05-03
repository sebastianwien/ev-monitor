package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TickerItemDTO(
        String type,   // LEADER, STAT, FACT, NEWS
        String text,
        String icon,   // Heroicon name hint for frontend
        String url     // optional: article link for NEWS items
) {
    /** Convenience constructor for items without a URL (backwards compatible). */
    public TickerItemDTO(String type, String text, String icon) {
        this(type, text, icon, null);
    }
}
