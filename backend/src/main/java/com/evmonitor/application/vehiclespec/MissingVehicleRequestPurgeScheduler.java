package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.vehiclespec.MissingVehicleRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * DSGVO-Minimierung: die Kontakt-Mail einer Meldung dient nur der Rückmeldung und wird
 * 30 Tage nach Erledigung genullt. Meldungen, die nie erledigt werden (Spam, Duplikat,
 * kein echtes Auto), verlieren sie spätestens 90 Tage nach Eingang. Die Meldung selbst
 * bleibt als Protokoll.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MissingVehicleRequestPurgeScheduler {

    static final int RETENTION_DAYS = 30;
    static final int OPEN_RETENTION_DAYS = 90;

    private final MissingVehicleRequestRepository repository;

    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void purgeContactEmails() {
        LocalDateTime now = LocalDateTime.now();
        int purgedDone = repository.purgeContactEmailsDoneBefore(now.minusDays(RETENTION_DAYS));
        int purgedOpen = repository.purgeContactEmailsOpenCreatedBefore(now.minusDays(OPEN_RETENTION_DAYS));
        if (purgedDone > 0 || purgedOpen > 0) {
            log.info("Fahrzeug-Meldungen: {} Kontakt-Mails {} Tage nach Erledigung, {} offene nach {} Tagen genullt",
                    purgedDone, RETENTION_DAYS, purgedOpen, OPEN_RETENTION_DAYS);
        }
    }
}
