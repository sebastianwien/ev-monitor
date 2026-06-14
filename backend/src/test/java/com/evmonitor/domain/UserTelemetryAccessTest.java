package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live-Sync (Tesla Telemetry, Smartcar Webhooks etc.) is gated centrally by
 * {@link User#canActivateTelemetry()}. The matrix below is the contract:
 *
 * <pre>
 *   role            premium   canActivate
 *   ----            -------   -----------
 *   USER            false     false   ← must buy AutoSync
 *   USER            true      true    ← AutoSync subscriber
 *   TESLA_FOUNDER   false     true    ← grandfathered
 *   BETA_TESTER     false     true    ← Trip-Detection beta
 *   ADMIN           false     true    ← always
 * </pre>
 */
class UserTelemetryAccessTest {

    @Test
    void plainUser_withoutPremium_cannotActivate() {
        assertFalse(buildUser("USER", false).canActivateTelemetry());
    }

    @Test
    void plainUser_withPremium_canActivate() {
        assertTrue(buildUser("USER", true).canActivateTelemetry());
    }

    @Test
    void teslaFounder_canActivate_withoutPremium() {
        assertTrue(buildUser("TESLA_FOUNDER", false).canActivateTelemetry());
    }

    @Test
    void betaTester_canActivate_withoutPremium() {
        assertTrue(buildUser("BETA_TESTER", false).canActivateTelemetry());
    }

    @Test
    void admin_canActivate_withoutPremium() {
        assertTrue(buildUser("ADMIN", false).canActivateTelemetry());
    }

    // --- source-aware activation: Tesla is free, Smartcar stays paid ---

    @Test
    void teslaSource_isFree_evenForPlainUserWithoutPremium() {
        assertTrue(buildUser("USER", false).canActivateTelemetry(TelemetrySource.TESLA));
    }

    @Test
    void smartcarSource_staysPaid_matchesNoArgGate() {
        assertFalse(buildUser("USER", false).canActivateTelemetry(TelemetrySource.SMARTCAR));
        assertTrue(buildUser("USER", true).canActivateTelemetry(TelemetrySource.SMARTCAR));
        assertTrue(buildUser("TESLA_FOUNDER", false).canActivateTelemetry(TelemetrySource.SMARTCAR));
    }

    private User buildUser(String role, boolean premium) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email("u@example.com")
                .username("u")
                .authProvider(AuthProvider.LOCAL)
                .role(role)
                .premium(premium)
                .createdAt(now).updatedAt(now)
                .build();
    }
}
