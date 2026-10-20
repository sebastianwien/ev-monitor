package com.evmonitor.infrastructure.external.gsc;

import com.evmonitor.application.seo.GscProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Lesender Zugriff auf die Search-Console-API {@code searchanalytics.query} per Service-Account.
 * Token-Tausch ohne Google-SDK: signiertes JWT (RS256, jjwt) gegen ein Access-Token, eine Stunde gültig.
 */
@Service
@Slf4j
public class GscClient {

    public static final int ROW_LIMIT = 25_000;
    private static final String SCOPE = "https://www.googleapis.com/auth/webmasters.readonly";
    private static final String API = "https://searchconsole.googleapis.com/webmasters/v3/sites/";
    private static final Duration JWT_LIFETIME = Duration.ofMinutes(10);
    private static final Duration TOKEN_SAFETY = Duration.ofMinutes(2);

    /** Eine Zeile der Antwort: Dimensionswerte in Abfragereihenfolge plus Kennzahlen. */
    public record GscRow(List<String> keys, int clicks, int impressions, double position) {
    }

    private final GscProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final int rowLimit;

    private volatile GscServiceAccount account;
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    @Autowired
    public GscClient(GscProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                Clock.systemUTC(), ROW_LIMIT);
    }

    GscClient(GscProperties properties, ObjectMapper objectMapper, HttpClient httpClient, Clock clock, int rowLimit) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
        this.rowLimit = rowLimit;
    }

    public boolean isEnabled() {
        return properties.isConfigured();
    }

    /**
     * Alle Zeilen für das Datumsfenster und die Dimensionen, über {@code startRow} durchpaginiert.
     * Dimensionen z. B. {@code date, page} oder {@code date, query, page}.
     */
    public List<GscRow> query(LocalDate from, LocalDate to, List<String> dimensions) {
        List<GscRow> all = new ArrayList<>();
        int startRow = 0;
        while (true) {
            List<GscRow> page = queryPage(from, to, dimensions, startRow);
            all.addAll(page);
            if (page.size() < rowLimit) return all;
            startRow += rowLimit;
        }
    }

    private List<GscRow> queryPage(LocalDate from, LocalDate to, List<String> dimensions, int startRow) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "startDate", from.toString(),
                    "endDate", to.toString(),
                    "dimensions", dimensions,
                    "rowLimit", rowLimit,
                    "startRow", startRow,
                    "dataState", "final"));
            String site = URLEncoder.encode(properties.getSiteUrl(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API + site + "/searchAnalytics/query"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GscClientException("GSC query HTTP " + response.statusCode() + ": " + shorten(response.body()));
            }
            return parseRows(objectMapper.readTree(response.body()));
        } catch (GscClientException e) {
            throw e;
        } catch (Exception e) {
            throw new GscClientException("GSC query failed: " + e.getMessage(), e);
        }
    }

    private List<GscRow> parseRows(JsonNode root) {
        List<GscRow> rows = new ArrayList<>();
        for (JsonNode r : root.path("rows")) {
            List<String> keys = new ArrayList<>();
            for (JsonNode k : r.path("keys")) keys.add(k.asText());
            rows.add(new GscRow(keys, r.path("clicks").asInt(), r.path("impressions").asInt(),
                    r.path("position").asDouble()));
        }
        return rows;
    }

    private synchronized String token() throws Exception {
        Instant now = clock.instant();
        if (accessToken != null && now.plus(TOKEN_SAFETY).isBefore(tokenExpiresAt)) return accessToken;
        if (account == null) account = GscServiceAccount.parse(properties.getServiceAccountJson(), objectMapper);

        String assertion = Jwts.builder()
                .issuer(account.clientEmail())
                .audience().single(account.tokenUri())
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(JWT_LIFETIME)))
                .signWith(account.privateKey(), Jwts.SIG.RS256)
                .compact();
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(account.tokenUri()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new GscClientException("GSC token HTTP " + response.statusCode() + ": " + shorten(response.body()));
        }
        JsonNode json = objectMapper.readTree(response.body());
        accessToken = json.path("access_token").asText();
        tokenExpiresAt = now.plusSeconds(json.path("expires_in").asLong(3600));
        return accessToken;
    }

    private static String shorten(String body) {
        if (body == null) return "";
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
