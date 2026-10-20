package com.evmonitor.application.seo;

import com.evmonitor.application.JobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Prüft jede Nacht um 05:30, ob Wettbewerbsdaten fällig sind (Top 10 wöchentlich, Volumen und Ideen monatlich),
 * nach dem GSC-Abruf, damit die wichtigsten Begriffe aktuell sind. Eigener Lock.
 */
@Component
@Slf4j
public class SeoCompetitionScheduler {

    static final String LOCK_NAME = "seo_competition";
    static final Duration LOCK_TTL = Duration.ofHours(1);

    private final SeoCompetitionService service;
    private final JobLockService locks;
    private final Executor executor;

    @Autowired
    public SeoCompetitionScheduler(SeoCompetitionService service, JobLockService locks) {
        this(service, locks, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "seo-competition-manual");
            t.setDaemon(true);
            return t;
        }));
    }

    SeoCompetitionScheduler(SeoCompetitionService service, JobLockService locks, Executor executor) {
        this.service = service;
        this.locks = locks;
        this.executor = executor;
    }

    @Scheduled(cron = "0 30 5 * * *", zone = "Europe/Berlin")
    public void runNightly() {
        if (!service.isEnabled()) return;
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            log.info("Wettbewerb übersprungen, Lock {} ist belegt", LOCK_NAME);
            return;
        }
        runReleasing();
    }

    /** Manueller Start: holt nur, was fällig ist, damit kein Klick Geld kostet, das nicht nötig ist. */
    public boolean tryStartManual() {
        if (!service.isEnabled()) return false;
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) return false;
        executor.execute(this::runReleasing);
        return true;
    }

    private void runReleasing() {
        try {
            service.run();
        } catch (Exception e) {
            log.error("Wettbewerb abgebrochen", e);
        } finally {
            locks.release(LOCK_NAME);
        }
    }
}
