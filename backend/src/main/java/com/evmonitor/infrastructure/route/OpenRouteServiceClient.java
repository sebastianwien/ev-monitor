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
 * Nutzer geht nicht an einen Dritten. Uebertragen werden Koordinaten aus gerundeten
 * Geohash-Mittelpunkten, ohne Nutzerbezug und ohne Zeitstempel - entweder Start und Ziel
 * einer Fahrt, oder ihre Stuetzpunkte, wenn die Route entlang der gefahrenen Spur laufen soll.
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

    /** Grenze des Dienstes: mehr Wegpunkte quittiert er mit Fehler 2004. */
    public static final int MAX_WAYPOINTS = 50;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public OpenRouteServiceClient(RestTemplate restTemplate,
                                  @Value("${openrouteservice.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Eine gerechnete Route: die Linie und ihre Laenge.
     *
     * @param polyline       encodierte Polyline (Google-Format, 5 Nachkommastellen)
     * @param distanceMeters Laenge der Route laut Router - der Massstab, an dem sich messen
     *                       laesst, ob sie zur gefahrenen Strecke passt
     */
    public record Route(String polyline, double distanceMeters) {}

    /**
     * Liefert die Route zwischen zwei Punkten. Leeres Optional bei fehlendem Key, Fehler des
     * Routers oder Antwort ohne Route - der Aufrufer behandelt alle drei Faelle gleich.
     */
    public Optional<Route> route(double startLat, double startLon, double endLat, double endLon) {
        return route(List.of(new double[]{startLat, startLon}, new double[]{endLat, endLon}));
    }

    /**
     * Route ueber alle uebergebenen Punkte in ihrer Reihenfolge - der Router verbindet sie
     * entlang echter Strassen. Damit wird aus einer Folge von Messpunkten eine fahrbare Linie.
     *
     * <p>Der Dienst faehrt jeden Punkt exakt an. Liegt einer neben der tatsaechlich befahrenen
     * Strasse, baut die Route dort einen Umweg - das ist der Preis dafuer, echtes Map Matching
     * mit den Mitteln eines Routers nachzubilden.
     *
     * @param waypoints Punkte als {@code [lat, lon]}, mindestens zwei, hoechstens
     *                  {@value #MAX_WAYPOINTS}
     */
    @SuppressWarnings("unchecked")
    public Optional<Route> route(List<double[]> waypoints) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        if (waypoints == null || waypoints.size() < 2 || waypoints.size() > MAX_WAYPOINTS) {
            return Optional.empty();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, apiKey);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        // ORS erwartet Laenge vor Breite.
        Map<String, Object> body = Map.of(
                "coordinates", waypoints.stream().map(p -> List.of(p[1], p[0])).toList());
        try {
            Map<String, Object> response =
                    restTemplate.postForObject(DIRECTIONS_URL, new HttpEntity<>(body, headers), Map.class);
            List<Map<String, Object>> routes = response == null
                    ? List.of()
                    : (List<Map<String, Object>>) response.getOrDefault("routes", List.of());
            if (routes.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> first = routes.get(0);
            Object geometry = first.get("geometry");
            if (!(geometry instanceof String s) || s.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Route(s, distanceMeters(first)));
        } catch (Exception e) {
            log.warn("openrouteservice lieferte keine Route: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Laenge aus {@code summary.distance}; 0, wenn der Router sie nicht mitschickt. */
    @SuppressWarnings("unchecked")
    private static double distanceMeters(Map<String, Object> route) {
        Object summary = route.get("summary");
        if (summary instanceof Map<?, ?> map && map.get("distance") instanceof Number distance) {
            return distance.doubleValue();
        }
        return 0;
    }
}
