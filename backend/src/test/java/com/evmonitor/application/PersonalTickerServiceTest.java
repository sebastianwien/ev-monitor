package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.domain.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalTickerServiceTest {

    @Mock
    private LeaderboardQueryRepository queryRepository;
    @Mock
    private EvLogStatisticsService statisticsService;
    @Mock
    private LeaderboardService leaderboardService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PersonalTickerService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(queryRepository.getTopCarMonthSummary(eq(userId), any(), any())).thenReturn(Optional.empty());
        when(statisticsService.getPeerBenchmark(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(leaderboardService.getMyBestStanding(userId)).thenReturn(Optional.empty());
        when(userRepository.isLeaderboardVisible(userId)).thenReturn(true);
    }

    @Test
    void userWithoutLogs_getsEmptyList() {
        assertThat(service.getItems(userId)).isEmpty();
        verify(statisticsService, never()).getPeerBenchmark(any(), any(), any(), any());
    }

    @Test
    void monthSummary_becomesPersonalItem() {
        givenMonth(12, 9, "187.4");

        List<TickerItemDTO> items = service.getItems(userId);

        assertThat(items).hasSize(1);
        TickerItemDTO item = items.get(0);
        assertThat(item.type()).isEqualTo("PERSONAL");
        assertThat(item.variant()).isEqualTo("personal");
        assertThat(item.messageKey()).isEqualTo("my_month");
        assertThat(item.params())
                .containsEntry("kwh", "187")
                .containsEntry("charges", "12")
                .containsEntry("homePercent", "75")
                .containsEntry("month", String.valueOf(LocalDate.now().getMonthValue()));
    }

    @Test
    void consumption_shownWithThreePeersFromThisMonth_forOwnTopCarOnly() {
        givenMonth(5, 5, "100");
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        when(statisticsService.getPeerBenchmark(eq(carId), eq(userId), eq(monthStart), any()))
                .thenReturn(Optional.of(benchmark("16.84", "18.2", 3, false)));

        TickerItemDTO item = byKey(service.getItems(userId), "my_consumption");

        assertThat(item.params()).containsEntry("mine", "16.8").containsEntry("peers", "18.2");
    }

    @Test
    void consumption_hiddenWithFewerThanThreePeers() {
        givenMonth(5, 5, "100");
        when(statisticsService.getPeerBenchmark(any(), any(), any(), any()))
                .thenReturn(Optional.of(benchmark("16.8", "18.2", 2, false)));

        assertThat(keys(service.getItems(userId))).doesNotContain("my_consumption");
    }

    @Test
    void consumption_hiddenWhenPeerValueIsLifetimeFallback() {
        givenMonth(5, 5, "100");
        when(statisticsService.getPeerBenchmark(any(), any(), any(), any()))
                .thenReturn(Optional.of(benchmark("16.8", "18.2", 8, true)));

        assertThat(keys(service.getItems(userId))).doesNotContain("my_consumption");
    }

    @Test
    void consumption_hiddenWithoutOwnValue() {
        givenMonth(5, 5, "100");
        when(statisticsService.getPeerBenchmark(any(), any(), any(), any()))
                .thenReturn(Optional.of(benchmark(null, "18.2", 8, false)));

        assertThat(keys(service.getItems(userId))).doesNotContain("my_consumption");
    }

    @Test
    void rank_withGap() {
        when(leaderboardService.getMyBestStanding(userId)).thenReturn(Optional.of(
                new MyBestStanding(LeaderboardCategory.MONTHLY_KWH, 3, new BigDecimal("210.4"), new BigDecimal("12.5"))));

        TickerItemDTO item = byKey(service.getItems(userId), "my_rank");

        assertThat(item.params())
                .containsEntry("category", "MONTHLY_KWH")
                .containsEntry("rank", "3")
                .containsEntry("gap", "12.5");
    }

    @Test
    void rank_tiedWithPlaceAhead_getsOwnSentence() {
        when(leaderboardService.getMyBestStanding(userId)).thenReturn(Optional.of(
                new MyBestStanding(LeaderboardCategory.MONTHLY_KWH, 3, new BigDecimal("250.0"), new BigDecimal("0.0"))));

        TickerItemDTO item = byKey(service.getItems(userId), "my_rank_tied");

        assertThat(item.params()).containsEntry("category", "MONTHLY_KWH").containsEntry("rank", "3")
                .doesNotContainKey("gap");
    }

    @Test
    void rank_leaderGetsOwnSentence() {
        when(leaderboardService.getMyBestStanding(userId)).thenReturn(Optional.of(
                new MyBestStanding(LeaderboardCategory.MONTHLY_CHARGES, 1, new BigDecimal("14"), null)));

        TickerItemDTO item = byKey(service.getItems(userId), "my_rank_leader");

        assertThat(item.params()).containsEntry("category", "MONTHLY_CHARGES").doesNotContainKey("gap");
    }

    @Test
    void rank_hiddenWhenLeaderboardInvisible() {
        when(userRepository.isLeaderboardVisible(userId)).thenReturn(false);
        when(leaderboardService.getMyBestStanding(userId)).thenReturn(Optional.of(
                new MyBestStanding(LeaderboardCategory.MONTHLY_KWH, 3, new BigDecimal("210.4"), new BigDecimal("12.5"))));

        assertThat(service.getItems(userId)).isEmpty();
    }

    // ---- helpers ----

    private void givenMonth(long charges, long home, String kwh) {
        when(queryRepository.getTopCarMonthSummary(eq(userId), any(), any()))
                .thenReturn(Optional.of(new MonthChargeSummary(carId, charges, home, new BigDecimal(kwh))));
    }

    private static EvLogStatisticsResponse.PeerBenchmark benchmark(String mine, String peers, int peerUsers, boolean lifetime) {
        return new EvLogStatisticsResponse.PeerBenchmark(
                mine == null ? null : new BigDecimal(mine), new BigDecimal(peers), null, null,
                peerUsers, 20, 20, EvLogStatisticsResponse.PeerBenchmark.MatchType.SPEC, lifetime, false);
    }

    private static TickerItemDTO byKey(List<TickerItemDTO> items, String key) {
        return items.stream().filter(i -> key.equals(i.messageKey())).findFirst()
                .orElseThrow(() -> new AssertionError("kein Eintrag " + key + " in " + items));
    }

    private static List<String> keys(List<TickerItemDTO> items) {
        return items.stream().map(TickerItemDTO::messageKey).toList();
    }
}
