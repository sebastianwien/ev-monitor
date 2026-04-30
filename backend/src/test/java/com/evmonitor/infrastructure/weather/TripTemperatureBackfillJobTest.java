package com.evmonitor.infrastructure.weather;

import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripTemperatureBackfillJobTest {

    @Mock
    EvTripRepository evTripRepository;

    @Mock
    TemperatureService temperatureService;

    @InjectMocks
    TripTemperatureBackfillJob backfillJob;

    @Test
    void run_enrichesTripWithGeohashAndNoTemperature() {
        EvTrip trip = buildTrip("u2ewmk");
        when(evTripRepository.findAllWithGeohashAndNoTemperature()).thenReturn(List.of(trip));
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), any())).thenReturn(Optional.of(12.3));

        String summary = backfillJob.run();

        verify(evTripRepository).updateTemperature(eq(trip.getId()), any(BigDecimal.class));
        assertThat(summary).contains("1 enriched");
    }

    @Test
    void run_countsFailedWhenTemperatureUnavailable() {
        EvTrip trip = mock(EvTrip.class);
        when(trip.getLocationStartGeohash()).thenReturn("u2ewmk");
        when(trip.getTripStartedAt()).thenReturn(OffsetDateTime.parse("2026-04-20T10:00:00Z"));
        when(evTripRepository.findAllWithGeohashAndNoTemperature()).thenReturn(List.of(trip));
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());

        String summary = backfillJob.run();

        verify(evTripRepository, never()).updateTemperature(any(), any());
        assertThat(summary).contains("1 failed");
    }

    @Test
    void run_skipsTripsWithNullStartedAt() {
        EvTrip trip = mock(EvTrip.class);
        when(trip.getTripStartedAt()).thenReturn(null);
        when(evTripRepository.findAllWithGeohashAndNoTemperature()).thenReturn(List.of(trip));

        String summary = backfillJob.run();

        verifyNoInteractions(temperatureService);
        assertThat(summary).contains("0 enriched");
    }

    @Test
    void run_withEmptyList_returnsZeroSummary() {
        when(evTripRepository.findAllWithGeohashAndNoTemperature()).thenReturn(List.of());

        String summary = backfillJob.run();

        assertThat(summary).contains("0 enriched").contains("0 failed");
        verifyNoInteractions(temperatureService);
    }

    private EvTrip buildTrip(String geohash) {
        EvTrip trip = mock(EvTrip.class);
        when(trip.getId()).thenReturn(UUID.randomUUID());
        when(trip.getLocationStartGeohash()).thenReturn(geohash);
        when(trip.getTripStartedAt()).thenReturn(OffsetDateTime.parse("2026-04-20T10:00:00Z"));
        return trip;
    }
}
