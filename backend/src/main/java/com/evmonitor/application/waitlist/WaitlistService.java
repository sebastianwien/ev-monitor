package com.evmonitor.application.waitlist;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.WaitlistFeature;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistEntry;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-User Opt-in auf Feature-Wartelisten. Jede Methode ist auf den eingeloggten
 * User gebunden (userId aus dem JWT) - ein User sieht/aendert nur eigene Eintraege.
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final FeatureWaitlistRepository repo;
    private final UserRepository userRepository;

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

    /** Admin-Sicht: wer wartet seit wann, in Reihenfolge des Eintrags. */
    public record AdminEntry(UUID userId, String email, LocalDateTime since) {}

    @Transactional(readOnly = true)
    public List<AdminEntry> adminEntries(WaitlistFeature feature) {
        List<FeatureWaitlistEntry> entries = repo.findAllByFeatureOrderByCreatedAtAsc(feature);
        Map<UUID, String> emails = userRepository.findAllByIds(entries.stream().map(FeatureWaitlistEntry::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getEmail));
        return entries.stream()
                .map(e -> new AdminEntry(e.getUserId(), emails.get(e.getUserId()), e.getCreatedAt()))
                .toList();
    }

    /** Idempotent: kein Fehler, wenn der User nicht auf der Liste steht. */
    @Transactional
    public void leave(UUID userId, WaitlistFeature feature) {
        repo.deleteByUserIdAndFeature(userId, feature);
    }
}
