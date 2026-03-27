package com.evmonitor.infrastructure.external;

import com.evmonitor.application.PlausibleTrafficRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlausibleService {

    private static final Logger log = LoggerFactory.getLogger(PlausibleService.class);
    private static final String BASE_URL = "https://plausible.io/api/v1/stats/timeseries";

    @Value("${plausible.api-key:}")
    private String apiKey;

    @Value("${plausible.site-id:ev-monitor.net}")
    private String siteId;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public PlausibleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch timeseries data from Plausible.
     * period="day"  → hourly buckets for today
     * period="7d" / "30d" / "90d" → daily buckets for the last N days
     */
    public List<PlausibleTrafficRow> getTimeseries(String period) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Plausible API key not configured");
            return List.of();
        }

        try {
            String url = buildUrl(period);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Plausible API returned status {}: {}", response.statusCode(), response.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");

            List<PlausibleTrafficRow> rows = new ArrayList<>();
            for (JsonNode r : results) {
                rows.add(new PlausibleTrafficRow(
                        r.path("date").asText(),
                        r.path("visitors").asInt(),
                        r.path("pageviews").asInt()
                ));
            }
            return rows;

        } catch (Exception e) {
            log.error("Failed to fetch Plausible timeseries for period={}", period, e);
            return List.of();
        }
    }

    private String buildUrl(String period) {
        if ("day".equals(period)) {
            return BASE_URL + "?site_id=" + siteId + "&period=day&metrics=visitors,pageviews";
        }

        int days = Integer.parseInt(period.replace("d", ""));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1);
        String fromStr = from.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

        return BASE_URL + "?site_id=" + siteId
                + "&period=custom"
                + "&date=" + fromStr + "," + todayStr
                + "&metrics=visitors,pageviews";
    }
}
