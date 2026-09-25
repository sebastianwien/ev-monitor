package com.evmonitor.infrastructure.web;

import com.evmonitor.application.vweuda.VwEudaAuthException;
import com.evmonitor.application.vweuda.VwEudaConnectService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Verbinden fuer den VW EU-Data-Act-AutoSync. Nur dieser eine Endpoint sieht das Passwort des
 * Nutzers; Status, Trennen und Historie liegen weiter beim Connectors-Service.
 * Fehler kommen als sprechende Codes - bewusst kein 401, das deutet das Frontend als
 * abgelaufene ev-monitor-Sitzung.
 */
@RestController
@RequestMapping("/api/eu-data-act")
public class VwEudaConnectController {

    private final VwEudaConnectService service;

    public VwEudaConnectController(VwEudaConnectService service) {
        this.service = service;
    }

    public record ConnectRequest(@NotBlank String brand, @NotBlank String email, @NotBlank String password) {}

    @PostMapping("/cars/{carId}/connect")
    public ResponseEntity<VwEudaConnectService.ConnectionStatus> connect(@PathVariable UUID carId,
                                                                       @RequestBody ConnectRequest body,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        return ResponseEntity.ok(service.connect(userId, carId, body.brand(), body.email(), body.password()));
    }

    @ExceptionHandler(VwEudaAuthException.InvalidCredentials.class)
    ResponseEntity<Map<String, String>> invalidCredentials(VwEudaAuthException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CREDENTIALS", e.getMessage());
    }

    @ExceptionHandler(VwEudaAuthException.InteractionRequired.class)
    ResponseEntity<Map<String, String>> interactionRequired(VwEudaAuthException e) {
        return error(HttpStatus.CONFLICT, "PORTAL_INTERACTION_REQUIRED", e.getMessage());
    }

    @ExceptionHandler(VwEudaAuthException.PortalUnavailable.class)
    ResponseEntity<Map<String, String>> portalUnavailable(VwEudaAuthException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "PORTAL_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(VwEudaConnectService.NotEntitledException.class)
    ResponseEntity<Map<String, String>> notEntitled(RuntimeException e) {
        return error(HttpStatus.FORBIDDEN, "NOT_ENTITLED", e.getMessage());
    }

    @ExceptionHandler(VwEudaConnectService.RateLimitedException.class)
    ResponseEntity<Map<String, String>> rateLimited(RuntimeException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage());
    }

    @ExceptionHandler(VwEudaConnectService.ConnectorRejectedException.class)
    ResponseEntity<Map<String, String>> connectorRejected(VwEudaConnectService.ConnectorRejectedException e) {
        return error(HttpStatus.valueOf(e.status()), e.code(), e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String, String>> forbidden(SecurityException e) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message == null ? "" : message));
    }
}
