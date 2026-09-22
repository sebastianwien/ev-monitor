package com.evmonitor.infrastructure.web;

import com.evmonitor.application.manualimport.ManualTripImportService;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Accepts manual CSV/JSON imports of driving trips via JWT auth.
 *
 * POST /api/import/trips
 * Body: { "carId": "uuid", "format": "csv"|"json", "data": "<raw content>" }
 * Response: { "imported": N, "skipped": N, "errors": N, "warnings": N }
 *
 * CSV header (all optional except started_at and ended_at):
 *   started_at,ended_at,distance_km,odometer_start_km,odometer_end_km,soc_start,soc_end,route_type
 */
@RestController
@RequestMapping("/api/import/trips")
@Slf4j
@RequiredArgsConstructor
public class ManualTripImportController {

    private final ManualTripImportService importService;

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
                    request.data()
            );
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Manual trip import failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Import fehlgeschlagen: " + e.getMessage()));
        }
    }

    private record ImportRequest(UUID carId, String format, String data) {}
}
