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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>
 * Beim Blue/Green-Deploy laufen kurz zwei Instanzen. Deshalb raeumt jede Instanz beim Herunterfahren
 * ihren eigenen laufenden Job auf; fremde PROCESSING-Jobs gelten erst nach {@link #STALE_AFTER}
 * als haengengeblieben (Absturz).
 */
@Component
@Slf4j
public class XpengImportJobWorker {

    /** Laengster Import auf Prod: 25 s (Stand 10/2026). Was laenger PROCESSING ist, ist verwaist. */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final UUID NO_JOB = new UUID(0, 0);
    private static final String ABORTED = "Server-Neustart - Job wurde abgebrochen";

    private final XpengImportJobRepository jobRepo;
    private final XpengImportService importService;
    private final TransactionTemplate tx;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "xpeng-import-worker"));
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicReference<UUID> currentJob = new AtomicReference<>();

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
        abortStaleJobs();
        wakeUp();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        UUID running = currentJob.get();
        if (running != null) {
            tx.executeWithoutResult(status -> jobRepo.markProcessingAsFailed(running, ABORTED, LocalDateTime.now()));
            log.warn("XpengImportWorker: job={} beim Herunterfahren abgebrochen", running);
        }
    }

    /** Verwaiste PROCESSING-Jobs abgestuerzter Instanzen beenden, nie den eigenen laufenden. */
    void abortStaleJobs() {
        try {
            LocalDateTime now = LocalDateTime.now();
            UUID own = Optional.ofNullable(currentJob.get()).orElse(NO_JOB);
            int aborted = tx.execute(status ->
                    jobRepo.markStaleProcessingAsFailed(ABORTED, now, now.minus(STALE_AFTER), own));
            if (aborted > 0) log.warn("XpengImportWorker: {} verwaiste Jobs abgebrochen", aborted);
        } catch (Exception e) {
            log.error("XpengImportWorker: stale check failed", e);
        }
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
                currentJob.set(job.get().getId());
                try {
                    processSafely(job.get());
                } finally {
                    currentJob.set(null);
                }
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
     * Nach einem Neustart: verwaiste PROCESSING-Jobs werden FAILED (siehe {@link #STALE_AFTER}).
     * QUEUED-Jobs laufen weiter, sofern ihr Tempfile noch existiert.
     */
    void recoverInterruptedJobs() {
        tx.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            int aborted = jobRepo.markStaleProcessingAsFailed(ABORTED, now, now.minus(STALE_AFTER), NO_JOB);
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
