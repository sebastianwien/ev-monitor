package com.evmonitor.infrastructure.route;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holt eine Strassenverbindung zwischen zwei Punkten von openrouteservice (HeiGIT, Heidelberg).
 *
 * <p>Der Standard-Plan ist kostenlos und erlaubt 2000 Anfragen pro Tag bei 40 pro Minute.
 * Aufgerufen wird ausschliesslich serverseitig: so bleibt der API-Key geheim und die IP der
 * Nutzer geht nicht an einen Dritten. Uebertragen wird nur ein Koordinatenpaar aus gerundeten
 * Geohash-Mittelpunkten, ohne Nutzerbezug und ohne Zeitstempel.
 *
 * <p>Ergebnisse stehen unter CC-BY-SA 4.0 und verlangen die Nennung
 * "© openrouteservice by HeiGIT | Data from OpenStreetMap" - das Frontend zeigt sie an der Karte.
 *
 * <p>Ohne konfigurierten Key ist die Funktion still abgeschaltet: keine Aufrufe, keine Fehler.
 */
@Component
@Slf4j
public class OpenRouteServiceClient {

    private static final String DIRECTIONS_URL =
            "https://api.openrouteservice.org/v2/directions/driving-car";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public OpenRouteServiceClient(RestTemplate restTemplate,
                                  @Value("${openrouteservice.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Liefert die Route als encodierte Polyline (Google-Format, 5 Nachkommastellen).
     * Leeres Optional bei fehlendem Key, Fehler des Routers oder Antwort ohne Route -
     * der Aufrufer behandelt alle drei Faelle gleich.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> route(double startLat, double startLon, double endLat, double endLon) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, apiKey);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        // ORS erwartet Laenge vor Breite.
        Map<String, Object> body = Map.of(
                "coordinates", List.of(List.of(startLon, startLat), List.of(endLon, endLat)));
        try {
            Map<String, Object> response =
                    restTemplate.postForObject(DIRECTIONS_URL, new HttpEntity<>(body, headers), Map.class);
            List<Map<String, Object>> routes = response == null
                    ? List.of()
                    : (List<Map<String, Object>>) response.getOrDefault("routes", List.of());
            if (routes.isEmpty()) {
                return Optional.empty();
            }
            Object geometry = routes.get(0).get("geometry");
            return geometry instanceof String s && !s.isBlank() ? Optional.of(s) : Optional.empty();
        } catch (Exception e) {
            log.warn("openrouteservice lieferte keine Route: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
