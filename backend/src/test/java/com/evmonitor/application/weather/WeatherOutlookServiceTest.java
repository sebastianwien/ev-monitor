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
        List<Double> week = List.of(12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0);
        List<String> dates = java.util.stream.IntStream.rangeClosed(1, 14).mapToObj(d -> String.format("2026-11-%02d", d)).toList();
        when(temperatures.getDailyWeather(anyDouble(), anyDouble(), eq(7), eq(7)))
                .thenReturn(new TemperatureService.DailyWeather(dates,
                        concat(week, List.of(6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0)),
                        concat(week, List.of(1.0, 2.0, -1.0, 3.0, 2.0, 1.0, 0.0)),
                        concat(week, List.of(9.0, 10.0, 8.0, 7.0, 11.0, 9.0, 8.0)),
                        List.of(0, 0, 0, 0, 0, 0, 0, 3, 61, 71, 2, 1, 80, 45),
                        concat(week, List.of(20.0, 30.0, 10.0, 50.0, 15.0, 12.0, 8.0))));

        WeatherOutlook o = service.outlook(userId).orElseThrow();

        // 7 Tage zurueck gegen 7 Tage voraus, dazu jeder Tag der kommenden Woche
        assertThat(o.deltaC()).isEqualTo(-6.0);
        assertThat(o.aheadDays()).isEqualTo(7);
        assertThat(o.days()).hasSize(7);
        assertThat(o.days().get(0)).isEqualTo(new WeatherOutlook.Day("2026-11-08", 1.0, 9.0, 3, 20.0));
        verify(temperatures).getDailyWeather(doubleThat(lat -> lat > 52.4 && lat < 52.6),
                doubleThat(lon -> lon > 13.3 && lon < 13.5), eq(7), eq(7));
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
        when(temperatures.getDailyWeather(anyDouble(), anyDouble(), anyInt(), anyInt())).thenThrow(new RuntimeException("429"));

        assertThat(service.outlook(userId)).isEmpty();
    }

    private static List<Double> concat(List<Double> a, List<Double> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
