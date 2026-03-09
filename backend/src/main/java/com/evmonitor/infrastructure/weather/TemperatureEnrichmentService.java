package com.evmonitor.infrastructure.weather;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.domain.EvLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Enriches charging logs with ambient temperature data from Open-Meteo.
 * All operations run asynchronously so users are never blocked waiting for the weather API.
 */
@Service
public class TemperatureEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(TemperatureEnrichmentService.class);

    private final TemperatureService temperatureService;
    private final EvLogRepository evLogRepository;

    public TemperatureEnrichmentService(TemperatureService temperatureService, EvLogRepository evLogRepository) {
        this.temperatureService = temperatureService;
        this.evLogRepository = evLogRepository;
    }

    /**
     * Fetches temperature for a newly created log and persists it asynchronously.
     * Called right after log creation — geohash and loggedAt are available at that point.
     */
    @Async
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
}
