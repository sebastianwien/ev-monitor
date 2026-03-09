package com.evmonitor.infrastructure.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Service
public class TemperatureService {

    private static final Logger log = LoggerFactory.getLogger(TemperatureService.class);

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";

    // Open-Meteo archive only has data up to ~5 days ago
    private static final int ARCHIVE_THRESHOLD_DAYS = 5;

    private final RestTemplate restTemplate;

    public TemperatureService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Returns temperature in °C at the given coordinates and datetime.
     * Uses forecast API for recent/current times, archive API for historical data.
     */
    public Optional<Double> getTemperature(double latitude, double longitude, LocalDateTime at) {
        LocalDate date = at.toLocalDate();
        LocalDate cutoff = LocalDate.now().minusDays(ARCHIVE_THRESHOLD_DAYS);

        try {
            if (date.isBefore(cutoff)) {
                return fetchHistorical(latitude, longitude, date, at.getHour());
            } else {
                return fetchCurrent(latitude, longitude);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch temperature for ({}, {}) at {}: {}", latitude, longitude, at, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Double> fetchCurrent(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromHttpUrl(FORECAST_URL)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("current", "temperature_2m")
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return Optional.empty();

        Map<String, Object> current = (Map<String, Object>) response.get("current");
        if (current == null) return Optional.empty();

        Object temp = current.get("temperature_2m");
        return temp != null ? Optional.of(((Number) temp).doubleValue()) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<Double> fetchHistorical(double latitude, double longitude, LocalDate date, int hour) {
        String dateStr = date.toString(); // yyyy-MM-dd
        String url = UriComponentsBuilder.fromHttpUrl(ARCHIVE_URL)
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
