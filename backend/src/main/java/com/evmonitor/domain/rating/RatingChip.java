package com.evmonitor.domain.rating;

/** Feste Auswahl für Stärken und Schwächen, je Liste höchstens {@link #MAX_PER_LIST}. */
public enum RatingChip {
    EFFICIENCY,
    CHARGING_SPEED,
    SPACE,
    DRIVING_FUN,
    PRICE,
    SOFTWARE,
    BUILD_QUALITY,
    DESIGN,
    ASSISTANCE,
    SERVICE;

    public static final int MAX_PER_LIST = 3;
}
