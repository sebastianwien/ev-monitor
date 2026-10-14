package com.evmonitor.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {

    private final PublicModelService publicModelService;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmOnStartup() {
        warm();
    }

    // Re-warm slightly before the 4h Caffeine TTL so the cache never goes cold.
    // initialDelay = 3.5h so it doesn't double-fire with the startup warmup.
    @Scheduled(initialDelay = 3 * 60 * 60 * 1000 + 30 * 60 * 1000, fixedDelay = 3 * 60 * 60 * 1000 + 30 * 60 * 1000) // 3.5h
    public void warmOnSchedule() {
        warm();
    }

    private void warm() {
        log.info("Cache warmup starting...");
        long start = System.currentTimeMillis();
        try {
            publicModelService.getPlatformStats();
            publicModelService.getModelsWithWltpData(false);
            // Model ranking (/modelle) lists every model with data, also on the seed path:
            // a cold miss takes 7 to 9 s, which demo users would otherwise wait for.
            // The ranking for a profile on /modelle runs every listed model through its model stats:
            // cold that took 13.6 s locally (61 models), warm 11 ms. So every listed model is warmed too.
            publicModelService.getTopModels(false).forEach(m ->
                    publicModelService.getModelStats(m.brandDisplayName(), m.modelUrlSlug(), false));
            publicModelService.getTopModels(true);
            publicModelService.getModelsWithoutData(false);
            publicModelService.getModelsWithoutData(true);
            publicModelService.getMostEfficientModels(12, false);
            publicModelService.getLongestRangeModels(12, false);
            publicModelService.getBrandModels("Tesla", false);
            publicModelService.getModelStats("Tesla", "Model 3", false);
            publicModelService.getModelStats("Tesla", "Model Y", false);
            log.info("Cache warmup done in {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Cache warmup failed (non-fatal): {}", e.getMessage());
        }
    }
}
