package com.evmonitor.infrastructure.web;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.ChargingProviderTariffResponse;
import com.evmonitor.application.ChargingProviderTariffService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.infrastructure.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final ChargingProviderTariffService service;
    private final NearbyCpoService nearbyCpoService;
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
                                                      HttpServletRequest request) {
        if (!rateLimitService.tryConsumeCpoLookup(clientIp(request))) {
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

    private static boolean isOnEarth(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
