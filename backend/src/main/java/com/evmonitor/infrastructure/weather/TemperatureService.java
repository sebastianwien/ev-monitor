package com.evmonitor.infrastructure.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches ambient temperature from Open-Meteo (https://open-meteo.com).
 * Free, no API key, GDPR-compliant (only anonymous coordinates sent).
 *
 * <p>Strategie:
 * <ul>
 *   <li>Trips älter als 5 Tage: Archive-Endpoint (ERA5-Reanalyse, akkurat)</li>
 *   <li>Trips jünger als 5 Tage: Forecast-Endpoint mit hourly + start_date/end_date
 *       (Modell-Output, deckt past_days + Zukunft ab)</li>
 * </ul>
 * Beide Endpoints liefern dasselbe JSON-Format ({@code hourly.temperature_2m[]}),
 * so dass die Auswertung gemeinsam erfolgt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemperatureService {

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";

    // Open-Meteo archive only has data up to ~5 days ago
    private static final int ARCHIVE_THRESHOLD_DAYS = 5;

    private final RestTemplate restTemplate;

    /**
     * Returns temperature in °C at the given coordinates and datetime.
     */
    public Optional<Double> getTemperature(double latitude, double longitude, LocalDateTime at) {
        LocalDate date = at.toLocalDate();
        LocalDate cutoff = LocalDate.now().minusDays(ARCHIVE_THRESHOLD_DAYS);
        String baseUrl = date.isBefore(cutoff) ? ARCHIVE_URL : FORECAST_URL;

        try {
            return fetchHourly(baseUrl, latitude, longitude, date, at.getHour());
        } catch (Exception e) {
            log.warn("Failed to fetch temperature for ({}, {}) at {}: {}", latitude, longitude, at, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Double> fetchHourly(String baseUrl, double latitude, double longitude, LocalDate date, int hour) {
        String dateStr = date.toString(); // yyyy-MM-dd
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("start_date", dateStr)
                .queryParam("end_date", dateStr)
                .queryParam("hourly", "temperature_2m")
                .queryParam("timezone", "auto")
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return Optional.empty();

        Map<String, Object> hourly = (Map<String, Object>) response.get("hourly");
        if (hourly == null) return Optional.empty();

        List<Number> temperatures = (List<Number>) hourly.get("temperature_2m");
        if (temperatures == null || hour >= temperatures.size()) return Optional.empty();

        Number temp = temperatures.get(hour);
        return temp != null ? Optional.of(temp.doubleValue()) : Optional.empty();
    }
}
