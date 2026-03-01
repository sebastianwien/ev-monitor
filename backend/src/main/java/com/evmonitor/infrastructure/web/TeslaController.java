package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tesla.TeslaApiService;
import com.evmonitor.application.tesla.TeslaConnectionStatus;
import com.evmonitor.application.tesla.TeslaSyncResult;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tesla")
@RequiredArgsConstructor
@Slf4j
public class TeslaController {

    private final TeslaApiService teslaApiService;

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
            teslaApiService.saveConnection(
                userId,
                request.accessToken(),
                request.vehicleId(),
                request.vehicleName()
            );

            log.info("Tesla connected for user {}: {}", userId, request.vehicleName());

            return ResponseEntity.ok(new ConnectResponse(
                true,
                "Tesla account connected successfully",
                request.vehicleName()
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

    // DTOs
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
