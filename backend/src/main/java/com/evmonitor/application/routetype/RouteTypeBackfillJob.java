package com.evmonitor.application.routetype;

import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Einmal-Backfill des Streckentyps für den Bestand, ausgelöst über {@code POST /api/admin/route-type/backfill}.
 * Kandidaten sind alle Ladungen, deren Herkunft leer, TRIPS oder HEURISTIC ist; USER und SOURCE werden nie angefasst.
 * Mit {@code dryRun} wird nur gerechnet und gezählt, nichts geschrieben. Gezählt werden nur echte Änderungen
 * (Typ oder Herkunft weicht vom gespeicherten Wert ab).
 *
 * <p>Läuft asynchron (lokal 43.000 Kandidaten in 11 Minuten, das überlebt kein HTTP-Timeout); es läuft höchstens
 * ein Lauf gleichzeitig, das letzte Ergebnis bleibt im Speicher abrufbar.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RouteTypeBackfillJob {

    private final EvLogRepository evLogRepository;
    private final RouteTypeDerivationService derivation;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<RouteTypeBackfillSummary> last = new AtomicReference<>();

    public record Status(boolean running, RouteTypeBackfillSummary last) {}

    public Status status() {
        return new Status(running.get(), last.get());
    }

    /** Lauf reservieren; false, wenn gerade einer läuft. Danach {@link #runAsync} aufrufen. */
    public boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    @Async
    public void runAsync(boolean dryRun) {
        try {
            last.set(run(dryRun));
        } finally {
            running.set(false);
        }
    }

    public RouteTypeBackfillSummary run(boolean dryRun) {
        List<UUID> ids = evLogRepository.findRouteTypeBackfillCandidateIds();
        log.info("Streckentyp-Backfill ({}): {} Kandidaten", dryRun ? "Dry-run" : "Echtlauf", ids.size());
        TreeMap<String, Integer> bySource = new TreeMap<>();
        int changed = 0;
        int errors = 0;
        for (UUID id : ids) {
            try {
                Optional<EvLog> log = evLogRepository.findById(id);
                if (log.isEmpty()) continue;
                Optional<RouteTypeDerivationService.Derivation> d = derivation.compute(log.get());
                if (d.isEmpty() || isUnchanged(log.get(), d.get())) continue;
                if (!dryRun) evLogRepository.updateRouteType(id, d.get().routeType(), d.get().source());
                changed++;
                bySource.merge(log.get().getDataSource() + " -> " + d.get().routeType() + "/" + d.get().source(), 1, Integer::sum);
            } catch (RuntimeException e) {
                errors++;
                RouteTypeBackfillJob.log.warn("Streckentyp-Backfill: Ladung {} übersprungen: {}", id, e.getMessage());
            }
        }
        RouteTypeBackfillSummary summary = new RouteTypeBackfillSummary(dryRun, ids.size(), changed, errors, bySource);
        log.info("Streckentyp-Backfill fertig: {}", summary);
        return summary;
    }

    private static boolean isUnchanged(EvLog log, RouteTypeDerivationService.Derivation d) {
        return d.routeType() == log.getRouteType() && d.source() == log.getRouteTypeSource();
    }
}
