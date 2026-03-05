package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tesla.*;
import com.evmonitor.domain.TeslaConnection;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/tesla")
@RequiredArgsConstructor
@Slf4j
public class TeslaController {

    private final TeslaApiService teslaApiService;
    private final TeslaFleetApiService teslaFleetApiService;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    /**
     * Connect Tesla account
     * Frontend should obtain access token via Tesla OAuth and send it here
     */
    @PostMapping("/connect")
    public ResponseEntity<ConnectResponse> connect(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody ConnectRequest request
    ) {
        UUID userId = principal.getUser().getId();

        try {
            TeslaConnection connection = teslaApiService.saveConnection(
                userId,
                request.accessToken(),
                request.vehicleId(),
                request.vehicleName()
            );

            log.info("Tesla connected for user {}: {}", userId, connection.getVehicleName());

            return ResponseEntity.ok(new ConnectResponse(
                true,
                "Tesla account connected successfully",
                connection.getVehicleName()
            ));
        } catch (Exception e) {
            log.error("Failed to connect Tesla for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(new ConnectResponse(
                false,
                "Failed to connect: " + e.getMessage(),
                null
            ));
        }
    }

    /**
     * Manual sync - fetch latest data from Tesla and create logs
     */
    @PostMapping("/sync")
    public ResponseEntity<TeslaSyncResult> sync(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();

        try {
            TeslaSyncResult result = teslaApiService.syncChargingData(userId);
            log.info("Tesla sync for user {}: {} logs imported", userId, result.logsImported());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            log.error("Tesla sync failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get connection status
     */
    @GetMapping("/status")
    public ResponseEntity<TeslaConnectionStatus> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();

        return teslaApiService.getConnectionStatus(userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.ok(new TeslaConnectionStatus(false, null, null, false)));
    }

    /**
     * Disconnect Tesla account
     */
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        teslaApiService.disconnect(userId);
        log.info("Tesla disconnected for user {}", userId);
        return ResponseEntity.noContent().build();
    }

    // ===== Fleet API (OAuth2) Endpoints =====

    /**
     * Step 1: Get Tesla OAuth2 authorization URL.
     * Frontend redirects the user to this URL.
     */
    @GetMapping("/fleet/auth/start")
    public ResponseEntity<TeslaFleetAuthStartResponse> fleetAuthStart(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        TeslaFleetAuthStartResponse response = teslaFleetApiService.generateAuthUrl(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2: OAuth2 callback from Tesla.
     * Tesla redirects here with ?code=...&state=...
     * This endpoint exchanges the code for tokens and redirects the user back to the frontend.
     */
    @GetMapping("/fleet/auth/callback")
    public void fleetAuthCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        try {
            UUID userId = teslaFleetApiService.handleCallback(code, state);
            log.info("Tesla Fleet OAuth2 callback successful for user {}", userId);
            response.sendRedirect(appBaseUrl + "/cars?tesla-connected=true");
        } catch (Exception e) {
            log.error("Tesla Fleet OAuth2 callback failed: {}", e.getMessage());
            response.sendRedirect(appBaseUrl + "/cars?tesla-error=" +
                    java.net.URLEncoder.encode(e.getMessage(), "UTF-8"));
        }
    }

    /**
     * Manually trigger a Fleet API charging history sync.
     */
    @PostMapping("/fleet/sync-history")
    public ResponseEntity<TeslaFleetSyncResult> fleetSyncHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        try {
            TeslaFleetSyncResult result = teslaFleetApiService.syncChargingHistory(userId);
            log.info("Tesla Fleet sync for user {}: {}", userId, result.message());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            log.error("Tesla Fleet sync failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // ===== DTOs
    public record ConnectRequest(
        String accessToken,
        String vehicleId,
        String vehicleName
    ) {}

    public record ConnectResponse(
        boolean success,
        String message,
        String vehicleName
    ) {}
}
