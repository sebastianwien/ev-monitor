package com.evmonitor.infrastructure.weather;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Backfill-Job für outside_temp_celsius auf ev_trip-Zeilen die einen Geohash
 * haben aber noch keine Temperatur. Wird manuell via POST /api/admin/backfill-trip-temperature
 * ausgelöst (ADMIN-only). Rate-limiting: 50ms Pause zwischen Open-Meteo-Requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripTemperatureBackfillJob {

    private static final long SLEEP_MS_BETWEEN_REQUESTS = 50;

    private final EvTripRepository evTripRepository;
    private final TemperatureService temperatureService;

    public String run() {
        List<EvTrip> candidates = evTripRepository.findAllWithGeohashAndNoTemperature();
        log.info("Trip temperature backfill started: {} trips to process", candidates.size());

        AtomicInteger enriched = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (EvTrip trip : candidates) {
            if (trip.getTripStartedAt() == null) {
                failed.incrementAndGet();
                continue;
            }
            try {
                LocalDateTime startedAt = trip.getTripStartedAt().toLocalDateTime();
                LocalDateTime endedAt = trip.getTripEndedAt() != null ? trip.getTripEndedAt().toLocalDateTime() : null;

                Optional<Double> startTemp = lookup(trip.getLocationStartGeohash(), startedAt);
                Optional<Double> endTemp = lookup(trip.getLocationEndGeohash(), endedAt);

                Double mean;
                if (startTemp.isPresent() && endTemp.isPresent()) {
                    mean = (startTemp.get() + endTemp.get()) / 2.0;
                } else if (startTemp.isPresent()) {
                    mean = startTemp.get();
                } else if (endTemp.isPresent()) {
                    mean = endTemp.get();
                } else {
                    failed.incrementAndGet();
                    Thread.sleep(SLEEP_MS_BETWEEN_REQUESTS);
                    continue;
                }

                BigDecimal tempBd = BigDecimal.valueOf(mean).setScale(1, RoundingMode.HALF_UP);
                evTripRepository.updateTemperature(trip.getId(), tempBd);
                enriched.incrementAndGet();

                Thread.sleep(SLEEP_MS_BETWEEN_REQUESTS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Trip temperature backfill interrupted after {}/{} trips", enriched.get(), candidates.size());
                break;
            } catch (Exception e) {
                failed.incrementAndGet();
                log.warn("Backfill failed for trip {}: {}", trip.getId(), e.getMessage());
            }
        }

        String summary = String.format("Trip temperature backfill complete: %d enriched, %d failed (of %d total)",
                enriched.get(), failed.get(), candidates.size());
        log.info(summary);
        return summary;
    }

    private Optional<Double> lookup(String geohash, LocalDateTime at) {
        if (geohash == null || geohash.isBlank() || at == null) {
            return Optional.empty();
        }
        WGS84Point center = GeoHash.fromGeohashString(geohash).getBoundingBoxCenter();
        return temperatureService.getTemperature(center.getLatitude(), center.getLongitude(), at);
    }
}
