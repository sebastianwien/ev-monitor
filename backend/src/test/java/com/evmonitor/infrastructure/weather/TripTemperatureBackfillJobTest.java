package com.evmonitor.infrastructure.weather;

import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.weather.TemperatureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Eine Fahrt bekommt das Mittel aus Start- und Endtemperatur; ist nur einer der beiden Orte
 * bekannt, dessen Wert. Der geschriebene Wert traegt immer die Herkunft FORECAST, damit der
 * naechste Lauf ihn nicht erneut einsammelt.
 */
@ExtendWith(MockitoExtension.class)
class TripTemperatureBackfillJobTest {

    private static final String BERLIN = "u33df5x";

    @Mock EvTripRepository evTripRepository;
    @Mock TemperatureService temperatureService;

    private TripTemperatureBackfillJob job() {
        return new TripTemperatureBackfillJob(evTripRepository, new TemperatureBackfillRunner(temperatureService));
    }

    private static OffsetDateTime utc(int hour) {
        return OffsetDateTime.of(2026, 8, 14, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    private void respondWithHourEqualsValue() {
        when(temperatureService.getHourlyForUtcDay(anyDouble(), anyDouble(), any()))
                .thenReturn(IntStream.range(0, 24).mapToObj(Double::valueOf).toList());
    }

    @Test
    void mitteltStartUndEndeUndHaeltDieHerkunftFest() {
        UUID id = UUID.randomUUID();
        when(evTripRepository.findTemperatureCandidates(any()))
                .thenReturn(List.<Object[]>of(new Object[]{id, BERLIN, utc(10), BERLIN, utc(12)}));
        respondWithHourEqualsValue();

        String summary = job().run();

        ArgumentCaptor<BigDecimal> temp = ArgumentCaptor.forClass(BigDecimal.class);
        verify(evTripRepository).updateTemperature(eq(id), temp.capture(), eq(TemperatureSource.FORECAST));
        assertThat(temp.getValue()).isEqualByComparingTo("11.0");
        assertThat(summary).contains("1 enriched");
    }

    @Test
    void kommtMitNurEinemBekanntenOrtAus() {
        UUID id = UUID.randomUUID();
        when(evTripRepository.findTemperatureCandidates(any()))
                .thenReturn(List.<Object[]>of(new Object[]{id, BERLIN, utc(10), null, null}));
        respondWithHourEqualsValue();

        job().run();

        verify(evTripRepository).updateTemperature(eq(id), argThat(t -> t.compareTo(new BigDecimal("10.0")) == 0),
                eq(TemperatureSource.FORECAST));
    }

    @Test
    void ueberspringtFahrtenGanzOhneOrt() {
        when(evTripRepository.findTemperatureCandidates(any()))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), null, null, null, null}));

        String summary = job().run();

        verifyNoInteractions(temperatureService);
        verify(evTripRepository, never()).updateTemperature(any(), any(), any());
        assertThat(summary).contains("0 enriched");
    }
}
