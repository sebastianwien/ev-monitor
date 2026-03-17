package com.evmonitor.application;

public record TickerItemDTO(
        String type,   // LEADER, STAT, FACT
        String text,
        String icon    // Heroicon name hint for frontend
) {
}
