package com.evmonitor.application.voice;

import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftEntity;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Deckel und Nutzungsprotokoll des Sprachlogs auf {@code voice_draft}. Zeitstempel liegen in der
 * Serverzeit (wie {@code DEFAULT now()}), der Monat in der Zeitzone des Nutzers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceQuotaService {

    private final UserRepository userRepository;
    private final VoiceDraftRepository repository;

    public VoiceQuota quota(UUID userId, ZoneId zone) {
        VoiceQuota.Plan plan = userRepository.findById(userId)
                .map(u -> VoiceQuota.plan(u.getSubscriptionTier(), u.getRole()))
                .orElse(VoiceQuota.Plan.FREE);
        ZonedDateTime now = ZonedDateTime.now(Clock.systemDefaultZone()).withZoneSameInstant(zone);
        LocalDateTime since = VoiceQuota.monthStart(now).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime firstUse = repository.findFirstSuccessAt(userId)
                .map(t -> t.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone).toLocalDateTime())
                .orElse(null);
        return VoiceQuota.of(plan, repository.countSuccessfulSince(userId, since), firstUse, now);
    }

    /** Best Effort: ein fehlendes Protokoll darf dem Nutzer seinen Entwurf nicht nehmen. */
    public void record(VoiceDraftEntity row) {
        try {
            repository.save(row);
        } catch (Exception e) {
            log.warn("voice_draft nicht gespeichert: {}", e.getClass().getSimpleName());
        }
    }
}
