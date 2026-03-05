package com.evmonitor.infrastructure.web;

import com.evmonitor.application.goe.GoeApiService;
import com.evmonitor.application.goe.GoeConnectionStatus;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goe")
@RequiredArgsConstructor
@Slf4j
public class GoeController {

    private final GoeApiService goeApiService;

    /**
     * List all active go-e connections for the current user.
     */
    @GetMapping("/connections")
    public ResponseEntity<List<GoeConnectionStatus>> getConnections(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        return ResponseEntity.ok(goeApiService.getConnectionsForUser(userId));
    }

    /**
     * Connect a new go-eCharger via Cloud API.
     */
    @PostMapping("/connect")
    public ResponseEntity<?> connect(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ConnectRequest request) {
        UUID userId = principal.getUser().getId();

        if (request.serial() == null || request.serial().isBlank()) {
            return ResponseEntity.badRequest().body("Seriennummer darf nicht leer sein");
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return ResponseEntity.badRequest().body("API Key darf nicht leer sein");
        }
        if (request.carId() == null) {
            return ResponseEntity.badRequest().body("Fahrzeug muss ausgewählt sein");
        }

        try {
            GoeConnectionStatus status = goeApiService.connect(
                    userId,
                    request.carId(),
                    request.serial(),
                    request.apiKey(),
                    request.displayName()
            );
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            log.warn("go-e connect failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Disconnect (deactivate) a go-e connection.
     */
    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Void> disconnect(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID userId = principal.getUser().getId();
        goeApiService.disconnect(id, userId);
        return ResponseEntity.noContent().build();
    }

    // DTOs
    public record ConnectRequest(
            String serial,
            String apiKey,
            UUID carId,
            String displayName
    ) {}
}
