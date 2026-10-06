package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;

import java.math.BigDecimal;

/**
 * Bester Monatsrang des Nutzers über alle Kategorien, als Position wie in der Bestenliste.
 *
 * @param gapToNext Abstand zum Eintrag direkt davor in der Einheit der Kategorie, 0 bei Gleichstand,
 *                  null auf Platz 1
 */
public record MyBestStanding(LeaderboardCategory category, int rank, BigDecimal value, BigDecimal gapToNext) {
}
