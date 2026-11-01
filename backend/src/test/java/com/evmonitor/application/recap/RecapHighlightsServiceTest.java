package com.evmonitor.application.recap;

import com.evmonitor.application.EvLogStatisticsResponse.PeerBenchmark;
import com.evmonitor.application.EvLogStatisticsResponse.PeerBenchmark.MatchType;
import com.evmonitor.application.EvLogStatisticsService;
import com.evmonitor.application.LeaderboardService;
import com.evmonitor.application.MyLeaderboardStandingDTO;
import com.evmonitor.application.recap.RecapHighlights.Place;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecapHighlightsServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final LeaderboardService leaderboardService = mock(LeaderboardService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EvLogStatisticsService statisticsService = mock(EvLogStatisticsService.class);
    private final RecapHighlightsService service = new RecapHighlightsService(leaderboardService, userRepository, statisticsService);

    private final UUID userId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(userRepository.isLeaderboardVisible(userId)).thenReturn(true);
        when(leaderboardService.getMyStandings(any(), any())).thenReturn(List.of());
        when(statisticsService.getPeerBenchmark(any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    private static MyLeaderboardStandingDTO standing(LeaderboardCategory category, Integer rank) {
        return new MyLeaderboardStandingDTO(category, category.getDisplayName(), category.getUnit(), false, rank,
                rank == null ? null : BigDecimal.ONE, null, false, null);
    }

    @Test
    void podium_namesAllPodiumPlacesSortedByRank_fromTheWholeMonth() {
        when(leaderboardService.getMyStandings(eq(userId), eq(LocalDate.of(2026, 10, 31)))).thenReturn(List.of(
                standing(LeaderboardCategory.MONTHLY_KWH, 3),
                standing(LeaderboardCategory.MONTHLY_DISTANCE, 1),
                standing(LeaderboardCategory.MONTHLY_CHARGES, 4),
                standing(LeaderboardCategory.MONTHLY_NIGHT_OWL, null)));

        RecapHighlights h = service.highlights(userId, carId, OCTOBER);

        assertThat(h.places()).containsExactly(
                new Place(LeaderboardCategory.MONTHLY_DISTANCE, 1),
                new Place(LeaderboardCategory.MONTHLY_KWH, 3));
    }

    @Test
    void noPodium_namesOnlyTheBestPlaceWithinTop10() {
        assertThat(RecapHighlightsService.recapPlaces(List.of(
                standing(LeaderboardCategory.MONTHLY_KWH, 9), standing(LeaderboardCategory.MONTHLY_DISTANCE, 7))))
                .containsExactly(new Place(LeaderboardCategory.MONTHLY_DISTANCE, 7));
        assertThat(RecapHighlightsService.recapPlaces(List.of(standing(LeaderboardCategory.MONTHLY_KWH, 10))))
                .containsExactly(new Place(LeaderboardCategory.MONTHLY_KWH, 10));
        assertThat(RecapHighlightsService.recapPlaces(List.of(
                standing(LeaderboardCategory.MONTHLY_KWH, 11), standing(LeaderboardCategory.MONTHLY_DISTANCE, 40)))).isEmpty();
        assertThat(RecapHighlightsService.recapPlaces(List.of())).isEmpty();
    }

    @Test
    void leaderboardOptOut_showsNoPlaces() {
        when(userRepository.isLeaderboardVisible(userId)).thenReturn(false);

        assertThat(service.highlights(userId, carId, OCTOBER).places()).isEmpty();
        verify(leaderboardService, never()).getMyStandings(any(), any());
    }

    @Test
    void peerConsumption_sameMonthSameModel_withAtLeastThreeOtherUsers() {
        when(statisticsService.getPeerBenchmark(eq(carId), eq(userId), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(Optional.of(benchmark("15.2", "16.8", 3, false)));

        RecapHighlights h = service.highlights(userId, carId, OCTOBER);

        assertThat(h.peer().mine()).isEqualByComparingTo("15.2");
        assertThat(h.peer().peers()).isEqualByComparingTo("16.8");
    }

    @Test
    void peerConsumption_hiddenWithFewerUsersOrLifetimeFallback() {
        when(statisticsService.getPeerBenchmark(any(), any(), any(), any())).thenReturn(Optional.of(benchmark("15.2", "16.8", 2, false)));
        assertThat(service.highlights(userId, carId, OCTOBER).peer()).isNull();

        when(statisticsService.getPeerBenchmark(any(), any(), any(), any())).thenReturn(Optional.of(benchmark("15.2", "16.8", 5, true)));
        assertThat(service.highlights(userId, carId, OCTOBER).peer()).isNull();
    }

    @Test
    void failingSource_dropsOnlyItsLine() {
        when(leaderboardService.getMyStandings(any(), any())).thenThrow(new IllegalStateException("db"));
        when(statisticsService.getPeerBenchmark(any(), any(), any(), any())).thenReturn(Optional.of(benchmark("15.2", "16.8", 3, false)));

        RecapHighlights h = service.highlights(userId, carId, OCTOBER);

        assertThat(h.places()).isEmpty();
        assertThat(h.peer()).isNotNull();
        assertThat(h.isEmpty()).isFalse();
    }

    private static PeerBenchmark benchmark(String mine, String peers, int users, boolean lifetime) {
        return new PeerBenchmark(new BigDecimal(mine), new BigDecimal(peers), null, null, users, 20, 20, MatchType.SPEC, lifetime, false);
    }
}
