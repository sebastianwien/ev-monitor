package com.evmonitor.infrastructure.web;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.ChargingProviderTariffResponse;
import com.evmonitor.application.ChargingProviderTariffService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.application.NearbyStation;
import com.evmonitor.application.StationMatch;
import com.evmonitor.application.StationSearchService;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/charging-provider-tariffs")
@RequiredArgsConstructor
public class ChargingProviderTariffController {

    /** Praezision oeffentlicher Ladungen: sieben Stellen, rund 150 m. */
    private static final int PUBLIC_GEOHASH_PRECISION = 7;

    /** Unter drei Zeichen trifft alles, ueber 80 ist kein Suchtext mehr. */
    private static final int SEARCH_MIN = 3;
    private static final int SEARCH_MAX = 80;

    private final ChargingProviderTariffService service;
    private final NearbyCpoService nearbyCpoService;
    private final StationSearchService stationSearchService;
    private final RateLimitService rateLimitService;

    @GetMapping
    public List<ChargingProviderTariffResponse> getAllTariffs() {
        return service.getAllCurrentTariffs();
    }

    @GetMapping("/emps")
    public List<String> getAvailableEmps() {
        return service.getAvailableEmps();
    }

    @GetMapping("/cpos")
    public List<String> getKnownCpos(@RequestParam(required = false) String country) {
        return service.getKnownCpoNames(country);
    }

    /**
     * Die Ladenetze, die laut Ladesaeulenregister an diesem Ort stehen - als Vorauswahl im
     * Log-Formular. Eine leere Liste heisst nur, dass es keinen Vorschlag gibt; das Formular
     * zeigt dann die vollstaendige Liste aus {@link #getKnownCpos(String)}.
     *
     * <p>Die Koordinaten werden sofort in eine Geohash-Zelle umgerechnet und nur diese wird
     * weiterverwendet - weder gespeichert noch in Rohform an den fremden Dienst gegeben.
     */
    @GetMapping("/cpos/nearby")
    public ResponseEntity<List<String>> getNearbyCpos(@RequestParam double lat,
                                                      @RequestParam double lon,
                                                      Authentication authentication) {
        if (!rateLimitService.tryConsumeCpoLookup(quotaKey(authentication))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (!isOnEarth(lat, lon)) {
            return ResponseEntity.badRequest().build();
        }

        String geohash = GeoHash.withCharacterPrecision(lat, lon, PUBLIC_GEOHASH_PRECISION).toBase32();
        // Antwortet das Register nicht, ist das fuer das Formular dasselbe wie "kein Vorschlag":
        // es zeigt dann die vollstaendige Anbieterliste.
        return ResponseEntity.ok(nearbyCpoService.findNearbyCpos(geohash).orElseGet(List::of));
    }

    /**
     * Die Ladestandorte laut Ladesaeulenregister im Umkreis - als Kacheln im Log-Formular.
     * Gleiche Regeln wie {@link #getNearbyCpos}: Drosselung, Geohash statt Rohkoordinaten,
     * Ausfall des Registers ist eine leere Liste.
     */
    @GetMapping("/cpos/nearby-stations")
    public ResponseEntity<List<NearbyStation>> getNearbyStations(@RequestParam double lat,
                                                                 @RequestParam double lon,
                                                                 Authentication authentication) {
        if (!rateLimitService.tryConsumeCpoLookup(quotaKey(authentication))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (!isOnEarth(lat, lon)) {
            return ResponseEntity.badRequest().build();
        }
        String geohash = GeoHash.withCharacterPrecision(lat, lon, PUBLIC_GEOHASH_PRECISION).toBase32();
        return ResponseEntity.ok(nearbyCpoService.findNearbyStations(geohash).orElseGet(List::of));
    }

    /**
     * Textsuche im Ladesaeulenregister: "EnBW Lichtenau" liefert die Saeule als Kachel, ohne
     * dass der Nutzer die Strasse kennen muss. Eingabe wird normalisiert (Cache-Schluessel),
     * eigener Drossel-Topf, Ausfall des Registers ist eine leere Liste.
     */
    @GetMapping("/cpos/search-stations")
    public ResponseEntity<List<StationMatch>> searchStations(@RequestParam String q, Authentication authentication) {
        String query = q == null ? "" : q.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        if (query.length() < SEARCH_MIN || query.length() > SEARCH_MAX) {
            return ResponseEntity.badRequest().build();
        }
        if (!rateLimitService.tryConsumeStationSearch(quotaKey(authentication))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok(stationSearchService.search(query).orElseGet(List::of));
    }

    private static boolean isOnEarth(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /**
     * Ein Kontingent je Nutzer, geteilt mit {@link com.evmonitor.application.ChargingSiteService}:
     * beide Wege fuehren zum selben Fremddienst, ein zweiter Topf liesse sich sonst umgehen.
     */
    static String quotaKey(Authentication authentication) {
        return "user:" + ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
    }
}
