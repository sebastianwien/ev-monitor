package com.evmonitor.infrastructure.config;

import com.evmonitor.application.DriveConsumptionService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Modellschnitt des Fahrverbrauchs: Summen je Auto eines Modells und Zeitraums, 24 h wie die Peer-Seite.
 * Ein Eintrag je Modell und Zeitraum, nicht je Nutzer; der Fragende fällt beim Lesen heraus.
 */
@Configuration
public class DriveConsumptionCacheConfig {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> driveConsumptionCacheCustomizer() {
        return cacheManager -> cacheManager.registerCustomCache(DriveConsumptionService.MODEL_CACHE, Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build());
    }
}
