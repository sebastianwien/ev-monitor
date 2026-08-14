package com.evmonitor.infrastructure.weather;

import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.weather.TemperatureSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fuellt und korrigiert {@code temperature_celsius} auf Ladelogs.
 *
 * <p>Der Arbeitsvorrat sind Logs mit Ort, deren Temperatur fehlt <em>oder</em> deren Herkunft
 * unbekannt ist ({@code temperature_source IS NULL}). Der zweite Fall betrifft Altdaten, die mit
 * der frueheren, zeitzonenfalschen Wetterabfrage gefuellt wurden. Weil jede fertige Zeile ihre
 * Herkunft bekommt, schrumpft der Vorrat mit jedem Lauf und ist am Ende leer - der Job hoert dann
 * von selbst auf zu arbeiten, ohne dass ihn jemand abschalten muss.
 *
 * <p>Wird naechtlich vom Scheduler gestartet und laesst sich zusaetzlich ueber
 * {@code POST /api/admin/backfill-temperature} ausloesen (ADMIN-only).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemperatureBackfillJob {

    /**
     * Aufrufbudget pro Lauf. Zusammen mit dem Fahrten-Backfill bleibt Raum unter dem Tageslimit
     * von 10.000 Anfragen, inklusive Reserve fuer den laufenden Betrieb.
     */
    static final int MAX_API_CALLS = 4_500;

    /** ~75 Anfragen/min - Faktor 8 unter dem Minutenlimit, und das Stundenlimit bleibt unerreicht. */
    static final long SLEEP_MS_BETWEEN_REQUESTS = 800;

    static final int MAX_CONSECUTIVE_ERRORS = 5;

    /**
     * 4.500 Abrufe mit 800 ms Pause dauern etwa eine Stunde - 90 Minuten lassen Luft fuer traege
     * Antworten, ohne dass beide Backfills zusammen in den Tagesbetrieb laufen.
     */
    static final java.time.Duration MAX_DURATION = java.time.Duration.ofMinutes(90);

    /** So viele Zeilen werden pro Lauf geladen - durch Buendelung reichen dafuer weniger Abrufe. */
    static final int CANDIDATE_LIMIT = 12_000;

    private final EvLogRepository evLogRepository;
    private final TemperatureBackfillRunner runner;

    public String run() {
        List<TemperatureBackfillRunner.Candidate> candidates =
                evLogRepository.findTemperatureCandidates(CANDIDATE_LIMIT).stream()
                        .map(c -> new TemperatureBackfillRunner.Candidate(c.id(),
                                List.of(new TemperatureBackfillRunner.Point(c.geohash(), c.at()))))
                        .toList();

        log.info("Log temperature backfill: {} candidates", candidates.size());
        var summary = runner.run("logs", candidates,
                (id, celsius) -> evLogRepository.updateTemperature(id, celsius, TemperatureSource.FORECAST),
                new TemperatureBackfillRunner.Budget(
                        MAX_API_CALLS, SLEEP_MS_BETWEEN_REQUESTS, MAX_CONSECUTIVE_ERRORS, MAX_DURATION));

        return "Log temperature backfill: " + summary;
    }
}
