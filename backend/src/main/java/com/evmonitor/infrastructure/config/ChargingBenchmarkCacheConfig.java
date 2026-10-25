package com.evmonitor.infrastructure.config;

import com.evmonitor.application.ChargingBenchmarkCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ChargingBenchmarkCacheConfig {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> chargingBenchmarkCacheCustomizer() {
        // Ein Eintrag: die Ladungen aller Nutzer, fuer alle gleich
        return cacheManager -> cacheManager.registerCustomCache(ChargingBenchmarkCache.CACHE, Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofHours(1))
                .build());
    }
}
