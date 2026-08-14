package com.evmonitor.infrastructure.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches ambient temperature from Open-Meteo (https://open-meteo.com).
 * Free, no API key, GDPR-compliant (only anonymous coordinates sent).
 *
 * <p>Strategie:
 * <ul>
 *   <li>Zeitpunkte aelter als 5 Tage: Archive-Endpoint (ERA5-Reanalyse, akkurat)</li>
 *   <li>Zeitpunkte juenger als 5 Tage: Forecast-Endpoint (Modell-Output, deckt past_days
 *       und Zukunft ab)</li>
 * </ul>
 *
 * <p>Beide Endpoints beantworten {@code start_hour}/{@code end_hour}: die gesuchte Stunde steht
 * in der Anfrage, die Antwort enthaelt genau einen Wert. Frueher wurde ein ganzer Tag geholt und
 * die Stunde als Index herausgegriffen - zusammen mit {@code timezone=auto} (Antwort in ORTSZEIT)
 * und einer Stunde aus der Server-Zone (Prod: UTC) lag der gelesene Wert im Sommer zwei Stunden
 * daneben. Alle Zeiten laufen deshalb explizit in UTC.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemperatureService {

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";

    // Open-Meteo archive only has data up to ~5 days ago
    private static final int ARCHIVE_THRESHOLD_DAYS = 5;

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");

    private final RestTemplate restTemplate;

    /**
     * Returns temperature in °C at the given coordinates and datetime.
     *
     * @param at Zeitpunkt in Server-Zone (so, wie er in der DB steht) - wird fuer die Abfrage
     *           nach UTC umgerechnet.
     */
    public Optional<Double> getTemperature(double latitude, double longitude, LocalDateTime at) {
        if (at == null) return Optional.empty();
        ZonedDateTime utc = at.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(ARCHIVE_THRESHOLD_DAYS);
        String baseUrl = utc.toLocalDate().isBefore(cutoff) ? ARCHIVE_URL : FORECAST_URL;

        try {
            return fetchHour(baseUrl, latitude, longitude, utc.format(HOUR));
        } catch (Exception e) {
            log.warn("Failed to fetch temperature for ({}, {}) at {}: {}", latitude, longitude, at, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Double> fetchHour(String baseUrl, double latitude, double longitude, String hourUtc) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "temperature_2m")
                .queryParam("start_hour", hourUtc)
                .queryParam("end_hour", hourUtc)
                .queryParam("timezone", "UTC")
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return Optional.empty();

        Map<String, Object> hourly = (Map<String, Object>) response.get("hourly");
        if (hourly == null) return Optional.empty();

        List<Number> temperatures = (List<Number>) hourly.get("temperature_2m");
        if (temperatures == null || temperatures.isEmpty()) return Optional.empty();

        Number temp = temperatures.get(0);
        return temp != null ? Optional.of(temp.doubleValue()) : Optional.empty();
    }
}
