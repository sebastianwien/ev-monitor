package com.evmonitor.application.imports.sample;

import com.evmonitor.infrastructure.persistence.sample.ImportSampleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * DSGVO-Minimierung: pseudonymisierte Upload-Kopien werden nach der Aufbewahrungsfrist geloescht
 * (Standard 180 Tage). Das Loeschbegehren einzelner User greift sofort ueber die Kontoloeschung.
 */
@Component
@Slf4j
public class ImportSamplePurgeScheduler {

    private final ImportSampleRepository repository;
    private final Duration retention;

    public ImportSamplePurgeScheduler(ImportSampleRepository repository,
                                      @Value("${import-samples.retention:P180D}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(cron = "0 40 3 * * *")
    public void purgeExpired() {
        int removed = repository.deleteByCreatedAtBefore(LocalDateTime.now().minus(retention));
        if (removed > 0) {
            log.info("Import-Samples: {} Kopien aelter als {} geloescht", removed, retention);
        }
    }
}
