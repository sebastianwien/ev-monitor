package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Bester Monatsrang über alle Kategorien für den persönlichen Ticker-Eintrag. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardBestStandingTest {

    @Mock
    private LeaderboardQueryRepository queryRepository;

    @InjectMocks
    private LeaderboardService leaderboardService;

    private final UUID me = UUID.randomUUID();

    @Test
    void notRankedAnywhere_returnsEmpty() {
        assertThat(leaderboardService.getMyBestStanding(me)).isEmpty();
    }

    @Test
    void picksBestRankAcrossCategories_withGapToPlaceAhead() {
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(List.of(
                row(UUID.randomUUID(), "300.0"), row(UUID.randomUUID(), "250.0"), row(me, "210.4")));
        when(queryRepository.getChargesRanking(any(), any())).thenReturn(List.of(
                row(UUID.randomUUID(), "14"), row(me, "11")));

        Optional<MyBestStanding> best = leaderboardService.getMyBestStanding(me);

        assertThat(best).isPresent();
        assertThat(best.get().category()).isEqualTo(LeaderboardCategory.MONTHLY_CHARGES);
        assertThat(best.get().rank()).isEqualTo(2);
        assertThat(best.get().gapToNext()).isEqualByComparingTo("3");
    }

    @Test
    void leader_hasNoGap() {
        when(queryRepository.getDistanceRanking(any(), any())).thenReturn(List.of(
                row(me, "1200"), row(UUID.randomUUID(), "900")));

        MyBestStanding best = leaderboardService.getMyBestStanding(me).orElseThrow();

        assertThat(best.rank()).isEqualTo(1);
        assertThat(best.gapToNext()).isNull();
    }

    @Test
    void tie_sharesTheHigherRank_andGapCountsToNextBetterValue() {
        UUID first = UUID.randomUUID();
        when(queryRepository.getKwhRanking(any(), any())).thenReturn(List.of(
                row(first, "300.0"), row(UUID.randomUUID(), "250.0"), row(me, "250.0")));

        MyBestStanding best = leaderboardService.getMyBestStanding(me).orElseThrow();

        assertThat(best.rank()).isEqualTo(2);
        assertThat(best.gapToNext()).isEqualByComparingTo("50.0");
    }

    @Test
    void tieAtTheTop_countsAsLeading() {
        when(queryRepository.getChargesRanking(any(), any())).thenReturn(List.of(
                row(UUID.randomUUID(), "14"), row(me, "14")));

        MyBestStanding best = leaderboardService.getMyBestStanding(me).orElseThrow();

        assertThat(best.rank()).isEqualTo(1);
        assertThat(best.gapToNext()).isNull();
    }

    @Test
    void lowerIsBetter_gapIsPositive() {
        when(queryRepository.getCheapestRanking(any(), any())).thenReturn(List.of(
                row(UUID.randomUUID(), "0.21"), row(me, "0.25")));

        MyBestStanding best = leaderboardService.getMyBestStanding(me).orElseThrow();

        assertThat(best.category()).isEqualTo(LeaderboardCategory.MONTHLY_CHEAPEST);
        assertThat(best.gapToNext()).isEqualByComparingTo("0.04");
    }

    private LeaderboardRankRow row(UUID userId, String value) {
        return new LeaderboardRankRow(UUID.randomUUID(), userId, "u", "Tesla Model 3", new BigDecimal(value), null, null);
    }
}
