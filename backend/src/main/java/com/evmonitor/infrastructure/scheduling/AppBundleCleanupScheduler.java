package com.evmonitor.infrastructure.scheduling;

import com.evmonitor.application.AppUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Raeumt alte Capgo-Bundle-Zeilen (app_bundle) auf. Die Zip-Dateien selbst rotiert
 * die CI (der Backend-Mount ist read-only), hier geht es nur um die DB-Zeilen.
 */
@Component
@Slf4j
public class AppBundleCleanupScheduler {

    private static final int RETENTION_DAYS = 30;

    private final AppUpdateService appUpdateService;

    public AppBundleCleanupScheduler(AppUpdateService appUpdateService) {
        this.appUpdateService = appUpdateService;
    }

    @Scheduled(cron = "0 20 4 * * *")
    public void purgeOldBundles() {
        int deleted = appUpdateService.purgeBundlesOlderThan(RETENTION_DAYS);
        if (deleted > 0) {
            log.info("Purged {} app bundle row(s) older than {} days", deleted, RETENTION_DAYS);
        }
    }
}
