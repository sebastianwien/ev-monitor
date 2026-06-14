package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PremiumProperties;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.CustomUserDetailsService;
import com.evmonitor.infrastructure.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoints for service-to-service communication.
 * Secured by InternalAuthFilter (X-Internal-Token header), NOT by user JWT.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserRepository userRepository;
    private final PremiumProperties premiumProperties;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public record ImpersonateRequest(String email) {}
    public record ImpersonateResponse(String token, String email, String username) {}

    @PostMapping("/impersonate")
    public ResponseEntity<?> impersonate(@RequestBody ImpersonateRequest request, HttpServletRequest httpRequest) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
            String token = jwtService.generateImpersonationToken(userDetails);
            log.warn("IMPERSONATION: admin logged in as '{}' from IP {}", request.email(), httpRequest.getRemoteAddr());
            return ResponseEntity.ok(new ImpersonateResponse(token, request.email(), userDetails.getUsername()));
        } catch (Exception e) {
            log.warn("IMPERSONATION FAILED: target '{}' not found, IP {}", request.email(), httpRequest.getRemoteAddr());
            return ResponseEntity.badRequest().body(Map.of("error", "User not found: " + request.email()));
        }
    }

    @GetMapping("/users/{userId}/has-premium")
    public ResponseEntity<Map<String, Boolean>> hasPremium(@PathVariable UUID userId) {
        // Beta mode: Wallbox is free for everyone until PREMIUM_ENABLED=true
        if (!premiumProperties.isEnabled()) {
            return ResponseEntity.ok(Map.of("premium", true));
        }
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(Map.of("premium", user.isPremium())))
                .orElse(ResponseEntity.notFound().build());
    }

    public record TelemetryAccessResponse(
            boolean canActivate,
            String role,
            boolean premium,
            String tier,
            String preferredProfile) {}

    private TelemetryAccessResponse toResponse(com.evmonitor.domain.User user,
            com.evmonitor.domain.TelemetrySource source) {
        return new TelemetryAccessResponse(
                user.canActivateTelemetry(source),
                user.getRole(),
                user.isPremium(),
                user.getSubscriptionTier().name(),
                user.preferredTelemetryProfile().name());
    }

    /**
     * Single source of truth for "may this user activate Live-Sync (Tesla Telemetry / Smartcar Webhook)?".
     * Used by the connectors-service before pushing telemetry config and by the reconciliation job
     * that detects role/tier drift. Response now carries tier + preferredProfile so the
     * connectors-service can pick the correct Tesla telemetry profile (CHARGING_ONLY vs FULL)
     * without a second round-trip.
     *
     * <p>{@code source} selects the activation policy: {@code TESLA} activation is free,
     * {@code SMARTCAR} stays paid. Absent/unknown source falls back to {@code SMARTCAR}
     * (fail-closed - never accidentally frees a paid integration).
     */
    @GetMapping("/users/{userId}/telemetry-access")
    public ResponseEntity<TelemetryAccessResponse> telemetryAccess(
            @PathVariable UUID userId,
            @RequestParam(value = "source", required = false) com.evmonitor.domain.TelemetrySource source) {
        com.evmonitor.domain.TelemetrySource resolved =
                source != null ? source : com.evmonitor.domain.TelemetrySource.SMARTCAR;
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(toResponse(user, resolved)))
                .orElse(ResponseEntity.notFound().build());
    }

    public record EntitlementBatchRequest(java.util.List<UUID> userIds,
            com.evmonitor.domain.TelemetrySource source) {}

    /**
     * Bulk variant of {@code /telemetry-access}: takes a list of userIds (and an optional
     * {@code source}, same semantics as the single endpoint) and returns a
     * {userId → entitlement} map. Designed for periodic reconciliation jobs that would
     * otherwise fan out to one HTTP call per user. Unknown ids are omitted from the response.
     */
    @PostMapping("/users/entitlements")
    public ResponseEntity<Map<UUID, TelemetryAccessResponse>> entitlementsBatch(
            @RequestBody EntitlementBatchRequest request) {
        if (request == null || request.userIds() == null || request.userIds().isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        com.evmonitor.domain.TelemetrySource resolved =
                request.source() != null ? request.source() : com.evmonitor.domain.TelemetrySource.SMARTCAR;
        Map<UUID, TelemetryAccessResponse> result = new java.util.HashMap<>();
        for (com.evmonitor.domain.User user : userRepository.findAllByIds(request.userIds())) {
            result.put(user.getId(), toResponse(user, resolved));
        }
        return ResponseEntity.ok(result);
    }
}
