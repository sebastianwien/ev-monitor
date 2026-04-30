package com.evmonitor.infrastructure.weather;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.weather.TemperatureEnricher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter für {@link TemperatureEnricher}: reichert Charging-Logs mit
 * Umgebungstemperatur von Open-Meteo an. Läuft asynchron, damit User beim
 * Anlegen eines Logs nicht auf die Wetter-API warten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemperatureEnrichmentService implements TemperatureEnricher {

    private final TemperatureService temperatureService;
    private final EvLogRepository evLogRepository;
    private final EvTripRepository evTripRepository;

    /**
     * Fetches temperature for a newly created log and persists it asynchronously.
     * Called right after log creation - geohash and loggedAt are available at that point.
     */
    @Async
    @Override
    public void enrichLog(UUID logId, String geohash, LocalDateTime loggedAt) {
        if (geohash == null || geohash.isBlank()) {
            return;
        }
        try {
            WGS84Point center = GeoHash.fromGeohashString(geohash).getBoundingBoxCenter();
            Optional<Double> temp = temperatureService.getTemperature(center.getLatitude(), center.getLongitude(), loggedAt);
            temp.ifPresentOrElse(
                    t -> {
                        evLogRepository.updateTemperature(logId, t);
                        log.debug("Temperature enriched for log {}: {}°C", logId, t);
                    },
                    () -> log.debug("No temperature available for log {} (geohash={})", logId, geohash)
            );
        } catch (Exception e) {
            log.warn("Temperature enrichment failed for log {}: {}", logId, e.getMessage());
        }
    }

    @Async
    @Override
    public void enrichTrip(UUID tripId, String geohash, LocalDateTime startedAt) {
        if (geohash == null || geohash.isBlank()) {
            return;
        }
        try {
            WGS84Point center = GeoHash.fromGeohashString(geohash).getBoundingBoxCenter();
            Optional<Double> temp = temperatureService.getTemperature(center.getLatitude(), center.getLongitude(), startedAt);
            temp.ifPresentOrElse(
                    t -> {
                        BigDecimal tempBd = BigDecimal.valueOf(t).setScale(1, RoundingMode.HALF_UP);
                        evTripRepository.updateTemperature(tripId, tempBd);
                        log.debug("Temperature enriched for trip {}: {}°C", tripId, tempBd);
                    },
                    () -> log.debug("No temperature available for trip {} (geohash={})", tripId, geohash)
            );
        } catch (Exception e) {
            log.warn("Temperature enrichment failed for trip {}: {}", tripId, e.getMessage());
        }
    }
}
