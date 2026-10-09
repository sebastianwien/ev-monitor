package com.evmonitor.infrastructure.config;

import com.evmonitor.application.rating.PublicModelRatingsService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Fahrerbewertungen auf der Modellseite: 10 Minuten statt des globalen 4-h-Defaults,
 * damit neue Bewertungen zeitnah erscheinen. Freigaben im Admin-Tab leeren den Eintrag sofort.
 */
@Configuration
public class RatingCacheConfig {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> ratingCacheCustomizer() {
        return cacheManager -> cacheManager.registerCustomCache(PublicModelRatingsService.CACHE, Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build());
    }
}
