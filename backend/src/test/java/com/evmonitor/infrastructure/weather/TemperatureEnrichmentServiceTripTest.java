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
    void enrichTrip_withValidGeohash_fetchesAndPersistsTemperature() {
        UUID tripId = UUID.randomUUID();
        // u2ewmk = Vienna area (~48.2°N, 16.4°E)
        String geohash = "u2ewmk";
        LocalDateTime startedAt = LocalDateTime.of(2026, 4, 20, 16, 0);

        when(temperatureService.getTemperature(anyDouble(), anyDouble(), eq(startedAt)))
                .thenReturn(Optional.of(14.5));

        enrichmentService.enrichTrip(tripId, geohash, startedAt);

        ArgumentCaptor<BigDecimal> tempCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(temperatureService).getTemperature(anyDouble(), anyDouble(), eq(startedAt));
        verify(evTripRepository).updateTemperature(eq(tripId), tempCaptor.capture());
        assertThat(tempCaptor.getValue()).isEqualByComparingTo("14.5");
    }

    @Test
    void enrichTrip_whenTemperatureServiceReturnsEmpty_doesNotUpdate() {
        UUID tripId = UUID.randomUUID();
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), any()))
                .thenReturn(Optional.empty());

        enrichmentService.enrichTrip(tripId, "u2ewmk", LocalDateTime.now());

        verify(evTripRepository, never()).updateTemperature(any(), any());
    }

    @Test
    void enrichTrip_withNullGeohash_doesNothing() {
        enrichmentService.enrichTrip(UUID.randomUUID(), null, LocalDateTime.now());

        verifyNoInteractions(temperatureService, evTripRepository);
    }

    @Test
    void enrichTrip_withBlankGeohash_doesNothing() {
        enrichmentService.enrichTrip(UUID.randomUUID(), "", LocalDateTime.now());

        verifyNoInteractions(temperatureService, evTripRepository);
    }

    @Test
    void enrichTrip_whenTemperatureServiceThrows_doesNotPropagateException() {
        when(temperatureService.getTemperature(anyDouble(), anyDouble(), any()))
                .thenThrow(new RuntimeException("Open-Meteo unavailable"));

        enrichmentService.enrichTrip(UUID.randomUUID(), "u2ewmk", LocalDateTime.now());

        verify(evTripRepository, never()).updateTemperature(any(), any());
    }
}
