package com.evmonitor.infrastructure.weather;

import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.weather.TemperatureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Der Job holt seinen Arbeitsvorrat aus der Datenbank und schreibt jede fertige Zeile mit ihrer
 * Herkunft zurueck. Genau dadurch schrumpft der Vorrat und der Job kommt irgendwann zur Ruhe.
 */
@ExtendWith(MockitoExtension.class)
class TemperatureBackfillJobTest {

    @Mock EvLogRepository evLogRepository;
    @Mock TemperatureService temperatureService;

    private TemperatureBackfillJob job() {
        return new TemperatureBackfillJob(evLogRepository, new TemperatureBackfillRunner(temperatureService));
    }

    @Test
    void schreibtWertUndHerkunftZurueck() {
        UUID id = UUID.randomUUID();
        when(evLogRepository.findTemperatureCandidates(anyInt())).thenReturn(List.of(
                new EvLogRepository.TemperatureCandidate(id, "u33df5x", LocalDateTime.of(2026, 8, 14, 12, 5))));
        when(temperatureService.getHourlyForUtcDay(anyDouble(), anyDouble(), any()))
                .thenReturn(IntStream.range(0, 24).mapToObj(Double::valueOf).toList());

        String summary = job().run();

        verify(evLogRepository).updateTemperature(eq(id), anyDouble(), eq(TemperatureSource.FORECAST));
        assertThat(summary).contains("1 enriched");
    }

    @Test
    void machtNichtsWennDerVorratLeerIst() {
        when(evLogRepository.findTemperatureCandidates(anyInt())).thenReturn(List.of());

        String summary = job().run();

        verifyNoInteractions(temperatureService);
        assertThat(summary).contains("0 enriched");
    }
}
