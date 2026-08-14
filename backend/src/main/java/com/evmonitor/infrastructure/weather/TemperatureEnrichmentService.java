package com.evmonitor.infrastructure.weather;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.domain.weather.TemperatureSource;
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
                        evLogRepository.updateTemperature(logId, t, TemperatureSource.FORECAST);
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
    public void enrichTrip(UUID tripId,
                           String startGeohash, String endGeohash,
                           LocalDateTime startedAt, LocalDateTime endedAt) {
        boolean hasStart = isUsable(startGeohash) && startedAt != null;
        boolean hasEnd = isUsable(endGeohash) && endedAt != null;
        if (!hasStart && !hasEnd) {
            return;
        }
        try {
            Optional<Double> startTemp = hasStart ? lookup(startGeohash, startedAt) : Optional.empty();
            Optional<Double> endTemp = hasEnd ? lookup(endGeohash, endedAt) : Optional.empty();

            Double mean;
            if (startTemp.isPresent() && endTemp.isPresent()) {
                mean = (startTemp.get() + endTemp.get()) / 2.0;
            } else if (startTemp.isPresent()) {
                mean = startTemp.get();
            } else if (endTemp.isPresent()) {
                mean = endTemp.get();
            } else {
                log.debug("No temperature available for trip {} (startGeohash={}, endGeohash={})",
                        tripId, startGeohash, endGeohash);
                return;
            }

            BigDecimal tempBd = BigDecimal.valueOf(mean).setScale(1, RoundingMode.HALF_UP);
            evTripRepository.updateTemperature(tripId, tempBd, TemperatureSource.FORECAST);
            log.debug("Temperature enriched for trip {}: {}°C", tripId, tempBd);
        } catch (Exception e) {
            log.warn("Temperature enrichment failed for trip {}: {}", tripId, e.getMessage());
        }
    }

    private Optional<Double> lookup(String geohash, LocalDateTime at) {
        WGS84Point center = GeoHash.fromGeohashString(geohash).getBoundingBoxCenter();
        return temperatureService.getTemperature(center.getLatitude(), center.getLongitude(), at);
    }

    private static boolean isUsable(String geohash) {
        return geohash != null && !geohash.isBlank();
    }
}
