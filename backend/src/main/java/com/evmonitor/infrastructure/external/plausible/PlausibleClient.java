package com.evmonitor.infrastructure.external.plausible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plausible Stats API v2 ({@code POST /api/v2/query}). Eine Abfrage = Metriken x Dimensionen über ein
 * Datumsfenster, optional auf Goals gefiltert; durchpaginiert bis zur kurzen Seite. Key nie loggen.
 */
@Service
@Slf4j
public class PlausibleClient {

    public static final int ROW_LIMIT = 10_000;
    private static final String API = "https://plausible.io/api/v2/query";

    /** Eine Zeile: Dimensionswerte in Abfragereihenfolge, Metriken in Abfragereihenfolge. */
    public record Row(List<String> dimensions, List<Double> metrics) {
        public String dim(int i) {
            return dimensions.get(i);
        }

        public double metric(int i) {
            return metrics.get(i);
        }
    }

    /** goals: null = ohne Filter, sonst {@code event:goal} ist eines davon. */
    public record Query(List<String> metrics, LocalDate from, LocalDate to, List<String> dimensions, List<String> goals) {
    }

    private final String apiKey;
    private final String siteId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int rowLimit;

    @Autowired
    public PlausibleClient(@Value("${plausible.api-key:}") String apiKey,
                           @Value("${plausible.site-id:ev-monitor.net}") String siteId,
                           ObjectMapper objectMapper) {
        this(apiKey, siteId, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), ROW_LIMIT);
    }

    PlausibleClient(String apiKey, String siteId, ObjectMapper objectMapper, HttpClient httpClient, int rowLimit) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.siteId = siteId;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.rowLimit = rowLimit;
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    public List<Row> query(Query q) {
        List<Row> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<Row> page = queryPage(q, offset);
            all.addAll(page);
            if (page.size() < rowLimit) return all;
            offset += rowLimit;
        }
    }

    private List<Row> queryPage(Query q, int offset) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("site_id", siteId);
            body.put("metrics", q.metrics());
            body.put("date_range", List.of(q.from().toString(), q.to().toString()));
            body.put("dimensions", q.dimensions());
            if (q.goals() != null && !q.goals().isEmpty()) {
                body.put("filters", List.of(List.of("is", "event:goal", q.goals())));
            }
            body.put("pagination", Map.of("limit", rowLimit, "offset", offset));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new PlausibleClientException("Plausible query HTTP " + response.statusCode() + ": " + shorten(response.body()));
            }
            return parseRows(objectMapper.readTree(response.body()));
        } catch (PlausibleClientException e) {
            throw e;
        } catch (Exception e) {
            throw new PlausibleClientException("Plausible query failed: " + e.getMessage(), e);
        }
    }

    private static List<Row> parseRows(JsonNode root) {
        List<Row> rows = new ArrayList<>();
        for (JsonNode r : root.path("results")) {
            List<String> dims = new ArrayList<>();
            for (JsonNode d : r.path("dimensions")) dims.add(d.asText());
            List<Double> metrics = new ArrayList<>();
            for (JsonNode m : r.path("metrics")) metrics.add(m.isNull() ? 0.0 : m.asDouble());
            rows.add(new Row(dims, metrics));
        }
        return rows;
    }

    private static String shorten(String body) {
        if (body == null) return "";
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
