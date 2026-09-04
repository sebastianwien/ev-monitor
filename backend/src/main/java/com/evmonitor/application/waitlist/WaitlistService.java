package com.evmonitor.application.waitlist;

import com.evmonitor.domain.WaitlistFeature;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistEntry;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-User Opt-in auf Feature-Wartelisten. Jede Methode ist auf den eingeloggten
 * User gebunden (userId aus dem JWT) - ein User sieht/aendert nur eigene Eintraege.
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final FeatureWaitlistRepository repo;

    /** Status eines Eintrags plus, falls vorhanden, seit wann. */
    public record WaitlistStatus(boolean onWaitlist, LocalDateTime since) {}

    /** Idempotent: mehrfaches Eintragen aendert weder Bestand noch Zeitpunkt. */
    @Transactional
    public WaitlistStatus join(UUID userId, WaitlistFeature feature) {
        return repo.findByUserIdAndFeature(userId, feature)
                .map(e -> new WaitlistStatus(true, e.getCreatedAt()))
                .orElseGet(() -> {
                    FeatureWaitlistEntry saved = repo.save(FeatureWaitlistEntry.builder()
                            .userId(userId)
                            .feature(feature)
                            .createdAt(LocalDateTime.now())
                            .build());
                    return new WaitlistStatus(true, saved.getCreatedAt());
                });
    }

    @Transactional(readOnly = true)
    public WaitlistStatus status(UUID userId, WaitlistFeature feature) {
        return repo.findByUserIdAndFeature(userId, feature)
                .map(e -> new WaitlistStatus(true, e.getCreatedAt()))
                .orElse(new WaitlistStatus(false, null));
    }

    /** Idempotent: kein Fehler, wenn der User nicht auf der Liste steht. */
    @Transactional
    public void leave(UUID userId, WaitlistFeature feature) {
        repo.deleteByUserIdAndFeature(userId, feature);
    }
}
