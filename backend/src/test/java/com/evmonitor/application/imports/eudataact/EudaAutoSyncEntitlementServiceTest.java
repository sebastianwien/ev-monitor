package com.evmonitor.application.imports.eudataact;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.SubscriptionTier;
import com.evmonitor.domain.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wer darf den VW-EU-Data-Act-AutoSync nutzen: AutoSync-Abo, privilegierte Rollen und alle
 * anderen im launch-verankerten Trial. Launch und Dauer kommen aus der Konfiguration.
 */
class EudaAutoSyncEntitlementServiceTest {

    private static final LocalDate LAUNCH = LocalDate.of(2026, 9, 21);
    private static final LocalDate BEFORE_LAUNCH = LocalDate.of(2026, 1, 1);

    private final EudaAutoSyncEntitlementService service = new EudaAutoSyncEntitlementService(properties(30));

    private static EudaAutoSyncTrialProperties properties(int days) {
        EudaAutoSyncTrialProperties p = new EudaAutoSyncTrialProperties();
        p.setLaunchDate(LAUNCH);
        p.setDays(days);
        return p;
    }

    private static User user(String role, SubscriptionTier tier, LocalDate registeredOn) {
        return User.builder()
                .id(UUID.randomUUID()).email("u@example.com").username("u").passwordHash("x")
                .authProvider(AuthProvider.LOCAL).role(role).emailVerified(true)
                .subscriptionTier(tier)
                .createdAt(registeredOn.atStartOfDay()).updatedAt(registeredOn.atStartOfDay())
                .build();
    }

    @Test
    void freeUserWithinLaunchTrial_isEntitledViaTrial() {
        var e = service.entitlementFor(user("USER", SubscriptionTier.NONE, BEFORE_LAUNCH), LAUNCH.plusDays(30));
        assertTrue(e.entitled());
        assertTrue(e.viaTrial());
        assertEquals(LAUNCH.plusDays(30), e.trialEndsAt());
    }

    @Test
    void freeUserAfterTrial_isNotEntitled() {
        var e = service.entitlementFor(user("USER", SubscriptionTier.NONE, BEFORE_LAUNCH), LAUNCH.plusDays(31));
        assertFalse(e.entitled());
        assertFalse(e.viaTrial());
        assertEquals(LAUNCH.plusDays(30), e.trialEndsAt(), "Ablaufdatum bleibt sichtbar, damit das Frontend es nennen kann");
    }

    @Test
    void userRegisteredAfterLaunch_getsTrialFromRegistration() {
        LocalDate registered = LAUNCH.plusDays(10);
        var e = service.entitlementFor(user("USER", SubscriptionTier.NONE, registered), registered.plusDays(30));
        assertTrue(e.viaTrial());
        assertEquals(registered.plusDays(30), e.trialEndsAt());
    }

    @Test
    void trialLengthIsConfigurable() {
        var twoWeeks = new EudaAutoSyncEntitlementService(properties(14));
        User u = user("USER", SubscriptionTier.NONE, BEFORE_LAUNCH);
        assertTrue(twoWeeks.entitlementFor(u, LAUNCH.plusDays(14)).entitled());
        assertFalse(twoWeeks.entitlementFor(u, LAUNCH.plusDays(15)).entitled());
    }

    @Test
    void autoSyncTiersAndPrivilegedRoles_areEntitledWithoutTrial() {
        LocalDate longAfter = LAUNCH.plusYears(1);
        for (SubscriptionTier tier : new SubscriptionTier[]{SubscriptionTier.AUTOSYNC, SubscriptionTier.AUTOSYNC_LIVE}) {
            var e = service.entitlementFor(user("USER", tier, BEFORE_LAUNCH), longAfter);
            assertTrue(e.entitled(), tier.name());
            assertFalse(e.viaTrial(), tier.name());
        }
        var admin = service.entitlementFor(user("ADMIN", SubscriptionTier.NONE, BEFORE_LAUNCH), longAfter);
        assertTrue(admin.entitled());
        assertFalse(admin.viaTrial());
    }

    @Test
    void supporterTier_doesNotUnlockAutoSync() {
        var e = service.entitlementFor(user("USER", SubscriptionTier.SUPPORTER, BEFORE_LAUNCH), LAUNCH.plusYears(1));
        assertFalse(e.entitled());
    }

    @Test
    void paidUserWithinTrial_isNotFlaggedAsTrial() {
        var e = service.entitlementFor(user("USER", SubscriptionTier.AUTOSYNC, BEFORE_LAUNCH), LAUNCH);
        assertTrue(e.entitled());
        assertFalse(e.viaTrial(), "Zahlende verlieren nichts - kein Ablauf-Hinweis");
    }
}
