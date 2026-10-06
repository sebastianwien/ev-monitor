package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rankings je Kategorie und Zeitfenster. Sie sind für alle Nutzer gleich und werden deshalb
 * global 15 Minuten gecacht (Bestenliste, eigene Standings, persönlicher Ticker). Eigene Bean,
 * weil {@code @Cacheable} bei Aufrufen innerhalb derselben Klasse nicht greift.
 */
@Component
@RequiredArgsConstructor
public class LeaderboardRankingProvider {

    private final LeaderboardQueryRepository queryRepository;

    @Cacheable(value = "leaderboardRanking", key = "#category.name() + ':' + #start + ':' + #endExclusive")
    public List<LeaderboardRankRow> getRanking(LeaderboardCategory category, LocalDateTime start, LocalDateTime endExclusive) {
        return getFreshRanking(category, start, endExclusive);
    }

    /** Ohne Cache - für die Monatsend-Prämien, die nicht auf einem bis zu 15 Minuten alten Stand laufen dürfen. */
    public List<LeaderboardRankRow> getFreshRanking(LeaderboardCategory category, LocalDateTime start, LocalDateTime endExclusive) {
        return switch (category) {
            case MONTHLY_KWH -> queryRepository.getKwhRanking(start, endExclusive);
            case MONTHLY_CHARGES -> queryRepository.getChargesRanking(start, endExclusive);
            case MONTHLY_DISTANCE -> queryRepository.getDistanceRanking(start, endExclusive);
            case MONTHLY_CHEAPEST -> queryRepository.getCheapestRanking(start, endExclusive);
            case MONTHLY_NIGHT_OWL -> queryRepository.getNightOwlRanking(start, endExclusive);
            case MONTHLY_ICE_CHARGER -> queryRepository.getIceChargerRanking(start, endExclusive);
            case MONTHLY_HEAT_CHARGER -> queryRepository.getHeatChargerRanking(start, endExclusive);
            case MONTHLY_POWER_CHARGER -> queryRepository.getPowerChargerRanking(start, endExclusive);
        };
    }
}
