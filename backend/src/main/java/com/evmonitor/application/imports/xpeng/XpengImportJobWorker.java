package com.evmonitor.application.imports.xpeng;

import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJobRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Arbeitet die XPeng-Import-Warteschlange ab. Die Warteschlange ist die Tabelle
 * {@code xpeng_import_job} selbst (Status QUEUED), nicht ein In-Memory-Executor:
 * <ul>
 *   <li>Jobs ueberleben Deploys und Crashes (QUEUED bleibt QUEUED, Tempfile liegt auf Platte).</li>
 *   <li>Es gibt keine Queue-Obergrenze, die einen Job stumm haengen lassen koennte.</li>
 *   <li>Manuelle Uploads und kuenftige periodische API-Pulls legen einfach Jobs an.</li>
 * </ul>
 * Genau ein Worker-Thread verarbeitet Jobs sequenziell - ein Import ist CPU- und DB-lastig,
 * mehr Parallelitaet wuerde nur den restlichen Betrieb bremsen. Aufgeweckt wird der Worker
 * sofort nach dem Commit eines neuen Jobs und zusaetzlich periodisch als Sicherheitsnetz.
 */
@Component
@Slf4j
public class XpengImportJobWorker {

    private final XpengImportJobRepository jobRepo;
    private final XpengImportService importService;
    private final TransactionTemplate tx;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "xpeng-import-worker"));
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public XpengImportJobWorker(XpengImportJobRepository jobRepo, XpengImportService importService,
                                TransactionTemplate tx) {
        this.jobRepo = jobRepo;
        this.importService = importService;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        try {
            recoverInterruptedJobs();
        } catch (Exception e) {
            log.error("XpengImportWorker: recovery on startup failed", e);
        }
        wakeUp();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onJobQueued(XpengImportJobQueuedEvent event) {
        wakeUp();
    }

    @Scheduled(fixedDelayString = "${xpeng.import.worker.poll-ms:30000}")
    void poll() {
        wakeUp();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Startet einen Abarbeitungslauf, falls keiner laeuft. Mehrfachaufrufe sind harmlos. */
    void wakeUp() {
        if (draining.compareAndSet(false, true)) {
            executor.execute(this::drain);
        }
    }

    /** Verarbeitet QUEUED-Jobs nacheinander, bis die Warteschlange leer ist. */
    void drain() {
        try {
            Optional<XpengImportJob> job;
            while ((job = claimNext()).isPresent()) {
                processSafely(job.get());
            }
        } catch (Throwable t) {
            log.error("XpengImportWorker: drain aborted", t);
        } finally {
            draining.set(false);
        }
    }

    private Optional<XpengImportJob> claimNext() {
        return tx.execute(status -> jobRepo.findNextQueuedForUpdate().map(job -> {
            job.setStatus(XpengImportJob.Status.PROCESSING);
            job.setStartedAt(LocalDateTime.now());
            return jobRepo.save(job);
        }));
    }

    private void processSafely(XpengImportJob job) {
        try {
            importService.process(job);
        } catch (Throwable t) {
            // process() markiert den Job selbst als FAILED; hier nur verhindern, dass ein
            // unerwarteter Fehler die restliche Warteschlange blockiert.
            log.error("XpengImportWorker: job={} threw", job.getId(), t);
        }
    }

    /**
     * Nach einem Neustart: PROCESSING-Jobs sind unvollstaendig und werden FAILED.
     * QUEUED-Jobs laufen weiter, sofern ihr Tempfile noch existiert.
     */
    void recoverInterruptedJobs() {
        tx.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            int aborted = jobRepo.markProcessingAsFailed("Server-Neustart - Job wurde abgebrochen", now);
            int orphaned = 0;
            for (XpengImportJob job : jobRepo.findAllByStatus(XpengImportJob.Status.QUEUED)) {
                if (job.getTempfilePath() != null && Files.exists(Path.of(job.getTempfilePath()))) continue;
                job.setStatus(XpengImportJob.Status.FAILED);
                job.setErrorMessage("Server-Neustart - Upload-Datei nicht mehr vorhanden, bitte erneut hochladen");
                job.setCompletedAt(now);
                jobRepo.save(job);
                orphaned++;
            }
            if (aborted > 0 || orphaned > 0) {
                log.warn("XpengImportWorker: recovery aborted={} orphanedQueued={}", aborted, orphaned);
            }
        });
    }
}
