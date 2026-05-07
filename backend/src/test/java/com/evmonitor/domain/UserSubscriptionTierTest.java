package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-aware entitlements layered on top of {@link UserTelemetryAccessTest}.
 * Defines what each (tier × role) combination unlocks:
 *
 * <pre>
 *   tier              role             preferredProfile   canUseTripPush
 *   ----              ----             ----------------   --------------
 *   NONE              USER             CHARGING_ONLY*     false
 *   AUTOSYNC          USER             CHARGING_ONLY      false
 *   AUTOSYNC_LIVE     USER             FULL               true
 *   NONE              TESLA_FOUNDER    CHARGING_ONLY      false
 *   AUTOSYNC          TESLA_FOUNDER    CHARGING_ONLY      false
 *   AUTOSYNC_LIVE     TESLA_FOUNDER    FULL               true   ← founder upgrades
 *   NONE              BETA_TESTER      FULL               true   ← grandfathered beta
 *   NONE              ADMIN            FULL               true
 * </pre>
 *
 * (*) {@code preferredProfile} for tier=NONE/role=USER is academic - the caller
 * MUST gate via {@link User#canActivateTelemetry()} before pushing any profile.
 */
class UserSubscriptionTierTest {

    @Test
    void newUser_defaultsToNoneTier() {
        User u = User.createNewLocalUser("u@example.com", "u", "hash");
        assertEquals(SubscriptionTier.NONE, u.getSubscriptionTier());
    }

    @Test
    void autoSyncTier_routesToChargingOnly() {
        assertEquals(TelemetryProfile.CHARGING_ONLY,
                buildUser("USER", SubscriptionTier.AUTOSYNC).preferredTelemetryProfile());
    }

    @Test
    void liveTier_routesToFull() {
        assertEquals(TelemetryProfile.FULL,
                buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).preferredTelemetryProfile());
    }

    @Test
    void betaTester_alwaysFull_evenWithoutTier() {
        assertEquals(TelemetryProfile.FULL,
                buildUser("BETA_TESTER", SubscriptionTier.NONE).preferredTelemetryProfile());
    }

    @Test
    void teslaFounder_chargingOnly_unlessLiveTier() {
        assertEquals(TelemetryProfile.CHARGING_ONLY,
                buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).preferredTelemetryProfile());
        assertEquals(TelemetryProfile.CHARGING_ONLY,
                buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC).preferredTelemetryProfile());
        assertEquals(TelemetryProfile.FULL,
                buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC_LIVE).preferredTelemetryProfile());
    }

    @Test
    void admin_alwaysFull() {
        assertEquals(TelemetryProfile.FULL,
                buildUser("ADMIN", SubscriptionTier.NONE).preferredTelemetryProfile());
    }

    @Test
    void canUseTripPush_onlyForLiveTierOrBetaOrAdmin() {
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canUseTripPush());
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canUseTripPush());
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canUseTripPush());
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC).canUseTripPush());
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canUseTripPush());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canUseTripPush());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC).canUseTripPush());
    }

    @Test
    void isPremium_derivedFromTier() {
        assertFalse(buildUser("USER", SubscriptionTier.NONE).isPremium());
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC).isPremium());
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).isPremium());
    }

    @Test
    void canActivateTelemetry_unchangedByTierIntroduction() {
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canActivateTelemetry());
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC).canActivateTelemetry());
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canActivateTelemetry());
        assertTrue(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canActivateTelemetry());
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canActivateTelemetry());
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canActivateTelemetry());
    }

    private User buildUser(String role, SubscriptionTier tier) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email("u@example.com")
                .username("u")
                .authProvider(AuthProvider.LOCAL)
                .role(role)
                .subscriptionTier(tier)
                .createdAt(now).updatedAt(now)
                .build();
    }
}
