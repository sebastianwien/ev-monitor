package com.evmonitor.infrastructure.weather;

import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemperatureEnrichmentServiceTripTest {

    @Mock
    TemperatureService temperatureService;

    @Mock
    EvLogRepository evLogRepository;

    @Mock
    EvTripRepository evTripRepository;

    @InjectMocks
    TemperatureEnrichmentService enrichmentService;

    @Test
    void enrichTrip_withStartAndEnd_persistsMeanTemperature() {
        UUID tripId = UUID.randomUUID();
        String startGeohash = "u2ewmk"; // Vienna area
        String endGeohash = "u33d0k";   // Bratislava area
        LocalDateTime startedAt = LocalDateTime.of(2026, 4, 20, 16, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 4, 20, 17, 30);

        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(startedAt)))
                .thenReturn(Optional.of(10.0));
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(endedAt)))
                .thenReturn(Optional.of(20.0));

        enrichmentService.enrichTrip(tripId, startGeohash, endGeohash, startedAt, endedAt);

        ArgumentCaptor<BigDecimal> tempCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(temperatureService).getTemperature(anyDouble(), anyDouble(), eq(startedAt));
        verify(temperatureService).getTemperature(anyDouble(), anyDouble(), eq(endedAt));
        verify(evTripRepository).updateTemperature(eq(tripId), tempCaptor.capture());
        assertThat(tempCaptor.getValue()).isEqualByComparingTo("15.0");
    }

    @Test
    void enrichTrip_withOnlyStartParams_persistsStartTemperature() {
        UUID tripId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.of(2026, 4, 20, 16, 0);

        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(startedAt)))
                .thenReturn(Optional.of(14.5));

        enrichmentService.enrichTrip(tripId, "u2ewmk", null, startedAt, null);

        ArgumentCaptor<BigDecimal> tempCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(temperatureService, times(1)).getTemperature(anyDouble(), anyDouble(), eq(startedAt));
        verify(evTripRepository).updateTemperature(eq(tripId), tempCaptor.capture());
        assertThat(tempCaptor.getValue()).isEqualByComparingTo("14.5");
    }

    @Test
    void enrichTrip_whenEndTemperatureUnavailable_fallsBackToStartOnly() {
        UUID tripId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.of(2026, 4, 20, 16, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 4, 20, 17, 0);

        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(startedAt)))
                .thenReturn(Optional.of(12.0));
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(endedAt)))
                .thenReturn(Optional.empty());

        enrichmentService.enrichTrip(tripId, "u2ewmk", "u33d0k", startedAt, endedAt);

        ArgumentCaptor<BigDecimal> tempCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(evTripRepository).updateTemperature(eq(tripId), tempCaptor.capture());
        assertThat(tempCaptor.getValue()).isEqualByComparingTo("12.0");
    }

    @Test
    void enrichTrip_whenStartTemperatureUnavailable_fallsBackToEndOnly() {
        UUID tripId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.of(2026, 4, 20, 16, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 4, 20, 17, 0);

        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(startedAt)))
                .thenReturn(Optional.empty());
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(endedAt)))
                .thenReturn(Optional.of(18.4));

        enrichmentService.enrichTrip(tripId, "u2ewmk", "u33d0k", startedAt, endedAt);

        ArgumentCaptor<BigDecimal> tempCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(evTripRepository).updateTemperature(eq(tripId), tempCaptor.capture());
        assertThat(tempCaptor.getValue()).isEqualByComparingTo("18.4");
    }

    @Test
    void enrichTrip_whenBothTemperaturesUnavailable_doesNotUpdate() {
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), any()))
                .thenReturn(Optional.empty());

        enrichmentService.enrichTrip(UUID.randomUUID(), "u2ewmk", "u33d0k",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));

        verify(evTripRepository, never()).updateTemperature(any(), any());
    }

    @Test
    void enrichTrip_withAllNullLocationInputs_doesNothing() {
        enrichmentService.enrichTrip(UUID.randomUUID(), null, null, LocalDateTime.now(), LocalDateTime.now());

        verifyNoInteractions(temperatureService, evTripRepository);
    }

    @Test
    void enrichTrip_withBlankGeohashes_doesNothing() {
        enrichmentService.enrichTrip(UUID.randomUUID(), "", "  ", LocalDateTime.now(), LocalDateTime.now());

        verifyNoInteractions(temperatureService, evTripRepository);
    }

    @Test
    void enrichTrip_whenTemperatureServiceThrows_doesNotPropagateException() {
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), any()))
                .thenThrow(new RuntimeException("Open-Meteo unavailable"));

        enrichmentService.enrichTrip(UUID.randomUUID(), "u2ewmk", null, LocalDateTime.now(), null);

        verify(evTripRepository, never()).updateTemperature(any(), any());
    }

    @Test
    void enrichTrip_roundsMeanToOneDecimal() {
        UUID tripId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.of(2026, 4, 20, 16, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 4, 20, 17, 0);

        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(startedAt)))
                .thenReturn(Optional.of(10.1));
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(endedAt)))
                .thenReturn(Optional.of(10.4));

        enrichmentService.enrichTrip(tripId, "u2ewmk", "u33d0k", startedAt, endedAt);

        ArgumentCaptor<BigDecimal> tempCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(evTripRepository).updateTemperature(eq(tripId), tempCaptor.capture());
        assertThat(tempCaptor.getValue()).isEqualByComparingTo("10.3"); // (10.1+10.4)/2 = 10.25 → 10.3 HALF_UP
    }
}
