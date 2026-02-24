package com.evmonitor.infrastructure.web;

import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.application.spritmonitor.SpritMonitorImportService;
import com.evmonitor.application.spritmonitor.SpritMonitorVehicleDTO;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/import/sprit-monitor")
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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Failed to fetch vehicles. Check your token: " + e.getMessage()));
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
                request.carId
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to import fuelings: " + e.getMessage()));
        }
    }

    private record ImportRequest(
        String token,
        Integer vehicleId,
        UUID carId
    ) {}
}
