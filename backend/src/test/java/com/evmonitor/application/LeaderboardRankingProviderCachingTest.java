package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

/**
 * Rankings sind für alle Nutzer gleich und werden deshalb global gecacht. Prüft die Cache-Regel
 * mit echtem Spring-Proxy - ein reiner Mockito-Test sähe den Cache nicht.
 */
@SpringJUnitConfig(LeaderboardRankingProviderCachingTest.TestConfig.class)
class LeaderboardRankingProviderCachingTest {

    @Autowired
    private LeaderboardRankingProvider provider;

    /** Bewusst kein Spring-Bean: Spring würde sonst einen EntityManager in den Mock injizieren wollen. */
    private static final LeaderboardQueryRepository queryRepository = mock(LeaderboardQueryRepository.class);

    @Autowired
    private CacheManager cacheManager;

    private final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 10, 7, 0, 0);

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("leaderboardRanking");
        }

        @Bean
        LeaderboardRankingProvider provider() {
            return new LeaderboardRankingProvider(queryRepository);
        }
    }

    @BeforeEach
    void reset() {
        clearInvocations(queryRepository);
        cacheManager.getCache("leaderboardRanking").clear();
    }

    @Test
    void sameCategoryAndWindow_queriesOnce() {
        provider.getRanking(LeaderboardCategory.MONTHLY_KWH, start, end);
        provider.getRanking(LeaderboardCategory.MONTHLY_KWH, start, end);

        verify(queryRepository, times(1)).getKwhRanking(start, end);
    }

    @Test
    void otherCategoryOrWindow_queriesAgain() {
        provider.getRanking(LeaderboardCategory.MONTHLY_KWH, start, end);
        provider.getRanking(LeaderboardCategory.MONTHLY_CHARGES, start, end);
        provider.getRanking(LeaderboardCategory.MONTHLY_KWH, start, end.plusDays(1));

        verify(queryRepository, times(2)).getKwhRanking(any(), any());
        verify(queryRepository, times(1)).getChargesRanking(start, end);
    }

    @Test
    void freshRanking_bypassesCache_forMonthEndRewards() {
        provider.getRanking(LeaderboardCategory.MONTHLY_KWH, start, end);
        provider.getFreshRanking(LeaderboardCategory.MONTHLY_KWH, start, end);

        verify(queryRepository, times(2)).getKwhRanking(start, end);
    }
}
