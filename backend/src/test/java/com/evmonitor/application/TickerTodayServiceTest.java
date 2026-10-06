package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TickerTodayServiceTest {

    @Mock
    private LeaderboardQueryRepository queryRepository;

    @InjectMocks
    private TickerTodayService service;

    @Test
    void noCharges_noItems() {
        when(queryRepository.getTodayPublicCharges(any(), any())).thenReturn(List.of());

        assertThat(service.getTodayItems()).isEmpty();
    }

    @Test
    void window_isTheBerlinCalendarDay_inUtc() {
        when(queryRepository.getTodayPublicCharges(any(), any())).thenReturn(List.of());
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);

        service.getTodayItems();

        verify(queryRepository).getTodayPublicCharges(start.capture(), end.capture());
        // logged_at liegt als UTC in der DB: Berliner Mitternacht muss als UTC-Zeitpunkt ankommen.
        LocalTime berlinStart = start.getValue().atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.of("Europe/Berlin")).toLocalTime();
        assertThat(berlinStart).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(Duration.between(start.getValue(), end.getValue()).toHours()).isBetween(23L, 25L);
    }

    @Test
    void atMostTwo_oneChargePerUser_withPriceParams() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(queryRepository.getTodayPublicCharges(any(), any())).thenReturn(List.of(
                new TodayChargeRow(a, new BigDecimal("42.0"), new BigDecimal("16.38"), "Ionity"),
                new TodayChargeRow(a, new BigDecimal("10.0"), new BigDecimal("5.00"), null),
                new TodayChargeRow(b, new BigDecimal("25.55"), new BigDecimal("12.00"), null),
                new TodayChargeRow(UUID.randomUUID(), new BigDecimal("30.0"), new BigDecimal("15.00"), null)));

        List<TickerItemDTO> items = service.getTodayItems();

        assertThat(items).hasSize(2);
        TickerItemDTO first = items.get(0);
        assertThat(first.type()).isEqualTo("STAT");
        assertThat(first.messageKey()).isEqualTo("today_charge");
        assertThat(first.params()).isEqualTo(Map.of("kwh", "42", "cost", "16.38", "ct", "39", "provider", "Ionity"));

        TickerItemDTO second = items.get(1);
        assertThat(second.messageKey()).as("ohne Anbieter eigener Satz").isEqualTo("today_charge_anon");
        assertThat(second.params()).isEqualTo(Map.of("kwh", "26", "cost", "12.00", "ct", "47"));
    }
}
