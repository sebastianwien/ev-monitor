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
    /** Plausible ist am Folgetag vollständig. */
    static final int PLAUSIBLE_LAG_DAYS = 1;
    static final int MAX_MANUAL_DAYS = 500;

    private final SeoSyncService syncService;
    private final GscDailyRepository repository;
    private final JobLockService locks;
    private final GscProperties properties;
    private final Executor executor;
    private final PlausibleSyncService plausibleSync;
    private final Clock clock;

    @Autowired
    public SeoSyncScheduler(SeoSyncService syncService, GscDailyRepository repository, JobLockService locks,
                            GscProperties properties, PlausibleSyncService plausibleSync) {
        this(new Deps(syncService, repository, locks, properties, manualSyncExecutor()), plausibleSync, Clock.systemDefaultZone());
    }

    /** Ein eigener Thread für den manuellen Start, wie beim XpengImportJobWorker; der Lock serialisiert ohnehin. */
    private static Executor manualSyncExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "seo-sync-manual");
            t.setDaemon(true);
            return t;
        });
    }

    SeoSyncScheduler(Deps deps, PlausibleSyncService plausibleSync, Clock clock) {
        this.syncService = deps.syncService();
        this.repository = deps.repository();
        this.locks = deps.locks();
        this.properties = deps.properties();
        this.executor = deps.executor();
        this.plausibleSync = plausibleSync;
        this.clock = clock;
    }

    /** Konstruktor-Bündel, damit der Test-Konstruktor unter fünf Parametern bleibt. */
    record Deps(SeoSyncService syncService, GscDailyRepository repository, JobLockService locks,
                GscProperties properties, Executor executor) {
    }

    /** Welche Quellen laufen: beide, wenn konfiguriert. */
    private boolean anyEnabled() {
        return syncService.isEnabled() || plausibleSync.isEnabled();
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Berlin")
    public void runNightly() {
        if (!anyEnabled()) return;
        LocalDate today = LocalDate.now(clock);
        LocalDate gscTo = today.minusDays(LAG_DAYS);
        LocalDate gscFrom = repository.hasAnyData()
                ? gscTo.minusDays(NIGHTLY_WINDOW_DAYS)
                : today.minusDays(properties.getRetentionDays());
        LocalDate plausibleTo = today.minusDays(PLAUSIBLE_LAG_DAYS);
        LocalDate plausibleFrom = plausibleSync.hasAnyData()
                ? plausibleTo.minusDays(NIGHTLY_WINDOW_DAYS)
                : plausibleSync.backfillFrom();
        runLocked(new Windows(gscFrom, gscTo, plausibleFrom, plausibleTo));
    }

    record Windows(LocalDate gscFrom, LocalDate gscTo, LocalDate plausibleFrom, LocalDate plausibleTo) {
    }

    /**
     * Manueller Start aus dem Admin-Tab, läuft im Hintergrund. false, wenn der Zugang fehlt oder gerade
     * ein Lauf aktiv ist.
     */
    public boolean tryStartManual(int days) {
        if (!anyEnabled()) return false;
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) return false;
        int window = Math.max(1, Math.min(MAX_MANUAL_DAYS, days));
        LocalDate today = LocalDate.now(clock);
        LocalDate gscTo = today.minusDays(LAG_DAYS);
        LocalDate plausibleTo = today.minusDays(PLAUSIBLE_LAG_DAYS);
        Windows w = new Windows(gscTo.minusDays(window - 1L), gscTo, plausibleTo.minusDays(window - 1L), plausibleTo);
        executor.execute(() -> syncReleasing(w));
        return true;
    }

    boolean runLocked(Windows w) {
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            log.info("SEO-Sync übersprungen, Lock {} ist belegt", LOCK_NAME);
            return false;
        }
        syncReleasing(w);
        return true;
    }

    /** Jede Quelle für sich fehlertolerant; der Lock wird immer freigegeben. */
    private void syncReleasing(Windows w) {
        try {
            if (syncService.isEnabled()) {
                try {
                    syncService.sync(w.gscFrom(), w.gscTo());
                } catch (Exception e) {
                    log.error("GSC-Sync {} bis {} abgebrochen", w.gscFrom(), w.gscTo(), e);
                }
            }
            if (plausibleSync.isEnabled()) {
                try {
                    plausibleSync.sync(w.plausibleFrom(), w.plausibleTo());
                } catch (Exception e) {
                    log.error("Plausible-Sync {} bis {} abgebrochen", w.plausibleFrom(), w.plausibleTo(), e);
                }
            }
        } finally {
            locks.release(LOCK_NAME);
        }
    }
}
