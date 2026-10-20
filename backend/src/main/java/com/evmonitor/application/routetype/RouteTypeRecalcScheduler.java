package com.evmonitor.application.routetype;

import com.evmonitor.domain.EvLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Nachlauf der Streckentyp-Ableitung: Fahrten kommen teils erst nach der Ladung an (Tesla Live, XPeng, Tessie).
 * Alle 6 Stunden werden die Ladungen der letzten 7 Tage neu gerechnet, deren Streckentyp fehlt oder aus
 * Fahrten stammt. USER, SOURCE und HEURISTIC bleiben unberührt (die Faustregel greift nur auf leeren Typ).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RouteTypeRecalcScheduler {

    static final int WINDOW_DAYS = 7;

    private final EvLogRepository evLogRepository;
    private final RouteTypeDerivationService derivation;

    public record Result(int candidates, int changed, int errors) {}

    @Scheduled(cron = "0 20 */6 * * *")
    public void recalcRecent() {
        Result r = run(LocalDateTime.now().minusDays(WINDOW_DAYS));
        log.info("Streckentyp-Nachlauf: {} Kandidaten, {} geändert, {} Fehler", r.candidates(), r.changed(), r.errors());
    }

    Result run(LocalDateTime since) {
        List<UUID> ids = evLogRepository.findRouteTypeRecalcCandidateIds(since);
        int changed = 0;
        int errors = 0;
        for (UUID id : ids) {
            try {
                if (derivation.derive(id).isPresent()) changed++;
            } catch (RuntimeException e) {
                errors++;
                log.warn("Streckentyp-Nachlauf: Ladung {} übersprungen: {}", id, e.getMessage());
            }
        }
        return new Result(ids.size(), changed, errors);
    }
}
