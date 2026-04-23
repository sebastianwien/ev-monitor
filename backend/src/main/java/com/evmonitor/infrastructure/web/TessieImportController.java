package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tessie.TessieImportResult;
import com.evmonitor.application.tessie.TessieImportService;
import com.evmonitor.application.tessie.TessieVehicleDTO;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/import/tessie")
@Slf4j
@RequiredArgsConstructor
public class TessieImportController {

    private static final Pattern VIN_PATTERN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");

    private final TessieImportService importService;

    /**
     * POST /api/import/tessie/vehicles
     * Body: { "token": "..." }
     * Response: [{ "vin": "5YJ...", "displayName": "Mein Tesla", "isActive": true }]
     */
    @PostMapping("/vehicles")
    public ResponseEntity<?> getVehicles(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> request
    ) {
        String token = request.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token ist erforderlich"));
        }

        try {
            List<TessieVehicleDTO> vehicles = importService.fetchVehicles(token);
            return ResponseEntity.ok(vehicles);
        } catch (HttpClientErrorException.Unauthorized e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Token ungültig. Bitte prüfe deinen Tessie API Token."));
        } catch (HttpClientErrorException e) {
            log.warn("Tessie API error: {} {}", e.getStatusCode().value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tessie API Fehler (" + e.getStatusCode().value() + "). Bitte versuche es später erneut."));
        } catch (ResourceAccessException e) {
            log.error("Tessie not reachable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tessie nicht erreichbar. Bitte versuche es später erneut."));
        } catch (Exception e) {
            log.error("Unexpected error during Tessie vehicle fetch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unerwarteter Fehler. Bitte versuche es später erneut."));
        }
    }

    /**
     * POST /api/import/tessie/import
     * Body: { "token": "...", "vin": "5YJ..." }
     * Response: { "drivesImported": 120, "chargesImported": 45, "skipped": 3 }
     */
    @PostMapping("/import")
    public ResponseEntity<?> importVin(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ImportRequest request
    ) {
        if (request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token ist erforderlich"));
        }
        if (request.vin() == null || !VIN_PATTERN.matcher(request.vin()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "VIN ungültig (17 Zeichen, A-Z0-9 ohne I/O/Q)"));
        }

        try {
            UUID userId = principal.getUser().getId();
            TessieImportResult result = importService.importForVin(userId, request.token(), request.vin());
            return ResponseEntity.ok(result);
        } catch (HttpClientErrorException.Unauthorized e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Token ungültig. Bitte prüfe deinen Tessie API Token."));
        } catch (HttpClientErrorException e) {
            log.warn("Tessie API error during import: {} {}", e.getStatusCode().value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tessie API Fehler (" + e.getStatusCode().value() + "). Bitte versuche es später erneut."));
        } catch (ResourceAccessException e) {
            log.error("Tessie not reachable during import: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tessie nicht erreichbar. Bitte versuche es später erneut."));
        } catch (Exception e) {
            log.error("Unexpected error during Tessie import for vin={}", request.vin(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Import fehlgeschlagen. Bitte versuche es später erneut."));
        }
    }

    private record ImportRequest(String token, String vin) {}
}
