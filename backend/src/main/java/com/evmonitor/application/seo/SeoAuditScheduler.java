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
 * Nächtlicher On-Page-Check um 05:00, nach dem Prerender-Neustart (04:12) und dem Cache-Warmer (04:17),
 * damit der Crawl den gewärmten Cache trifft. Danach wöchentlich Core Web Vitals. Eigener Lock, unabhängig vom
 * GSC-/Plausible-Sync.
 */
@Component
@Slf4j
public class SeoAuditScheduler {

    static final String LOCK_NAME = "seo_audit";
    static final Duration LOCK_TTL = Duration.ofHours(2);

    private final SeoAuditService service;
    private final SeoVitalsService vitals;
    private final JobLockService locks;
    private final Executor executor;

    @Autowired
    public SeoAuditScheduler(SeoAuditService service, SeoVitalsService vitals, JobLockService locks) {
        this(service, vitals, locks, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "seo-audit-manual");
            t.setDaemon(true);
            return t;
        }));
    }

    SeoAuditScheduler(SeoAuditService service, SeoVitalsService vitals, JobLockService locks, Executor executor) {
        this.service = service;
        this.vitals = vitals;
        this.locks = locks;
        this.executor = executor;
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "Europe/Berlin")
    public void runNightly() {
        if (!service.isEnabled()) return;
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            log.info("SEO-Audit übersprungen, Lock {} ist belegt", LOCK_NAME);
            return;
        }
        runReleasing();
    }

    /** Manueller Start aus dem Admin-Tab im Hintergrund. false, wenn aus oder gerade ein Lauf aktiv ist. */
    public boolean tryStartManual() {
        if (!service.isEnabled()) return false;
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) return false;
        executor.execute(this::runReleasing);
        return true;
    }

    /** Erst der Crawl, dann (einmal je Woche) Core Web Vitals für die Seiten, die er erreicht hat. */
    private void runReleasing() {
        try {
            try {
                service.run();
            } catch (Exception e) {
                log.error("SEO-Audit abgebrochen", e);
            }
            try {
                vitals.refreshIfDue();
            } catch (Exception e) {
                log.error("Core Web Vitals abgebrochen", e);
            }
        } finally {
            locks.release(LOCK_NAME);
        }
    }
}
