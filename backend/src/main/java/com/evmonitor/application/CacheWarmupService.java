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

    // Re-warm slightly before the 4h Caffeine TTL so the cache never goes cold
    @Scheduled(fixedDelay = 3 * 60 * 60 * 1000 + 30 * 60 * 1000) // 3.5h
    public void warmOnSchedule() {
        warm();
    }

    private void warm() {
        log.info("Cache warmup starting...");
        long start = System.currentTimeMillis();
        try {
            publicModelService.getPlatformStats();
            publicModelService.getModelsWithWltpData(false);
            publicModelService.getTopModels(12, false);
            publicModelService.getMostEfficientModels(12, false);
            publicModelService.getBrandModels("Tesla", false);
            publicModelService.getModelStats("Tesla", "Model 3", false);
            publicModelService.getModelStats("Tesla", "Model Y", false);
            log.info("Cache warmup done in {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Cache warmup failed (non-fatal): {}", e.getMessage());
        }
    }
}
