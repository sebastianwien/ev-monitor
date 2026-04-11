package com.evmonitor.infrastructure.web;

import com.evmonitor.application.manualimport.ManualImportService;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.DataSource;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Accepts Tronity XLSX imports (pre-converted to JSON by the frontend).
 *
 * POST /api/import/tronity
 * Body: { "carId": "uuid", "data": "<JSON array as string>", "mergeSessions": boolean }
 * Response: { "imported": N, "skipped": N, "errors": N }
 *
 * Logs are tagged with DataSource.TRONITY_IMPORT.
 */
@RestController
@RequestMapping("/api/import/tronity")
@Slf4j
@RequiredArgsConstructor
public class TronityImportController {

    private final ManualImportService importService;

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

        try {
            ImportApiResult result = importService.importData(
                    principal.getUser().getId(),
                    request.carId(),
                    "json",
                    request.data(),
                    DataSource.TRONITY_IMPORT
            );
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Tronity import failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Import fehlgeschlagen: " + e.getMessage()));
        }
    }

    private record ImportRequest(UUID carId, String data, Boolean mergeSessions) {}
}
