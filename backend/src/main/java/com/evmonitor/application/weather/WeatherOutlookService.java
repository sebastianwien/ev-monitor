package com.evmonitor.application.weather;

import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Wetter-Ausblick fuer die Story: wird es am Heimladeort in den naechsten zwei Wochen kaelter
 * als in der letzten Woche? Anker ist der haeufigste Heimladeort des Nutzers, ohne ihn gibt es
 * keinen Ausblick. Ein Wetterausfall ist kein Fehler fuer den Nutzer, nur kein Ausblick.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherOutlookService {

    private final ChargingSavingsQueryRepository places;
    private final WeatherOutlookCache cache;

    public Optional<WeatherOutlook> outlook(UUID userId) {
        String geohash = places.homeGeohash(userId);
        if (geohash == null || geohash.isBlank()) return Optional.empty();
        try {
            return cache.forGeohash(geohash);
        } catch (Exception e) {
            log.warn("Wetter-Ausblick fuer Zelle {} nicht verfuegbar: {}", geohash, e.getMessage());
            return Optional.empty();
        }
    }
}
