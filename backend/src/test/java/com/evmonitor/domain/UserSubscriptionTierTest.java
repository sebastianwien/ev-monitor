package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
    void canViewLiveAnalytics_paidGate_noTeslaFreePath() {
        // Paid analytics layer (power curves, phantom drain, energy split): jeder bezahlte
        // Tarif (AUTOSYNC, AUTOSYNC_LIVE, SUPPORTER) plus ADMIN/BETA_TESTER, fuer JEDE Marke.
        // Kein Marken-Argument by design - dieses Gate wird nie gratis fuer Tesla. AUTOSYNC
        // ist Teil des Zwei-Tier-Zielbilds: Nicht-Tesla-Fahrer bekommen die Auswertungen ueber
        // AutoSync, Tesla-Fahrer ueber Supporter.
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC).canViewLiveAnalytics());
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE).canViewLiveAnalytics());
        assertTrue(buildUser("USER", SubscriptionTier.SUPPORTER).canViewLiveAnalytics());
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE).canViewLiveAnalytics());
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE).canViewLiveAnalytics());
        assertTrue(buildUser("TESLA_FOUNDER", SubscriptionTier.AUTOSYNC).canViewLiveAnalytics());
        assertFalse(buildUser("USER", SubscriptionTier.NONE).canViewLiveAnalytics());
        assertFalse(buildUser("TESLA_FOUNDER", SubscriptionTier.NONE).canViewLiveAnalytics());
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

    // ------------------------------------------------------ Heimlade-Ersparnis

    // Launch-Anker des Trials, gespiegelt aus FeatureTrial#HOME_CHARGING_SAVINGS. Bewusst
    // hart notiert: aendert sich der Anker, sollen diese Spec-Tests brechen.
    private static final LocalDate TRIAL_LAUNCH = LocalDate.of(2026, 9, 3);
    private static final LocalDate LAST_TRIAL_DAY = TRIAL_LAUNCH.plusDays(30);   // 2026-10-03
    private static final LocalDate AFTER_TRIAL = LAST_TRIAL_DAY.plusDays(1);     // 2026-10-04
    private static final LocalDate LONG_BEFORE_LAUNCH = LocalDate.of(2025, 1, 1);

    /**
     * Bezahlte Tarife und privilegierte Rollen sehen die Kachel dauerhaft - auch nach
     * Trial-Ende. Der freie Tarif faellt dann heraus. Getestet mit einem Datum nach
     * dem Trial, damit belegt ist, dass hier der Tarif traegt und nicht das Trial.
     */
    @Test
    void chargingSavings_paidTiersAndRoles_entitledAfterTrialEnds() {
        assertFalse(buildUser("USER", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH).canViewChargingSavings(AFTER_TRIAL));

        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC, LONG_BEFORE_LAUNCH).canViewChargingSavings(AFTER_TRIAL));
        assertTrue(buildUser("USER", SubscriptionTier.AUTOSYNC_LIVE, LONG_BEFORE_LAUNCH).canViewChargingSavings(AFTER_TRIAL));
        assertTrue(buildUser("USER", SubscriptionTier.SUPPORTER, LONG_BEFORE_LAUNCH).canViewChargingSavings(AFTER_TRIAL));
        assertTrue(buildUser("ADMIN", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH).canViewChargingSavings(AFTER_TRIAL));
        assertTrue(buildUser("BETA_TESTER", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH).canViewChargingSavings(AFTER_TRIAL));
    }

    /**
     * Launch-verankertes Trial: ein Bestandsnutzer (vor dem Launch registriert) sieht die
     * Kachel ab dem Launch einen vollen Monat, danach nicht mehr.
     */
    @Test
    void chargingSavings_existingFreeUser_seesTileForOneMonthFromLaunch() {
        User existing = buildUser("USER", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH);
        assertTrue(existing.canViewChargingSavings(TRIAL_LAUNCH), "Launch-Tag");
        assertTrue(existing.canViewChargingSavings(LAST_TRIAL_DAY), "letzter Trial-Tag");
        assertFalse(existing.canViewChargingSavings(AFTER_TRIAL), "Tag nach dem Trial");
    }

    /** Wer nach dem Launch registriert, bekommt seine 30 Tage ab Registrierung. */
    @Test
    void chargingSavings_userRegisteredAfterLaunch_getsThirtyDaysFromRegistration() {
        LocalDate registered = LocalDate.of(2026, 11, 1);
        User late = buildUser("USER", SubscriptionTier.NONE, registered);
        assertTrue(late.canViewChargingSavings(registered), "Registrierungstag");
        assertTrue(late.canViewChargingSavings(registered.plusDays(30)), "letzter Trial-Tag");
        assertFalse(late.canViewChargingSavings(registered.plusDays(31)), "Tag nach dem Trial");
    }

    /** Ohne bekanntes Registrierungsdatum gibt es kein Trial - dann traegt nur der Tarif. */
    @Test
    void chargingSavings_withoutCreatedAt_noTrial() {
        User u = User.builder()
                .id(UUID.randomUUID()).email("u@example.com").username("u")
                .authProvider(AuthProvider.LOCAL).role("USER")
                .subscriptionTier(SubscriptionTier.NONE)
                .build();
        assertFalse(u.canViewChargingSavings(TRIAL_LAUNCH));
    }

    /**
     * Der Retention-Hinweis haengt an {@code isChargingSavingsViaTrial}: nur wer die Kachel
     * ausschliesslich ueber das Trial sieht, bekommt ihn - zahlende und privilegierte
     * Nutzer verlieren nichts und sehen ihn nie.
     */
    @Test
    void chargingSavingsViaTrial_onlyForFreeUserInsideWindow() {
        LocalDate during = TRIAL_LAUNCH.plusDays(10);
        assertTrue(buildUser("USER", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH).isChargingSavingsViaTrial(during));

        assertFalse(buildUser("USER", SubscriptionTier.AUTOSYNC, LONG_BEFORE_LAUNCH).isChargingSavingsViaTrial(during),
                "zahlender Nutzer sieht die Kachel ohnehin - kein Trial-Hinweis");
        assertFalse(buildUser("ADMIN", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH).isChargingSavingsViaTrial(during));
        assertFalse(buildUser("USER", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH).isChargingSavingsViaTrial(AFTER_TRIAL),
                "nach dem Trial gibt es nichts mehr zu halten");
    }

    /** Trial-Ende: launch-verankert fuer Bestandsuser, registrierungsverankert fuer Neue. */
    @Test
    void savingsTrialEndsAt_anchoredToLaterOfLaunchAndRegistration() {
        assertEquals(LAST_TRIAL_DAY,
                buildUser("USER", SubscriptionTier.NONE, LONG_BEFORE_LAUNCH).savingsTrialEndsAt());
        assertEquals(LocalDate.of(2026, 12, 1),
                buildUser("USER", SubscriptionTier.NONE, LocalDate.of(2026, 11, 1)).savingsTrialEndsAt());
    }

    private User buildUser(String role, SubscriptionTier tier) {
        return buildUser(role, tier, LocalDate.now());
    }

    private User buildUser(String role, SubscriptionTier tier, LocalDate registeredOn) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("u@example.com")
                .username("u")
                .authProvider(AuthProvider.LOCAL)
                .role(role)
                .subscriptionTier(tier)
                .createdAt(registeredOn.atStartOfDay()).updatedAt(LocalDateTime.now())
                .build();
    }
}
