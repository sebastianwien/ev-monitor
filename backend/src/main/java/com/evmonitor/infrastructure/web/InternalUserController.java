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
}
