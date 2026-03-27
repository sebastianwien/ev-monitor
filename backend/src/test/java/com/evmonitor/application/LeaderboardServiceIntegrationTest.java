package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.external.ExternalJokeService;
import com.evmonitor.infrastructure.external.FuelPriceService;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeaderboardService ranking logic, delta computation, and own-entry lookup.
 * Uses Mockito to avoid PostgreSQL-native SQL dependency.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardServiceIntegrationTest {

    @Mock
    private LeaderboardQueryRepository queryRepository;

    @Mock
    private CoinLogService coinLogService;

    @Mock
    private ExternalJokeService externalJokeService;

    @Mock
    private FuelPriceService fuelPriceService;

    @InjectMocks
    private LeaderboardService leaderboardService;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(externalJokeService.getJokes()).thenReturn(List.of());
    }

    // ---- Ranking and delta ----

    @Test
    void getLeaderboard_top10ContainsAllEntries_sortedByQueryOrder() {
        var rows = List.of(
                row(userA, "anna", "80.0"),
                row(userB, "bob", "50.0")
        );
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(rows);
        when(queryRepository.getTotalKwhThisMonth(any(), any())).thenReturn(BigDecimal.ZERO);
        when(queryRepository.getTotalChargesThisMonth(any(), any())).thenReturn(0L);

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_KWH, null);

        assertThat(result.entries()).hasSize(2);
        assertThat(result.entries().get(0).rank()).isEqualTo(1);
        assertThat(result.entries().get(0).username()).isEqualTo("anna");
        assertThat(result.entries().get(1).rank()).isEqualTo(2);
    }

    @Test
    void getLeaderboard_rankDeltaPositive_whenUserMovedUp() {
        var todayRanking = List.of(
                row(userA, "anna", "80.0"), // rank 1 today
                row(userB, "bob", "50.0")   // rank 2 today
        );
        var yesterdayRanking = List.of(
                row(userB, "bob", "50.0"),  // rank 1 yesterday
                row(userA, "anna", "40.0")  // rank 2 yesterday
        );
        when(queryRepository.getKwhRanking(any(), any()))
                .thenReturn(todayRanking)
                .thenReturn(yesterdayRanking);

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_KWH, null);

        // anna was rank 2 yesterday, rank 1 today -> delta = +1
        assertThat(result.entries().get(0).rankDelta()).isEqualTo(1);
        // bob was rank 1 yesterday, rank 2 today -> delta = -1
        assertThat(result.entries().get(1).rankDelta()).isEqualTo(-1);
    }

    @Test
    void getLeaderboard_isNew_whenUserNotInYesterdayRanking() {
        var todayRanking = List.of(row(userA, "anna", "80.0"));
        var yesterdayRanking = List.of(row(userB, "bob", "50.0")); // anna not present yesterday

        when(queryRepository.getKwhRanking(any(), any()))
                .thenReturn(todayRanking)
                .thenReturn(yesterdayRanking);

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_KWH, null);

        assertThat(result.entries().get(0).isNew()).isTrue();
        assertThat(result.entries().get(0).rankDelta()).isNull();
    }

    @Test
    void getLeaderboard_noDelta_whenYesterdayRankingEmpty() {
        var todayRanking = List.of(row(userA, "anna", "80.0"));
        when(queryRepository.getKwhRanking(any(), any()))
                .thenReturn(todayRanking)
                .thenReturn(List.of()); // empty = first day of month

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_KWH, null);

        assertThat(result.entries().get(0).rankDelta()).isNull();
        assertThat(result.entries().get(0).isNew()).isFalse();
    }

    // ---- Own entry ----

    @Test
    void getLeaderboard_ownEntryNull_whenNotAuthenticated() {
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(List.of(row(userA, "anna", "80.0")));

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_KWH, null);

        assertThat(result.ownEntry()).isNull();
    }

    @Test
    void getLeaderboard_ownEntryNull_whenUserIsInTop10() {
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(List.of(row(userA, "anna", "80.0")));

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_KWH, userA);

        // userA is in top 10, so ownEntry should be null
        assertThat(result.ownEntry()).isNull();
        assertThat(result.entries().get(0).username()).isEqualTo("anna");
    }

    @Test
    void getLeaderboard_ownEntryPresent_whenUserBeyondTop10() {
        var manyRows = new java.util.ArrayList<LeaderboardRankRow>();
        for (int i = 0; i < 10; i++) {
            manyRows.add(row(UUID.randomUUID(), "filler" + i, "100.0"));
        }
        manyRows.add(row(userB, "bob", "1.0")); // rank 11
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(manyRows);

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_KWH, userB);

        assertThat(result.entries()).hasSize(10);
        assertThat(result.ownEntry()).isNotNull();
        assertThat(result.ownEntry().username()).isEqualTo("bob");
        assertThat(result.ownEntry().rank()).isEqualTo(11);
    }

    // ---- Response metadata ----

    @Test
    void getLeaderboard_responseMetadata_matchesCategory() {
        when(queryRepository.getCheapestRanking(any(), any())).thenReturn(List.of());

        var result = leaderboardService.getLeaderboard(LeaderboardCategory.MONTHLY_CHEAPEST, null);

        assertThat(result.unit()).isEqualTo("ct/kWh");
        assertThat(result.lowerIsBetter()).isTrue();
        assertThat(result.period()).matches("\\d{4}-\\d{2}");
    }

    // ---- Ticker ----

    @Test
    void getTicker_containsStatItem() {
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getChargesRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getDistanceRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getCoinsRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getCheapestRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getNightOwlRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getIceChargerRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getPowerChargerRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getTotalKwhThisMonth(any(), any())).thenReturn(new BigDecimal("1234.5"));
        when(queryRepository.getTotalChargesThisMonth(any(), any())).thenReturn(42L);
        when(queryRepository.getTotalChargeDurationMinutes(any(), any())).thenReturn(1200L);
        when(queryRepository.getTotalCostEur(any(), any())).thenReturn(new BigDecimal("150.00"));
        when(fuelPriceService.getAvgFuelPrice()).thenReturn(1.80);

        var items = leaderboardService.getTicker();

        assertThat(items).anyMatch(i -> "STAT".equals(i.type()));
    }

    @Test
    void getTicker_includesLeaderItem_whenCategoryHasEntries() {
        var row = row(userA, "anna", "99.0");
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(List.of(row));
        when(queryRepository.getChargesRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getDistanceRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getCoinsRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getCheapestRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getNightOwlRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getIceChargerRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getPowerChargerRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getTotalKwhThisMonth(any(), any())).thenReturn(BigDecimal.ZERO);
        when(queryRepository.getTotalChargesThisMonth(any(), any())).thenReturn(0L);

        var items = leaderboardService.getTicker();

        assertThat(items).anyMatch(i -> "LEADER".equals(i.type()) && i.text().contains("anna"));
    }

    // ---- Month-end rewards ----

    @Test
    void awardMonthEndRewards_awardsTop3ForEligibleCategories() {
        UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID(), u3 = UUID.randomUUID();
        var ranking = List.of(
                row(u1, "first", "100.0"),
                row(u2, "second", "80.0"),
                row(u3, "third", "60.0")
        );
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getChargesRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getDistanceRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getCoinsRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getCheapestRanking(any(), any())).thenReturn(ranking);
        when(coinLogService.hasEverReceivedCoinForAction(any(), any())).thenReturn(false);

        leaderboardService.awardMonthEndRewards(LocalDate.of(2025, 12, 1));

        // 4 categories with hasMonthEndReward=true (KWH, CHARGES, DISTANCE, CHEAPEST), 3 users each = 12 awardCoins calls
        verify(coinLogService, times(12)).awardCoins(any(), any(), anyInt(), any());
    }

    @Test
    void awardMonthEndRewards_skipsAlreadyAwarded() {
        var ranking = List.of(row(userA, "anna", "100.0"));
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getChargesRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getDistanceRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getCoinsRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getCheapestRanking(any(), any())).thenReturn(ranking);
        when(coinLogService.hasEverReceivedCoinForAction(any(), any())).thenReturn(true); // already awarded

        leaderboardService.awardMonthEndRewards(LocalDate.of(2025, 11, 1));

        verify(coinLogService, never()).awardCoins(any(), any(), anyInt(), any());
    }

    @Test
    void awardMonthEndRewards_skipsNonRewardCategories() {
        var ranking = List.of(row(userA, "anna", "5.0"));
        when(queryRepository.getNightOwlRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getIceChargerRanking(any(), any())).thenReturn(ranking);
        when(queryRepository.getPowerChargerRanking(any(), any())).thenReturn(ranking);
        // Main categories return empty
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getChargesRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getDistanceRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getCoinsRanking(any(), any())).thenReturn(List.of());
        when(queryRepository.getCheapestRanking(any(), any())).thenReturn(List.of());

        leaderboardService.awardMonthEndRewards(LocalDate.of(2025, 10, 1));

        // Night owl, ice charger, power charger have hasMonthEndReward=false -> no awards
        verify(coinLogService, never()).awardCoins(any(), any(), anyInt(), any());
    }

    // ---- Helper ----

    private LeaderboardRankRow row(UUID userId, String username, String value) {
        return new LeaderboardRankRow(userId, username, new BigDecimal(value));
    }
}
