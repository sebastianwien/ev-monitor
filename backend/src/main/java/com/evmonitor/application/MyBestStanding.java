package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;

import java.math.BigDecimal;

/**
 * Bester Monatsrang des Nutzers über alle Kategorien. Gleiche Werte teilen sich den besseren Rang.
 *
 * @param gapToNext Abstand zum nächstbesseren Wert in der Einheit der Kategorie, null auf Platz 1
 */
public record MyBestStanding(LeaderboardCategory category, int rank, BigDecimal value, BigDecimal gapToNext) {
}
