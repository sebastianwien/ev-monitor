package com.evmonitor.infrastructure.web;

import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.application.spritmonitor.SpritMonitorImportService;
import com.evmonitor.application.spritmonitor.SpritMonitorVehicleDTO;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/import/sprit-monitor")
@Slf4j
public class SpritMonitorImportController {

    private final SpritMonitorImportService importService;

    public SpritMonitorImportController(SpritMonitorImportService importService) {
        this.importService = importService;
    }

    /**
     * Fetches electric vehicles from Sprit-Monitor
     *
     * POST /api/import/sprit-monitor/vehicles
     * Body: { "token": "..." }
     * Response: [{ "id": 123, "make": "Tesla", "model": "Model 3", "mainTankType": 5 }]
     */
    @PostMapping("/vehicles")
    public ResponseEntity<?> getVehicles(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody Map<String, String> request
    ) {
        String token = request.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required"));
        }

        try {
            List<SpritMonitorVehicleDTO> vehicles = importService.fetchVehicles(token);
            return ResponseEntity.ok(vehicles);
        } catch (HttpClientErrorException.Unauthorized e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "Token ungültig. Bitte prüfe deinen Sprit-Monitor API Token."));
        } catch (HttpClientErrorException e) {
            log.warn("Sprit-Monitor API error: {} {}", e.getStatusCode().value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "Sprit-Monitor API Fehler (" + e.getStatusCode().value() + "). Bitte versuche es später erneut."));
        } catch (ResourceAccessException e) {
            log.error("Sprit-Monitor not reachable (connection/timeout): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "Sprit-Monitor nicht erreichbar. Bitte versuche es später erneut."));
        } catch (Exception e) {
            log.error("Unexpected error during Sprit-Monitor vehicle fetch", e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "Sprit-Monitor nicht erreichbar. Bitte versuche es später erneut."));
        }
    }

    /**
     * Imports fuelings from Sprit-Monitor for a specific vehicle
     *
     * POST /api/import/sprit-monitor/fuelings
     * Body: { "token": "...", "vehicleId": 123, "carId": "uuid" }
     * Response: { "imported": 45, "skipped": 2, "errors": [] }
     */
    @PostMapping("/fuelings")
    public ResponseEntity<?> importFuelings(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody ImportRequest request
    ) {
        if (request.token == null || request.token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required"));
        }
        if (request.vehicleId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vehicle ID is required"));
        }
        if (request.carId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Car ID is required"));
        }

        try {
            ImportResult result = importService.importFuelings(
                principal.getUser().getId(),
                request.token,
                request.vehicleId,
                request.mainTankId,
                request.carId
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to import fuelings: " + e.getMessage()));
        }
    }

    /**
     * Deletes all Sprit-Monitor imports for the authenticated user
     *
     * DELETE /api/import/sprit-monitor/delete-all
     * Response: 200 OK (no body)
     */
    @DeleteMapping("/delete-all")
    public ResponseEntity<Void> deleteAllImports(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            importService.deleteAllImports(principal.getUser().getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete Sprit-Monitor imports for user {}", principal.getUser().getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private record ImportRequest(
        String token,
        Integer vehicleId,
        Integer mainTankId,
        UUID carId
    ) {}
}
