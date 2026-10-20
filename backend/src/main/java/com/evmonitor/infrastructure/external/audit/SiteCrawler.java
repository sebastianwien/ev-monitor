package com.evmonitor.infrastructure.external.audit;

import com.evmonitor.application.seo.SeoAuditProperties;
import com.evmonitor.application.seo.SeoPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holt Sitemap und Seiten von der eigenen Domain mit Googlebot-kompatiblem User-Agent, damit nginx an den
 * Prerender weiterreicht. Folgt keinen Redirects (eine umgeleitete Sitemap-URL ist ein Befund) und ruft nur
 * Pfade unter {@code seo.audit.base-url} ab, nie fremde Hosts.
 */
@Service
@Slf4j
public class SiteCrawler {

    /** status 0 = nicht erreichbar oder Antwort zu groß; body dann null. */
    public record Fetched(int status, String body) {
    }

    private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");

    private final SeoAuditProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public SiteCrawler(SeoAuditProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    SiteCrawler(SeoAuditProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** Normalisierte Pfade aller {@code <loc>} in Sitemap-Reihenfolge, ohne Dubletten. */
    public List<String> sitemapPaths() {
        Fetched sitemap = fetch("/sitemap.xml");
        if (sitemap.status() != 200 || sitemap.body() == null) {
            throw new SiteCrawlerException("Sitemap nicht lesbar: HTTP " + sitemap.status());
        }
        Set<String> paths = new LinkedHashSet<>();
        Matcher m = LOC.matcher(sitemap.body());
        while (m.find()) paths.add(SeoPath.normalize(m.group(1)));
        if (paths.isEmpty()) throw new SiteCrawlerException("Sitemap enthält keine URL");
        return new ArrayList<>(paths);
    }

    public Fetched fetch(String path) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(stripSlash(properties.getBaseUrl()) + encode(path)))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("User-Agent", properties.getUserAgent())
                    .header("Accept", "text/html,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("SEO-Audit: Pfad {} ist keine gültige URL", path);
            return new Fetched(0, null);
        }
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                byte[] bytes = in.readNBytes(properties.getMaxBytes() + 1);
                if (bytes.length > properties.getMaxBytes()) {
                    log.warn("SEO-Audit: {} größer als {} Bytes, abgebrochen", path, properties.getMaxBytes());
                    return new Fetched(0, null);
                }
                return new Fetched(response.statusCode(), new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            log.warn("SEO-Audit: {} nicht erreichbar: {}", path, e.getMessage());
            return new Fetched(0, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Fetched(0, null);
        }
    }

    private static String stripSlash(String base) {
        String b = base.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    /** Pfadsegmente URL-kodieren (Umlaute, Leerzeichen), Schrägstriche bleiben. */
    static String encode(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            segments.add(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", segments);
    }

}
