package com.evmonitor.infrastructure.weather;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Zeitpunkte kommen als {@link LocalDateTime} in Server-Zone herein (Prod laeuft auf UTC, ein
 * Entwicklerrechner auf Europe/Berlin). Die Zone wird hier fest auf Europe/Berlin gestellt, damit
 * die Umrechnung ueberhaupt sichtbar wird - mit UTC als Default waere der Zonenfehler unsichtbar.
 */
class TemperatureServiceTest {

    private static TimeZone originalTimeZone;

    private RestTemplate restTemplate;
    private TemperatureService service;

    @BeforeAll
    static void fixTimeZone() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new TemperatureService(restTemplate);
    }

    private void respondWith(Double... temperatures) {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(
                Map.of("hourly", Collections.singletonMap("temperature_2m", java.util.Arrays.asList(temperatures))));
    }

    /**
     * Regression: frueher wurde ein ganzer Tag geholt und die Stunde als Index herausgegriffen -
     * bei {@code timezone=auto} lieferte Open-Meteo das Array aber in ORTSZEIT, waehrend die
     * Stunde aus der Server-Zone kam. Auf dem UTC-Server bekam eine Ladung um 12:05 CEST so die
     * Temperatur von 10:00 Uhr morgens. Jetzt steht die gesuchte Stunde in der Anfrage.
     */
    @Test
    void fragtGenauDieGesuchteStundeInUtcAb() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 14, 12, 5); // 12:05 CEST = 10:05 UTC
        respondWith(27.2);

        assertThat(service.getTemperature(52.53, 13.46, at)).contains(27.2);
        verify(restTemplate).getForObject(argThat((String url) ->
                url.contains("start_hour=2026-08-14T10:00")
                && url.contains("end_hour=2026-08-14T10:00")
                && url.contains("timezone=UTC")
                && !url.contains("timezone=auto")
        ), eq(Map.class));
    }

    /** Kurz nach Mitternacht Ortszeit liegt der Zeitpunkt in UTC noch am Vortag. */
    @Test
    void rechnetUeberDieTagesgrenzeKorrektNachUtc() {
        LocalDateTime at = LocalDateTime.of(2026, 4, 21, 1, 30); // 01:30 CEST = 23:30 UTC am 20.
        respondWith(8.4);

        assertThat(service.getTemperature(48.2, 16.4, at)).contains(8.4);
        verify(restTemplate).getForObject(contains("start_hour=2026-04-20T23:00"), eq(Map.class));
    }

    @Test
    void nutztDenArchivEndpointFuerAeltereZeitpunkte() {
        LocalDateTime at = LocalDateTime.of(2026, 4, 20, 14, 30);
        when(restTemplate.getForObject(contains("archive-api.open-meteo.com"), eq(Map.class)))
                .thenReturn(Map.of("hourly", Map.of("temperature_2m", List.of(9.3))));

        assertThat(service.getTemperature(48.2, 16.4, at)).contains(9.3);
        verify(restTemplate).getForObject(argThat((String url) ->
                url.contains("archive-api.open-meteo.com")
                && url.contains("start_hour=2026-04-20T12:00")
                && url.contains("hourly=temperature_2m")
        ), eq(Map.class));
    }

    @Test
    void nutztDenForecastEndpointFuerFrischeZeitpunkte() {
        LocalDateTime at = LocalDateTime.now().minusDays(1).withHour(12).withMinute(0);
        when(restTemplate.getForObject(contains("api.open-meteo.com/v1/forecast"), eq(Map.class)))
                .thenReturn(Map.of("hourly", Map.of("temperature_2m", List.of(19.0))));

        assertThat(service.getTemperature(48.2, 16.4, at)).contains(19.0);
        verify(restTemplate).getForObject(argThat((String url) ->
                url.contains("api.open-meteo.com/v1/forecast") && !url.contains("current=")
        ), eq(Map.class));
    }

    @Test
    void bleibtLeerOhneStundenblockInDerAntwort() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of());

        assertThat(service.getTemperature(48.2, 16.4, LocalDateTime.of(2026, 4, 20, 14, 30))).isEmpty();
    }

    @Test
    void bleibtLeerWennDieAntwortKeinenWertEnthaelt() {
        respondWith();

        assertThat(service.getTemperature(48.2, 16.4, LocalDateTime.of(2026, 4, 20, 14, 30))).isEmpty();
    }

    @Test
    void bleibtLeerWennDerWertNullIst() {
        respondWith((Double) null);

        assertThat(service.getTemperature(48.2, 16.4, LocalDateTime.of(2026, 4, 20, 5, 0))).isEmpty();
    }

    @Test
    void bleibtLeerWennDieAbfrageFehlschlaegt() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("network down"));

        assertThat(service.getTemperature(48.2, 16.4, LocalDateTime.of(2026, 4, 20, 14, 30))).isEmpty();
    }

    @Test
    void bleibtLeerOhneZeitpunkt() {
        assertThat(service.getTemperature(48.2, 16.4, null)).isEmpty();
        verifyNoInteractions(restTemplate);
    }
}
