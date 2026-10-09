package com.evmonitor.domain.rating;

import java.time.LocalDate;

/**
 * Was ein Fahrer mit einem Auto erlebt hat, gemessen an seinen nicht gelöschten Logs.
 * {@code kmDriven} ist höchster minus niedrigster km-Stand, also die eigene Strecke und nicht der Tacho.
 */
public record CarExperience(int logCount, int kmDriven, LocalDate firstLogDate) {

    public static final int MIN_LOGS = 5;
    public static final int MIN_KM = 300;

    public static final CarExperience NONE = new CarExperience(0, 0, null);

    /** Bewertbar ab fünf Ladevorgängen oder 300 gefahrenen Kilometern. */
    public boolean qualifiesForRating() {
        return logCount >= MIN_LOGS || kmDriven >= MIN_KM;
    }
}
