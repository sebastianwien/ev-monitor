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
 *   NONE              USER             FULL               false
 *   AUTOSYNC          USER             FULL               false
 *   AUTOSYNC_LIVE     USER             FULL               true
 *   NONE              TESLA_FOUNDER    FULL               false
 *   AUTOSYNC          TESLA_FOUNDER    FULL               false
 *   AUTOSYNC_LIVE     TESLA_FOUNDER    FULL               true   ← founder upgrades
 *   NONE              BETA_TESTER      FULL               true   ← grandfathered beta
 *   NONE              ADMIN            FULL               true
 * </pre>
 *
 * {@code preferredProfile} is always FULL: Tesla Fleet-Telemetry data collection is
 * free for every Tesla driver. The caller still MUST gate via
 * {@link User#canActivateTelemetry()} before pushing any profile. {@code canUseTripPush}
 * still tracks the paid AutoSync Live tier (the brand-aware free path for trips lands
 * with {@code canViewLiveTrips(CarBrand)} in a later change).
 */
class UserSubscriptionTierTest {

    @Test
    void newUser_defaultsToNoneTier() {
        User u = User.createNewLocalUser("u@example.com", "u", "hash");
        assertEquals(SubscriptionTier.NONE, u.getSubscriptionTier());
    }

    @Test
    void allTiersAndRoles_routeToFull_dataCollectionIsFreeForTesla() {
        // Tesla data collection is free: every Tesla connection streams FULL regardless
        // of tier or role. The paywall moved from data ingestion to the analytics views.
        assertEquals(TelemetryProfile.FULL,
                buildUser("USER", SubscriptionTier.NONE).preferredTelemetryProfile());
        assertEquals(TelemetryProfile.FULL,
                buildUser("USER", SubscriptionTier.AUTOSYNC).preferredTelemetryProfile());
        assertEquals(TelemetryProfile.FULL,
                buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).preferredTelemetryProfile());
        assertEquals(TelemetryProfile.FULL,
                buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).preferredTelemetryProfile());
        assertEquals(TelemetryProfile.FULL,
                buildUser("BETA_TESTER", SubscriptionTier.NONE).preferredTelemetryProfile());
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
    void canViewLiveTrips_teslaCar_freeForEveryone() {
        // Trip detection is part of the free Tesla tier: any tier/role sees live trips
        // for a Tesla car. (Car-model eligibility is enforced separately in TripService.)
        assertTrue(buildUser("USER", SubscriptionTier.NONE).canViewLiveTrips(CarBrand.TESLA));
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveTrips(CarBrand.TESLA));
        assertTrue(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canViewLiveTrips(CarBrand.TESLA));
    }

    @Test
    void canViewLiveTrips_nonTeslaCar_onlyForLiveTierOrBetaOrAdmin() {
        // Other brands stay on the paid analytics gate. null brand (car not found) is
        // treated as non-free and falls back to the same paid gate.
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveTrips(CarBrand.VW));
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canViewLiveTrips(CarBrand.VW));
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canViewLiveTrips(CarBrand.VW));
        assertTrue(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveTrips(CarBrand.VW));
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveTrips(CarBrand.VW));
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canViewLiveTrips(CarBrand.VW));
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canViewLiveTrips(CarBrand.VW));
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canViewLiveTrips(null));
    }

    @Test
    void canViewLiveAnalytics_premiumGate_noTeslaFreePath() {
        // Paid analytics layer (power curves, phantom drain, energy split): AUTOSYNC_LIVE
        // or ADMIN/BETA_TESTER, for EVERY brand. There is no brand argument by design -
        // this gate never goes free for Tesla.
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveAnalytics());
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canViewLiveAnalytics());
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canViewLiveAnalytics());
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveAnalytics());
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canViewLiveAnalytics());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canViewLiveAnalytics());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC).canViewLiveAnalytics());
    }

    @Test
    void canViewLiveCharging_teslaCar_freeForEveryone() {
        // Live-charging card is part of the free Tesla tier: any owner of a Tesla car
        // sees it regardless of subscription tier or role.
        assertTrue(buildUser("USER", SubscriptionTier.NONE).canViewLiveCharging(CarBrand.TESLA));
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveCharging(CarBrand.TESLA));
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canViewLiveCharging(CarBrand.TESLA));
    }

    @Test
    void canViewLiveCharging_nonTeslaCar_onlyForLiveTierOrAdmin_betaTesterExcluded() {
        // For non-Tesla brands the card stays a paid feature: AUTOSYNC_LIVE or ADMIN only.
        // BETA_TESTER is intentionally excluded so the card remains a paid-feature preview.
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveCharging(CarBrand.VW));
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canViewLiveCharging(CarBrand.VW));
        assertTrue(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveCharging(CarBrand.VW));
        assertFalse(buildUser("BETA_TESTER", SubscriptionTier.NONE).canViewLiveCharging(CarBrand.VW));
        assertFalse(buildUser("BETA_TESTER", SubscriptionTier.AUTOSYNC).canViewLiveCharging(CarBrand.VW));
        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveCharging(CarBrand.VW));
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canViewLiveCharging(CarBrand.VW));
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canViewLiveCharging(CarBrand.VW));
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

    @Test
    void supporter_unlocksAnalyticsOnly_notTelemetry() {
        User supporter = buildUser("USER", SubscriptionTier.SUPPORTER);
        // SUPPORTER pays for the premium analytics view...
        assertTrue(supporter.canViewLiveAnalytics());
        // ...but must NOT unlock the telemetry/AutoSync entitlement (the security boundary):
        // a 2 EUR supporter may not activate Smartcar/AutoSync or push trips.
        assertFalse(supporter.canActivateTelemetry());
        assertFalse(supporter.isPremium());
        assertFalse(supporter.canUseTripPush());
        assertFalse(supporter.canViewLiveCharging(CarBrand.VW));
        // canViewLiveTrips is coupled to canViewLiveAnalytics, so it is true for a supporter.
        // Harmless: without telemetry activation a non-Tesla supporter has no trips to show.
        assertTrue(supporter.canViewLiveTrips(CarBrand.VW));
    }

    @Test
    void grantsTelemetry_excludesSupporter_butSupporterIsPaid() {
        assertFalse(SubscriptionTier.NONE.grantsTelemetry());
        assertTrue(SubscriptionTier.AUTOSYNC.grantsTelemetry());
        assertTrue(SubscriptionTier.AUTOSYNC_LIVE.grantsTelemetry());
        assertFalse(SubscriptionTier.SUPPORTER.grantsTelemetry());
        // Billing-wise SUPPORTER is a paid subscription.
        assertTrue(SubscriptionTier.SUPPORTER.isPaid());
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
