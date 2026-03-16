package com.evmonitor.infrastructure.web;

import com.evmonitor.application.manualimport.ManualImportService;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Accepts manual CSV/JSON imports of charging sessions via JWT auth.
 *
 * POST /api/import/sessions
 * Body: { "carId": "uuid", "format": "csv"|"json", "data": "<raw content>", "mergeSessions": boolean }
 * Response: { "imported": N, "skipped": N, "errors": N }
 *
 * CSV header (all optional except date and kwh):
 *   date,kwh,odometer_km,soc_before,soc_after,cost_eur,duration_min,location,
 *   charging_type,max_charging_power_kw,route_type,tire_type
 */
@RestController
@RequestMapping("/api/import/sessions")
@Slf4j
public class ManualImportController {

    private final ManualImportService importService;

    public ManualImportController(ManualImportService importService) {
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
            ImportApiResult result = importService.importData(
                    principal.getUser().getId(),
                    request.carId(),
                    format,
                    request.data(),
                    request.mergeSessions() != null && request.mergeSessions()
            );
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Manual import failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Import fehlgeschlagen: " + e.getMessage()));
        }
    }

    private record ImportRequest(UUID carId, String format, String data, Boolean mergeSessions) {}
}
