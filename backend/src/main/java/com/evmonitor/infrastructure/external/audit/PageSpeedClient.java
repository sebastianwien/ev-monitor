package com.evmonitor.infrastructure.external.audit;

import com.evmonitor.application.seo.SeoAudit.Vitals;
import com.evmonitor.application.seo.SeoAuditProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * PageSpeed Insights API v5, Strategie mobil. Bevorzugt Felddaten der URL (CrUX, 28 Tage echte Chrome-Nutzer),
 * sonst die Lighthouse-Messung ohne INP. Die Request-URL enthält den Key und wird nie geloggt.
 */
@Service
@Slf4j
public class PageSpeedClient {

    private static final String API = "https://www.googleapis.com/pagespeedonline/v5/runPagespeed";

    private final SeoAuditProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PageSpeedClient(SeoAuditProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    PageSpeedClient(SeoAuditProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        return properties.getPagespeedApiKey() != null && !properties.getPagespeedApiKey().isBlank();
    }

    /** Leer bei HTTP-Fehler, Timeout oder unlesbarer Antwort. */
    public Optional<Vitals> measure(String publicUrl) {
        String uri = API + "?url=" + URLEncoder.encode(publicUrl, StandardCharsets.UTF_8)
                + "&strategy=mobile&category=performance&key=" + URLEncoder.encode(properties.getPagespeedApiKey().trim(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(120)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("PageSpeed für {}: HTTP {}", publicUrl, response.statusCode());
                return Optional.empty();
            }
            return parse(objectMapper.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("PageSpeed für {} fehlgeschlagen: {}", publicUrl, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<Vitals> parse(JsonNode root) {
        JsonNode field = root.path("loadingExperience");
        JsonNode metrics = field.path("metrics");
        if (!field.path("origin_fallback").asBoolean(false) && metrics.has("LARGEST_CONTENTFUL_PAINT_MS")) {
            Integer lcp = intOrNull(metrics.path("LARGEST_CONTENTFUL_PAINT_MS").path("percentile"));
            JsonNode clsNode = metrics.path("CUMULATIVE_LAYOUT_SHIFT_SCORE").path("percentile");
            Double cls = clsNode.isNumber() ? clsNode.asDouble() / 100.0 : null;
            Integer inp = intOrNull(metrics.path("INTERACTION_TO_NEXT_PAINT").path("percentile"));
            return Optional.of(new Vitals(lcp, cls, inp, "FIELD"));
        }
        JsonNode audits = root.path("lighthouseResult").path("audits");
        JsonNode lcpNode = audits.path("largest-contentful-paint").path("numericValue");
        if (!lcpNode.isNumber()) return Optional.empty();
        JsonNode clsNode = audits.path("cumulative-layout-shift").path("numericValue");
        return Optional.of(new Vitals((int) Math.round(lcpNode.asDouble()), clsNode.isNumber() ? clsNode.asDouble() : null, null, "LAB"));
    }

    private static Integer intOrNull(JsonNode n) {
        return n.isNumber() ? n.asInt() : null;
    }
}
