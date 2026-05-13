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
    void canViewLiveTrips_onlyForLiveTierOrBetaOrAdmin() {
        // Same gate as canUseTripPush: AutoSync-Live-tier OR privileged role.
        // TESLA_FOUNDER without Live-tier is CHARGING_ONLY → must not see live trips.
        // Tessie-imported trips are handled in TripService (whitelist by data_source),
        // not here.
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveTrips());
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canViewLiveTrips());
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canViewLiveTrips());
        assertTrue(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveTrips());
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveTrips());
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canViewLiveTrips());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canViewLiveTrips());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC).canViewLiveTrips());
    }

    @Test
    void canViewLiveCharging_onlyForLiveTierOrAdmin_betaTesterExcluded() {
        // Strict gate for the dashboard Live-Charging card: AUTOSYNC_LIVE or ADMIN only.
        // BETA_TESTER is intentionally excluded so the card remains a paid-feature preview.
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveCharging());
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canViewLiveCharging());
        assertTrue(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveCharging());
        assertFalse(buildUser("BETA_TESTER", SubscriptionTier.NONE).canViewLiveCharging());
        assertFalse(buildUser("BETA_TESTER", SubscriptionTier.AUTOSYNC).canViewLiveCharging());
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveCharging());
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canViewLiveCharging());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canViewLiveCharging());
    }

    @Test
    void canBypassEligibilityGate_onlyForAdminOrBetaTester() {
        // Audit privilege: ADMIN and BETA_TESTER see live trips even from car models
        // that are not on the eligibility whitelist (Polestar 3 etc.), so the data
        // can be re-evaluated by impersonation. AUTOSYNC_LIVE customers do NOT get
        // audit access - they're bound to the same eligibility whitelist as everyone.
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canBypassEligibilityGate());
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canBypassEligibilityGate());
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canBypassEligibilityGate());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC_LIVE).canBypassEligibilityGate());
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC).canBypassEligibilityGate());
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canBypassEligibilityGate());
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
