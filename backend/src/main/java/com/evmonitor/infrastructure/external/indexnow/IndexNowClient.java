package com.evmonitor.infrastructure.external.indexnow;

import com.evmonitor.application.seo.SeoAuditProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * IndexNow: meldet geänderte URLs an Bing, Yandex, Seznam und Naver (ein Aufruf, die Suchmaschinen teilen die Meldung).
 * Der Schlüssel ist öffentlich, er liegt unter {@link #KEY_PATH} und beweist nur, dass die Meldung vom Host kommt.
 */
@Service
@Slf4j
public class IndexNowClient {

    public static final String KEY_PATH = "/indexnow-key.txt";
    private static final String API = "https://api.indexnow.org/indexnow";
    /** Laut Protokoll 8 bis 128 Zeichen aus a-z, A-Z, 0-9 und Bindestrich. */
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9-]{8,128}");
    private static final int MAX_URLS = 10_000;

    private final SeoAuditProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public IndexNowClient(SeoAuditProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    IndexNowClient(SeoAuditProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        String key = properties.getIndexnowKey();
        return key != null && KEY_FORMAT.matcher(key.trim()).matches();
    }

    /** Meldet Seitenpfade des Hosts. true, wenn alles angenommen wurde (oder nichts zu melden war). */
    public boolean submit(List<String> paths) {
        if (!isEnabled()) return false;
        if (paths.isEmpty()) return true;
        String host = properties.getSiteHost();
        boolean ok = true;
        for (int from = 0; from < paths.size(); from += MAX_URLS) {
            List<String> urls = paths.subList(from, Math.min(paths.size(), from + MAX_URLS)).stream()
                    .map(p -> "https://" + host + p)
                    .toList();
            ok &= send(host, urls);
        }
        return ok;
    }

    private boolean send(String host, List<String> urls) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "host", host,
                    "key", properties.getIndexnowKey().trim(),
                    "keyLocation", "https://" + host + KEY_PATH,
                    "urlList", urls));
            HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            if (status == 200 || status == 202) return true;
            log.warn("IndexNow: HTTP {} für {} URLs", status, urls.size());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("IndexNow fehlgeschlagen: {}", e.getMessage());
            return false;
        }
    }
}
