package com.evmonitor.infrastructure.web;

import com.evmonitor.application.euda.EudaAuthException;
import com.evmonitor.application.euda.EudaConnectService;
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
public class EudaConnectController {

    private final EudaConnectService service;

    public EudaConnectController(EudaConnectService service) {
        this.service = service;
    }

    public record ConnectRequest(@NotBlank String brand, @NotBlank String email, @NotBlank String password) {}

    @PostMapping("/cars/{carId}/connect")
    public ResponseEntity<EudaConnectService.ConnectionStatus> connect(@PathVariable UUID carId,
                                                                       @RequestBody ConnectRequest body,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        return ResponseEntity.ok(service.connect(userId, carId, body.brand(), body.email(), body.password()));
    }

    @ExceptionHandler(EudaAuthException.InvalidCredentials.class)
    ResponseEntity<Map<String, String>> invalidCredentials(EudaAuthException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CREDENTIALS", e.getMessage());
    }

    @ExceptionHandler(EudaAuthException.InteractionRequired.class)
    ResponseEntity<Map<String, String>> interactionRequired(EudaAuthException e) {
        return error(HttpStatus.CONFLICT, "PORTAL_INTERACTION_REQUIRED", e.getMessage());
    }

    @ExceptionHandler(EudaAuthException.PortalUnavailable.class)
    ResponseEntity<Map<String, String>> portalUnavailable(EudaAuthException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "PORTAL_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(EudaConnectService.NotEntitledException.class)
    ResponseEntity<Map<String, String>> notEntitled(RuntimeException e) {
        return error(HttpStatus.FORBIDDEN, "NOT_ENTITLED", e.getMessage());
    }

    @ExceptionHandler(EudaConnectService.RateLimitedException.class)
    ResponseEntity<Map<String, String>> rateLimited(RuntimeException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage());
    }

    @ExceptionHandler(EudaConnectService.ConnectorRejectedException.class)
    ResponseEntity<Map<String, String>> connectorRejected(EudaConnectService.ConnectorRejectedException e) {
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
