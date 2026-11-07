package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingStationCellService;
import com.evmonitor.application.ChargingStationMap;
import com.evmonitor.application.ChargingStationMapService;
import com.evmonitor.application.CpoPriceBenchmarkService;
import com.evmonitor.application.CpoTariffs;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Säulenkarte. Nur angemeldet (Regel {@code /api/**} in der Security-Config): beide Endpunkte
 * fragen ein fremdes Register ab bzw. rechnen Katalogpreise.
 *
 * <p>Datenschutz: die Karte schickt Geohash-6-Zellen (in Deutschland etwa 0,75 × 0,6 km) ihres Ausschnitts, nie
 * eine Position. Nichts davon wird gespeichert; der Cache liegt auf der Zelle, ohne Nutzerbezug.
 */
@RestController
@RequestMapping("/api/charging-stations")
@RequiredArgsConstructor
public class ChargingStationController {

    /** Länge von charging_networks.name. */
    private static final int CPO_MAX = 100;
    /** Stärkste Säulen im Register liegen bei 400 kW; darüber ist es kein Messwert. */
    private static final int POWER_MAX_KW = 1_000;

    private final ChargingStationMapService mapService;
    private final CpoPriceBenchmarkService benchmarkService;
    private final RateLimitService rateLimitService;

    /**
     * Standorte mit günstigstem Katalogpreis je Zelle. Zellen, die das Register fragen müssten und
     * über der Drossel liegen oder ohne Antwort bleiben, stehen in {@code missing} - kein 429 für alles.
     *
     * @param cells 1 bis {@link ChargingStationMapService#MAX_CELLS} Geohash-6-Zellen, kommagetrennt
     */
    @GetMapping
    public ResponseEntity<ChargingStationMap> stations(@RequestParam String cells, Authentication authentication) {
        List<String> list = Arrays.stream(cells.split(",", -1)).map(String::trim).distinct().toList();
        if (list.isEmpty() || list.size() > ChargingStationMapService.MAX_CELLS
                || !list.stream().allMatch(ChargingStationCellService::isValidCell)) {
            return ResponseEntity.badRequest().build();
        }
        String quotaKey = "user:" + ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return ResponseEntity.ok(mapService.stations(list, cell -> rateLimitService.tryConsumeStationCells(quotaKey)));
    }

    /**
     * Alle Katalogtarife an einer Säule in Deutschland, günstigste zuerst, mit Start- und
     * Blockiergebühr. 204: unbekannter Betreiber oder kein Tarif.
     *
     * @param powerKw höchste Leistung der Säule für diese Ladeart, wählt die kW-Stufe
     */
    @GetMapping("/tariffs")
    public ResponseEntity<CpoTariffs> tariffs(@RequestParam String cpo, @RequestParam ChargingType type,
                                              @RequestParam(required = false) Integer powerKw) {
        if (cpo.isBlank() || cpo.length() > CPO_MAX || type == ChargingType.UNKNOWN
                || (powerKw != null && (powerKw < 1 || powerKw > POWER_MAX_KW))) {
            return ResponseEntity.badRequest().build();
        }
        return benchmarkService.stationTariffs(cpo, type, powerKw)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
