package com.evmonitor.infrastructure.weather;

import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.weather.TemperatureSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fuellt und korrigiert {@code outside_temp_celsius} auf Fahrten - dasselbe Vorgehen wie beim
 * {@link TemperatureBackfillJob}, nur mit zwei Punkten pro Zeile: eine Fahrt bekommt das Mittel
 * aus Start- und Endtemperatur, und wo nur einer der beiden Orte bekannt ist, dessen Wert.
 *
 * <p>Ausloeser: naechtlicher Scheduler und {@code POST /api/admin/backfill-trip-temperature}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripTemperatureBackfillJob {

    /** Zweite Haelfte des Tagesbudgets, siehe {@link TemperatureBackfillJob#MAX_API_CALLS}. */
    static final int MAX_API_CALLS = 4_500;

    static final int CANDIDATE_LIMIT = 12_000;

    private final EvTripRepository evTripRepository;
    private final TemperatureBackfillRunner runner;

    public String run() {
        List<TemperatureBackfillRunner.Candidate> candidates = new ArrayList<>();
        for (Object[] row : evTripRepository.findTemperatureCandidates(PageRequest.of(0, CANDIDATE_LIMIT))) {
            List<TemperatureBackfillRunner.Point> points = new ArrayList<>(2);
            addPoint(points, (String) row[1], (OffsetDateTime) row[2]);
            addPoint(points, (String) row[3], (OffsetDateTime) row[4]);
            if (!points.isEmpty()) {
                candidates.add(new TemperatureBackfillRunner.Candidate((UUID) row[0], points));
            }
        }

        log.info("Trip temperature backfill: {} candidates", candidates.size());
        var summary = runner.run("trips", candidates, this::save,
                new TemperatureBackfillRunner.Budget(
                        MAX_API_CALLS,
                        TemperatureBackfillJob.SLEEP_MS_BETWEEN_REQUESTS,
                        TemperatureBackfillJob.MAX_CONSECUTIVE_ERRORS,
                        TemperatureBackfillJob.MAX_DURATION));

        return "Trip temperature backfill: " + summary;
    }

    private void save(UUID id, double celsius) {
        BigDecimal rounded = BigDecimal.valueOf(celsius).setScale(1, RoundingMode.HALF_UP);
        evTripRepository.updateTemperature(id, rounded, TemperatureSource.FORECAST);
    }

    private static void addPoint(List<TemperatureBackfillRunner.Point> points, String geohash, OffsetDateTime at) {
        if (geohash == null || geohash.isBlank() || at == null) return;
        LocalDateTime local = at.atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
        points.add(new TemperatureBackfillRunner.Point(geohash, local));
    }
}
