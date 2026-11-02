package com.evmonitor.infrastructure.external;

import com.evmonitor.infrastructure.persistence.FuelPriceMonthEntity;
import com.evmonitor.infrastructure.persistence.JpaFuelPriceMonthRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FuelPriceServiceTest {

    private final JpaFuelPriceMonthRepository months = mock(JpaFuelPriceMonthRepository.class);
    private final FuelPriceService service = new FuelPriceService(new ObjectMapper(), months);

    @Test
    void withoutApiKey_refreshKeepsFallbackPrices_andStoresNoMonth() {
        service.refresh();

        assertThat(service.getBenzinPrice()).isEqualTo(2.15);
        assertThat(service.getDieselPrice()).isEqualTo(2.32);
        assertThat(service.getAvgFuelPrice()).isEqualTo((2.15 + 2.32) / 2.0);
        verify(months, never()).save(any());
    }

    @Test
    void fetchedPrice_overwritesTheRowOfTheRunningMonth() {
        // 31.10. 23:30 UTC ist in Berlin schon der 1.11.: die Zeile gehört zum November.
        service.apply(2.061, 1.95, Instant.parse("2026-10-31T23:30:00Z"));

        ArgumentCaptor<FuelPriceMonthEntity> saved = ArgumentCaptor.forClass(FuelPriceMonthEntity.class);
        verify(months).save(saved.capture());
        assertThat(saved.getValue().getMonth()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(saved.getValue().getBenzinEurPerL()).isEqualByComparingTo("2.061");
        assertThat(service.getBenzinPrice()).isEqualTo(2.061);
    }

    @Test
    void priceOfAMonth_isTheStoredOne_elseTodaysPrice() {
        when(months.findById(LocalDate.of(2026, 10, 1))).thenReturn(Optional.of(
                new FuelPriceMonthEntity(LocalDate.of(2026, 10, 1), new BigDecimal("2.060"), Instant.now())));
        when(months.findById(LocalDate.of(2026, 9, 1))).thenReturn(Optional.empty());

        assertThat(service.getBenzinPrice(YearMonth.of(2026, 10))).isEqualTo(2.06);
        assertThat(service.getBenzinPrice(YearMonth.of(2026, 9))).isEqualTo(2.15);
    }

    @Test
    void storageFailure_neverBreaksThePrice() {
        when(months.findById(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
        when(months.save(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));

        service.apply(2.0, 1.9, Instant.parse("2026-10-15T06:00:00Z"));

        assertThat(service.getBenzinPrice()).isEqualTo(2.0);
        assertThat(service.getBenzinPrice(YearMonth.of(2026, 10))).isEqualTo(2.0);
    }

    @Test
    void snapshot_carriesThePreviousMonthsFrozenPrice_elseTodaysPrice() {
        LocalDate previous = YearMonth.now(java.time.ZoneId.of("Europe/Berlin")).minusMonths(1).atDay(1);
        when(months.findById(previous)).thenReturn(Optional.of(
                new FuelPriceMonthEntity(previous, new BigDecimal("2.060"), Instant.now())));
        assertThat(service.snapshot().previousMonthPetrolEurPerLitre()).isEqualTo(2.06);

        when(months.findById(previous)).thenReturn(Optional.empty());
        assertThat(service.snapshot().previousMonthPetrolEurPerLitre()).isEqualTo(2.15);
    }

    @Test
    void combustionConsumptionIsOneSharedConstant() {
        assertThat(FuelPriceService.COMBUSTION_LITERS_PER_100_KM).isEqualByComparingTo("7.0");
    }
}
