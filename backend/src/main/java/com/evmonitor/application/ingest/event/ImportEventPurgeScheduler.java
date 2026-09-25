package com.evmonitor.application.ingest.event;

import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * DSGVO-Minimierung: das Import-Protokoll wird nach der Aufbewahrungsfrist gelöscht (Standard
 * 90 Tage). Das Löschbegehren einzelner Nutzer greift sofort über die Kontolöschung.
 */
@Component
@Slf4j
public class ImportEventPurgeScheduler {

    private final ImportEventRepository repository;
    private final Duration retention;

    public ImportEventPurgeScheduler(ImportEventRepository repository,
                                     @Value("${import-events.retention:P90D}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(cron = "0 50 3 * * *")
    public void purgeExpired() {
        int removed = repository.deleteByCreatedAtBefore(LocalDateTime.now().minus(retention));
        if (removed > 0) {
            log.info("Import-Protokoll: {} Einträge älter als {} gelöscht", removed, retention);
        }
    }
}
