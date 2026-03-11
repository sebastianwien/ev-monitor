package com.evmonitor.infrastructure.web;

import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.application.teslalogger.TeslaLoggerImportService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Accepts manual imports from third-party Tesla data loggers (TeslaMate, TeslaLogger, TeslaFi).
 *
 * POST /api/import/tesla-logger
 * Body: { "carId": "uuid", "format": "csv"|"json", "data": "<raw content>" }
 * Response: { "imported": N, "skipped": N, "errors": [...], "coinsAwarded": N }
 *
 * Format spec (pflichtfelder zuerst):
 *   date, odometer_km, kwh, soc_before, soc_after, cost_eur, location, duration_min
 *
 * - date: ISO 8601, European (DD.MM.YYYY), US (MM/DD/YYYY), Unix timestamp (s or ms)
 * - location: "lat,lon" (computed to geohash) or place name (stored without geohash)
 * - soc_before OR soc_after is required; soc_after is preferred for consumption calculations
 */
@RestController
@RequestMapping("/api/import/tesla-logger")
@Slf4j
public class TeslaLoggerImportController {

    private final TeslaLoggerImportService importService;

    public TeslaLoggerImportController(TeslaLoggerImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<?> importData(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ImportRequest request
    ) {
        if (request.carId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "carId ist erforderlich"));
        }
        if (request.data() == null || request.data().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "data darf nicht leer sein"));
        }
        String format = request.format() != null ? request.format() : "csv";
        if (!format.equalsIgnoreCase("csv") && !format.equalsIgnoreCase("json")) {
            return ResponseEntity.badRequest().body(Map.of("error", "format muss 'csv' oder 'json' sein"));
        }

        try {
            ImportResult result = importService.importData(
                    principal.getUser().getId(),
                    request.carId(),
                    format,
                    request.data()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("TeslaLogger import failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Import fehlgeschlagen: " + e.getMessage()));
        }
    }

    private record ImportRequest(UUID carId, String format, String data) {}
}
