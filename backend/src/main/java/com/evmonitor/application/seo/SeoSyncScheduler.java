package com.evmonitor.application.seo;

import com.evmonitor.application.JobLockService;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nächtlicher GSC-Sync nach dem Backup (03:30) und vor den 06:00-Jobs. Fenster D-7 bis D-2, weil GSC
 * zwei bis drei Tage nachreift. Beim ersten Lauf (leere Tabelle) Backfill über die ganze Aufbewahrung.
 * Lock in {@code job_lock}, damit im Blue/Green-Deploy nur eine Instanz läuft.
 */
@Component
@Slf4j
public class SeoSyncScheduler {

    static final String LOCK_NAME = "seo_sync";
    static final Duration LOCK_TTL = Duration.ofHours(3);
    static final int NIGHTLY_WINDOW_DAYS = 6;
    static final int LAG_DAYS = 2;
    static final int MAX_MANUAL_DAYS = 500;

    private final SeoSyncService syncService;
    private final GscDailyRepository repository;
    private final JobLockService locks;
    private final GscProperties properties;
    private final Executor executor;
    private final Clock clock;

    @Autowired
    public SeoSyncScheduler(SeoSyncService syncService, GscDailyRepository repository, JobLockService locks,
                            GscProperties properties) {
        this(new Deps(syncService, repository, locks, properties, manualSyncExecutor()), Clock.systemDefaultZone());
    }

    /** Ein eigener Thread für den manuellen Start, wie beim XpengImportJobWorker; der Lock serialisiert ohnehin. */
    private static Executor manualSyncExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "seo-sync-manual");
            t.setDaemon(true);
            return t;
        });
    }

    SeoSyncScheduler(Deps deps, Clock clock) {
        this.syncService = deps.syncService();
        this.repository = deps.repository();
        this.locks = deps.locks();
        this.properties = deps.properties();
        this.executor = deps.executor();
        this.clock = clock;
    }

    /** Konstruktor-Bündel, damit der Test-Konstruktor unter fünf Parametern bleibt. */
    record Deps(SeoSyncService syncService, GscDailyRepository repository, JobLockService locks,
                GscProperties properties, Executor executor) {
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Berlin")
    public void runNightly() {
        if (!syncService.isEnabled()) return;
        LocalDate to = LocalDate.now(clock).minusDays(LAG_DAYS);
        LocalDate from = repository.hasAnyData()
                ? to.minusDays(NIGHTLY_WINDOW_DAYS)
                : LocalDate.now(clock).minusDays(properties.getRetentionDays());
        runLocked(from, to);
    }

    /**
     * Manueller Start aus dem Admin-Tab, läuft im Hintergrund. false, wenn der Zugang fehlt oder gerade
     * ein Lauf aktiv ist.
     */
    public boolean tryStartManual(int days) {
        if (!syncService.isEnabled()) return false;
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) return false;
        int window = Math.max(1, Math.min(MAX_MANUAL_DAYS, days));
        LocalDate to = LocalDate.now(clock).minusDays(LAG_DAYS);
        LocalDate from = to.minusDays(window - 1L);
        executor.execute(() -> syncReleasing(from, to));
        return true;
    }

    boolean runLocked(LocalDate from, LocalDate to) {
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            log.info("GSC-Sync übersprungen, Lock {} ist belegt", LOCK_NAME);
            return false;
        }
        syncReleasing(from, to);
        return true;
    }

    private void syncReleasing(LocalDate from, LocalDate to) {
        try {
            syncService.sync(from, to);
        } catch (Exception e) {
            log.error("GSC-Sync {} bis {} abgebrochen", from, to, e);
        } finally {
            locks.release(LOCK_NAME);
        }
    }
}
