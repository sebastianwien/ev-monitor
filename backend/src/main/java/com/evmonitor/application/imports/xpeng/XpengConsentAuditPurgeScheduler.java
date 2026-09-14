package com.evmonitor.application.imports.xpeng;

import com.evmonitor.infrastructure.persistence.xpeng.XpengConsentAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * DSGVO-Minimierung: archivierte XPeng-Einwilligungs-Nachweise werden nach 3 Jahren geloescht.
 * Laeuft einmal taeglich; das Loeschbegehren einzelner User greift sofort ueber die Kontoloeschung.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class XpengConsentAuditPurgeScheduler {

    private static final int RETENTION_YEARS = 3;

    private final XpengConsentAuditRepository auditRepository;

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpired() {
        int removed = auditRepository.deleteByArchivedAtBefore(
                LocalDateTime.now().minusYears(RETENTION_YEARS));
        if (removed > 0) {
            log.info("XPeng consent audit: {} abgelaufene Nachweise (>{}J) geloescht", removed, RETENTION_YEARS);
        }
    }
}
