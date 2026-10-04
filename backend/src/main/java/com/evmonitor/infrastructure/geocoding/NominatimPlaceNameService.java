package com.evmonitor.infrastructure.geocoding;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.application.PlaceNameService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ortsteil, Dorf oder Stadt fuer die Mitte einer Geohash-Zelle, per Nominatim-Reverse-Geocoding.
 * Nominatim-Regeln: hoechstens eine Anfrage je Sekunde, eigener User-Agent, Antworten duerfen
 * gecacht werden. Gefragt wird nur die Zellmitte (600 m bzw. 150 m grob), nie eine Nutzerposition.
 * Fehlschlaege landen nicht im Cache - der Name kommt beim naechsten Mal nach.
 */
@Service
@Slf4j
public class NominatimPlaceNameService implements PlaceNameService {

    /** Zoom 14 ist die Ortsteil-Ebene: bei 600-m-Zellen die ehrlichste Aussage, eine Strasse waere oft die falsche. */
    static final int ZOOM = 14;
    /** Von fein nach grob: was Nominatim fuer die Umgebung fuehrt, vom Ortsteil bis zur Gemeinde. */
    static final List<String> LEVELS = List.of("suburb", "neighbourhood", "village", "hamlet", "town", "city", "municipality");
    private static final long MIN_GAP_MS = 1_100;

    private final RestTemplate http;
    private final String baseUrl;
    private final String userAgent;
    private final boolean enabled;
    private final Cache<String, String> names = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterWrite(Duration.ofDays(30)).build();
    private final Object gate = new Object();
    private long lastRequestAt;

    public NominatimPlaceNameService(@Qualifier("nominatimRestTemplate") RestTemplate http,
                                     @Value("${nominatim.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
                                     @Value("${nominatim.user-agent:ev-monitor.net (kontakt@ev-monitor.net)}") String userAgent,
                                     @Value("${nominatim.enabled:true}") boolean enabled) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
        this.enabled = enabled;
    }

    @Override
    public Optional<String> nameFor(String geohash) {
        if (!enabled || geohash == null || geohash.isBlank()) return Optional.empty();
        String cached = names.getIfPresent(geohash);
        if (cached != null) return Optional.of(cached);
        Optional<String> name = lookup(geohash);
        name.ifPresent(n -> names.put(geohash, n));
        return name;
    }

    private Optional<String> lookup(String geohash) {
        WGS84Point center;
        try {
            center = GeoHash.fromGeohashString(geohash.trim()).getBoundingBoxCenter();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/reverse")
                .queryParam("lat", String.format(Locale.ROOT, "%.4f", center.getLatitude()))
                .queryParam("lon", String.format(Locale.ROOT, "%.4f", center.getLongitude()))
                .queryParam("zoom", ZOOM).queryParam("format", "jsonv2").build().toUri();
        try {
            throttle();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers()), Map.class).getBody();
            return pick(body);
        } catch (RestClientException e) {
            log.info("Nominatim ohne Antwort fuer Zelle {}: {}", geohash, e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> pick(Map<String, Object> body) {
        if (body == null || !(body.get("address") instanceof Map<?, ?> address)) return Optional.empty();
        for (String level : LEVELS) {
            Object v = address.get(level);
            if (v instanceof String s && !s.isBlank()) return Optional.of(s.trim());
        }
        return Optional.empty();
    }

    /** Nominatim erlaubt eine Anfrage je Sekunde - alle Nutzer teilen sich diese eine Leitung. */
    private void throttle() {
        synchronized (gate) {
            long wait = lastRequestAt + MIN_GAP_MS - System.currentTimeMillis();
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    /** Der User-Agent ist Pflicht laut Nutzungsbedingungen. */
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.USER_AGENT, userAgent);
        return h;
    }
}
