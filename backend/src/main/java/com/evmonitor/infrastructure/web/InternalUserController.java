package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PremiumProperties;
import com.evmonitor.domain.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoints for service-to-service communication.
 * Secured by InternalAuthFilter (X-Internal-Token header), NOT by user JWT.
 */
@RestController
@RequestMapping("/api/internal")
public class InternalUserController {

    private final UserRepository userRepository;
    private final PremiumProperties premiumProperties;

    public InternalUserController(UserRepository userRepository, PremiumProperties premiumProperties) {
        this.userRepository = userRepository;
        this.premiumProperties = premiumProperties;
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
