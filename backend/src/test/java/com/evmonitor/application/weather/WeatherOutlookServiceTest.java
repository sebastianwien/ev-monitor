package com.evmonitor.application.weather;

import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import com.evmonitor.infrastructure.weather.TemperatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeatherOutlookServiceTest {

    private final UUID userId = UUID.randomUUID();
    private ChargingSavingsQueryRepository places;
    private TemperatureService temperatures;
    private WeatherOutlookService service;

    @BeforeEach
    void setUp() {
        places = mock(ChargingSavingsQueryRepository.class);
        temperatures = mock(TemperatureService.class);
        service = new WeatherOutlookService(places, new WeatherOutlookCache(temperatures));
    }

    /** Anker ist der haeufigste Heimladeort (Geohash u33db, Berlin-Mitte), aufgeloest auf die Zellenmitte. */
    @Test
    void ausblickAmHeimladeort() {
        when(places.homeGeohash(userId)).thenReturn("u33dc0");
        when(temperatures.getDailyMeans(anyDouble(), anyDouble(), eq(7), eq(14)))
                .thenReturn(List.of(12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0,
                        6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0));

        WeatherOutlook o = service.outlook(userId).orElseThrow();

        assertThat(o.deltaC()).isEqualTo(-6.0);
        verify(temperatures).getDailyMeans(doubleThat(lat -> lat > 52.4 && lat < 52.6),
                doubleThat(lon -> lon > 13.3 && lon < 13.5), eq(7), eq(14));
    }

    @Test
    void ohneHeimladeortKeinAusblickUndKeinWetteraufruf() {
        when(places.homeGeohash(userId)).thenReturn(null);

        assertThat(service.outlook(userId)).isEmpty();
        verifyNoInteractions(temperatures);
    }

    @Test
    void wetterausfallIstKeinFehlerSondernKeinAusblick() {
        when(places.homeGeohash(userId)).thenReturn("u33dc0");
        when(temperatures.getDailyMeans(anyDouble(), anyDouble(), anyInt(), anyInt())).thenThrow(new RuntimeException("429"));

        assertThat(service.outlook(userId)).isEmpty();
    }
}
