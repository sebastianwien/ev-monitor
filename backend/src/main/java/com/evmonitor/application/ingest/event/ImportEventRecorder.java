package com.evmonitor.application.ingest.event;

import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Schreibt das Import-Protokoll {@code import_event}. Best Effort: nichts hier darf einen Import
 * abbrechen, Fehler werden geschluckt und nur mit Klassennamen geloggt.
 *
 * <p>Läuft eine Transaktion, wird erst nach ihrem Ende geschrieben, in eigener Transaktion
 * (REQUIRES_NEW). So überlebt das Protokoll jeden Rollback, hält keine zweite Verbindung, während der
 * Import noch Sperren hält, und ein zurückgerollter Import steht als {@code FAILED} im Protokoll statt
 * als Erfolg - etwa der Tür-2-Race, der erst beim Commit auffällt.
 */
@Service
@Slf4j
public class ImportEventRecorder {

    static final String ROLLED_BACK = "Transaktion zurückgerollt";

    private final ImportEventRepository repository;
    private final TransactionTemplate ownTransaction;

    public ImportEventRecorder(ImportEventRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(ImportEvent event) {
        if (event == null) return;
        if (event.getCreatedAt() == null) event.setCreatedAt(LocalDateTime.now());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            save(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) markRolledBack(event);
                save(event);
            }
        });
    }

    private static void markRolledBack(ImportEvent event) {
        if (!event.getOutcome().isSuccess()) return;
        event.setOutcome(ImportEventOutcome.FAILED);
        event.setError(ROLLED_BACK);
        event.setSessionsImported(0);
        event.setTripsImported(0);
    }

    private void save(ImportEvent event) {
        try {
            ownTransaction.executeWithoutResult(status -> repository.save(event));
        } catch (Exception e) {
            log.warn("Import-Event {}/{} nicht gespeichert: {}",
                    event.getDataSource(), event.getOutcome(), e.getClass().getSimpleName());
        }
    }
}
