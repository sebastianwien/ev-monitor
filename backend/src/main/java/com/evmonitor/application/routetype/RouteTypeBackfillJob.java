package com.evmonitor.application.routetype;

import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Einmal-Backfill des Streckentyps für den Bestand, ausgelöst über {@code POST /api/admin/route-type/backfill}.
 * Kandidaten sind alle Ladungen, deren Herkunft leer, TRIPS oder HEURISTIC ist; USER und SOURCE werden nie angefasst.
 * Mit {@code dryRun} wird nur gerechnet und gezählt, nichts geschrieben.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RouteTypeBackfillJob {

    private final EvLogRepository evLogRepository;
    private final RouteTypeDerivationService derivation;

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
                Optional<RouteTypeDerivationService.Derivation> d = dryRun ? derivation.compute(log.get()) : derivation.derive(id);
                if (d.isPresent()) {
                    changed++;
                    bySource.merge(log.get().getDataSource() + " -> " + d.get().routeType() + "/" + d.get().source(), 1, Integer::sum);
                }
            } catch (RuntimeException e) {
                errors++;
                RouteTypeBackfillJob.log.warn("Streckentyp-Backfill: Ladung {} übersprungen: {}", id, e.getMessage());
            }
        }
        RouteTypeBackfillSummary summary = new RouteTypeBackfillSummary(dryRun, ids.size(), changed, errors, bySource);
        log.info("Streckentyp-Backfill fertig: {}", summary);
        return summary;
    }
}
