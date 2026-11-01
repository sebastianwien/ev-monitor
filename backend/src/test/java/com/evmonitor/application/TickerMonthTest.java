package com.evmonitor.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class TickerMonthTest {

    private final LocalDate today = LocalDate.of(2026, 11, 1);

    @Test
    void parse_withoutValue_isCurrentMonth() {
        assertThat(TickerMonth.parse(null, today)).contains(YearMonth.of(2026, 11));
        assertThat(TickerMonth.parse("  ", today)).contains(YearMonth.of(2026, 11));
    }

    @Test
    void parse_acceptsCurrentAndPreviousMonth() {
        assertThat(TickerMonth.parse("2026-11", today)).contains(YearMonth.of(2026, 11));
        assertThat(TickerMonth.parse("2026-10", today)).contains(YearMonth.of(2026, 10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09", "2026-12", "2025-11", "10/2026", "2026-10-01", "abc"})
    void parse_rejectsOtherMonthsAndFormats(String raw) {
        assertThat(TickerMonth.parse(raw, today)).isEmpty();
    }

    @Test
    void previousMonth_coversTheWholeMonth() {
        TickerMonth m = TickerMonth.of(YearMonth.of(2026, 10), today);

        assertThat(m.firstDay()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(m.lastDay()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(m.start()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(m.end()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(m.monthParam()).isEqualTo("10");
    }

    @Test
    void currentMonth_endsToday_likeTheTickerAlwaysDid() {
        TickerMonth m = TickerMonth.of(YearMonth.of(2026, 11), today);

        assertThat(m.firstDay()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(m.lastDay()).isEqualTo(today);
        assertThat(m.end()).isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
    }

    @Test
    void currentMonth_onItsLastDay_endsAtNextMonth() {
        TickerMonth m = TickerMonth.of(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 30));

        assertThat(m.lastDay()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(m.end()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
    }
}
