package com.evmonitor.infrastructure.weather;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemperatureServiceTest {

    private RestTemplate restTemplate;
    private TemperatureService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new TemperatureService(restTemplate);
    }

    @Test
    void getTemperature_forOlderDate_callsArchiveEndpointAndReturnsCorrectHour() {
        LocalDateTime at = LocalDateTime.of(2026, 4, 20, 14, 30);
        List<Double> hourly = generateHourly(); // 0..23 → temperature index*1.0
        when(restTemplate.getForObject(contains("archive-api.open-meteo.com"), eq(Map.class)))
                .thenReturn(Map.of("hourly", Map.of("temperature_2m", hourly)));

        Optional<Double> temp = service.getTemperature(48.2, 16.4, at);

        assertThat(temp).contains(14.0); // hour 14
        verify(restTemplate).getForObject(argThat((String url) ->
                url.contains("archive-api.open-meteo.com")
                && url.contains("start_date=2026-04-20")
                && url.contains("end_date=2026-04-20")
                && url.contains("hourly=temperature_2m")
        ), eq(Map.class));
    }

    @Test
    void getTemperature_forRecentDate_usesForecastHourlyNotCurrent() {
        LocalDateTime at = LocalDateTime.now().minusDays(1).withHour(9).withMinute(0);
        List<Double> hourly = generateHourly();
        when(restTemplate.getForObject(contains("api.open-meteo.com/v1/forecast"), eq(Map.class)))
                .thenReturn(Map.of("hourly", Map.of("temperature_2m", hourly)));

        Optional<Double> temp = service.getTemperature(48.2, 16.4, at);

        assertThat(temp).contains(9.0);
        verify(restTemplate).getForObject(argThat((String url) ->
                url.contains("api.open-meteo.com/v1/forecast")
                && url.contains("hourly=temperature_2m")
                && url.contains("start_date=")
                && url.contains("end_date=")
                && !url.contains("current=")
        ), eq(Map.class));
    }

    @Test
    void getTemperature_returnsEmpty_whenArchiveResponseHasNoHourlyData() {
        LocalDateTime at = LocalDateTime.of(2026, 4, 20, 14, 30);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of());

        Optional<Double> temp = service.getTemperature(48.2, 16.4, at);

        assertThat(temp).isEmpty();
    }

    @Test
    void getTemperature_returnsEmpty_whenHourIndexExceedsArray() {
        LocalDateTime at = LocalDateTime.of(2026, 4, 20, 23, 0);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("hourly", Map.of("temperature_2m", List.of(1.0, 2.0))));

        Optional<Double> temp = service.getTemperature(48.2, 16.4, at);

        assertThat(temp).isEmpty();
    }

    @Test
    void getTemperature_returnsEmpty_whenRestTemplateThrows() {
        LocalDateTime at = LocalDateTime.of(2026, 4, 20, 14, 30);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("network down"));

        Optional<Double> temp = service.getTemperature(48.2, 16.4, at);

        assertThat(temp).isEmpty();
    }

    @Test
    void getTemperature_handlesNullValueInHourlyArray() {
        LocalDateTime at = LocalDateTime.of(2026, 4, 20, 5, 0);
        List<Double> hourly = new java.util.ArrayList<>(generateHourly());
        hourly.set(5, null);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("hourly", java.util.Collections.singletonMap("temperature_2m", hourly)));

        Optional<Double> temp = service.getTemperature(48.2, 16.4, at);

        assertThat(temp).isEmpty();
    }

    private static List<Double> generateHourly() {
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(Double::valueOf)
                .toList();
    }
}
