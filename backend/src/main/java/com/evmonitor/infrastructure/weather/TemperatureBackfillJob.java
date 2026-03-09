package com.evmonitor.infrastructure.weather;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-time backfill job to populate temperature_celsius for existing charging logs.
 * Triggered manually via POST /api/admin/backfill-temperature (requires ADMIN role).
 *
 * Rate limiting: 50ms sleep between requests to be a good Open-Meteo citizen.
 */
@Component
public class TemperatureBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(TemperatureBackfillJob.class);
    private static final long SLEEP_MS_BETWEEN_REQUESTS = 50;

    private final EvLogRepository evLogRepository;
    private final TemperatureService temperatureService;

    public TemperatureBackfillJob(EvLogRepository evLogRepository, TemperatureService temperatureService) {
        this.evLogRepository = evLogRepository;
        this.temperatureService = temperatureService;
    }

    /**
     * Fetches and persists temperatures for all logs that have a geohash but no temperature yet.
     * Returns a summary string suitable for logging.
     */
    public String run() {
        List<EvLog> candidates = evLogRepository.findAllWithGeohashAndNoTemperature();
        log.info("Temperature backfill started: {} logs to process", candidates.size());

        AtomicInteger enriched = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (EvLog evLog : candidates) {
            try {
                WGS84Point center = GeoHash.fromGeohashString(evLog.getGeohash()).getBoundingBoxCenter();
                Optional<Double> temp = temperatureService.getTemperature(
                        center.getLatitude(), center.getLongitude(), evLog.getLoggedAt());

                temp.ifPresentOrElse(
                        t -> {
                            evLogRepository.updateTemperature(evLog.getId(), t);
                            enriched.incrementAndGet();
                        },
                        failed::incrementAndGet
                );

                Thread.sleep(SLEEP_MS_BETWEEN_REQUESTS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Temperature backfill interrupted after {}/{} logs", enriched.get(), candidates.size());
                break;
            } catch (Exception e) {
                failed.incrementAndGet();
                log.warn("Backfill failed for log {}: {}", evLog.getId(), e.getMessage());
            }
        }

        String summary = String.format("Temperature backfill complete: %d enriched, %d failed (of %d total)",
                enriched.get(), failed.get(), candidates.size());
        log.info(summary);
        return summary;
    }
}
