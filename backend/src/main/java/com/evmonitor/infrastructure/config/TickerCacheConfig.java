package com.evmonitor.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Eigene Laufzeiten für die Ticker-Caches neben dem globalen 4-h-Default:
 * "Heute" 15 Minuten (sonst stünde abends "heute" für gestern, Opt-out wirkt schnell),
 * persönliche Einträge je Nutzer eine Stunde.
 */
@Configuration
public class TickerCacheConfig {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> tickerCacheCustomizer() {
        return cacheManager -> {
            cacheManager.registerCustomCache("tickerToday", Caffeine.newBuilder()
                    .maximumSize(1)
                    .expireAfterWrite(Duration.ofMinutes(15))
                    .build());
            cacheManager.registerCustomCache("tickerPersonal", Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(Duration.ofHours(1))
                    .build());
        };
    }
}
