package com.evmonitor.infrastructure.external.dataforseo;

import com.evmonitor.application.seo.SeoCompetitionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * DataForSEO API v3, Live-Modus (synchron, eine Aufgabe je Anfrage). Jede Antwort trägt ihre Kosten in US-Dollar,
 * die der Aufrufer gegen das Monatsbudget rechnet. Zugangsdaten nie loggen, nie in Fehlertexte.
 */
@Service
public class DataForSeoClient {

    private static final String API = "https://api.dataforseo.com/v3";
    private static final int OK = 20000;

    /** Ergebnis plus Kosten des Abrufs. */
    public record Paid<T>(T value, double costUsd) {
    }

    /** Organischer Treffer; rank = Platz unter den organischen Ergebnissen, domain ohne "www.". */
    public record SerpItem(int rank, String domain, String url, String title) {
    }

    /** cpc in US-Dollar; fehlende Werte null. */
    public record KeywordData(String keyword, Integer searchVolume, Double cpc, String competition) {
    }

    private final SeoCompetitionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DataForSeoClient(SeoCompetitionProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    DataForSeoClient(SeoCompetitionProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Paid<List<SerpItem>> serp(String keyword) {
        ObjectNode task = task();
        task.put("keyword", keyword);
        task.put("device", properties.getDevice());
        task.put("depth", properties.getSerpDepth());
        JsonNode t = post("/serp/google/organic/live/regular", task);
        List<SerpItem> items = new ArrayList<>();
        for (JsonNode result : t.path("result")) {
            for (JsonNode item : result.path("items")) {
                if (!"organic".equals(item.path("type").asText())) continue;
                items.add(new SerpItem(item.path("rank_group").asInt(), domain(item.path("domain").asText()),
                        item.path("url").asText(), textOrNull(item.path("title"))));
            }
        }
        return new Paid<>(items, t.path("cost").asDouble());
    }

    /** Bis zu 1 000 Begriffe je Abruf, Preis unabhängig von der Anzahl. */
    public Paid<List<KeywordData>> searchVolume(List<String> keywords) {
        ObjectNode task = task();
        ArrayNode list = task.putArray("keywords");
        keywords.forEach(list::add);
        JsonNode t = post("/keywords_data/google_ads/search_volume/live", task);
        List<KeywordData> data = new ArrayList<>();
        for (JsonNode r : t.path("result")) {
            data.add(new KeywordData(r.path("keyword").asText(), intOrNull(r.path("search_volume")),
                    doubleOrNull(r.path("cpc")), textOrNull(r.path("competition"))));
        }
        return new Paid<>(data, t.path("cost").asDouble());
    }

    public Paid<List<KeywordData>> suggestions(String seed, int limit) {
        ObjectNode task = task();
        task.put("keyword", seed);
        task.put("limit", limit);
        JsonNode t = post("/dataforseo_labs/google/keyword_suggestions/live", task);
        List<KeywordData> data = new ArrayList<>();
        for (JsonNode result : t.path("result")) {
            for (JsonNode item : result.path("items")) {
                JsonNode info = item.path("keyword_info");
                data.add(new KeywordData(item.path("keyword").asText(), intOrNull(info.path("search_volume")),
                        doubleOrNull(info.path("cpc")), textOrNull(info.path("competition_level"))));
            }
        }
        return new Paid<>(data, t.path("cost").asDouble());
    }

    private ObjectNode task() {
        ObjectNode task = objectMapper.createObjectNode();
        task.put("location_code", properties.getLocationCode());
        task.put("language_code", properties.getLanguageCode());
        return task;
    }

    /** Schickt eine Aufgabe und gibt die erste Aufgabe der Antwort zurück; wirft bei HTTP- oder API-Fehler. */
    private JsonNode post(String path, ObjectNode task) {
        String auth = Base64.getEncoder().encodeToString(
                (properties.getLogin().trim() + ":" + properties.getPassword().trim()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(API + path))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(List.of(task))))
                    .build();
        } catch (IOException e) {
            throw new DataForSeoException("Anfrage nicht serialisierbar", e);
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DataForSeoException(path + " nicht erreichbar: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataForSeoException(path + " unterbrochen", e);
        }
        if (response.statusCode() != 200) {
            throw new DataForSeoException(path + ": HTTP " + response.statusCode());
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new DataForSeoException(path + ": Antwort ist kein JSON", e);
        }
        JsonNode t = root.path("tasks").path(0);
        int status = t.path("status_code").asInt(root.path("status_code").asInt());
        if (status != OK) {
            String message = t.path("status_message").asText(root.path("status_message").asText(""));
            throw new DataForSeoException(path + ": " + status + " " + message);
        }
        return t;
    }

    private static String domain(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private static Integer intOrNull(JsonNode n) {
        return n.isNumber() ? n.asInt() : null;
    }

    private static Double doubleOrNull(JsonNode n) {
        return n.isNumber() ? n.asDouble() : null;
    }

    private static String textOrNull(JsonNode n) {
        return n.isTextual() ? n.asText() : null;
    }
}
