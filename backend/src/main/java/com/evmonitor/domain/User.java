package com.evmonitor.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class User {
    private final UUID id;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final AuthProvider authProvider;
    private final String role;
    private final boolean emailVerified;
    private final boolean seedData;
    private final boolean emailNotificationsEnabled;
    private final boolean premium;
    private final SubscriptionTier subscriptionTier;
    private final boolean referralRewardGiven;
    private final String referralCode;
    private final UUID referredByUserId;
    private final String stripeCustomerId;
    private final String utmSource;
    private final String utmMedium;
    private final String utmCampaign;
    private final String referrerSource;
    private final String registrationLocale;
    private final String country;
    private final Instant subscriptionPeriodEnd;
    private final boolean trialUsed;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Builder(toBuilder = true)
    private User(UUID id, String email, String username, String passwordHash, AuthProvider authProvider, String role,
            boolean emailVerified, boolean seedData, boolean emailNotificationsEnabled, boolean premium,
            SubscriptionTier subscriptionTier,
            boolean referralRewardGiven, String referralCode, UUID referredByUserId, String stripeCustomerId,
            String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale, String country, Instant subscriptionPeriodEnd, boolean trialUsed,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null)
            throw new IllegalArgumentException("User ID cannot be null");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be empty");
        if (authProvider == null)
            throw new IllegalArgumentException("Auth Provider cannot be null");

        // Reconcile legacy `premium` flag with new `subscriptionTier`. Tier is the
        // source of truth going forward; if only `premium` was set (older callers,
        // existing rows pre-V112), assume AUTOSYNC. If neither is set, default NONE.
        // toBuilder() copies tier=NONE from a freshly-built user, so a follow-up
        // .premium(true) would otherwise stay tier=NONE - upgrade it here too.
        if (subscriptionTier == null) {
            subscriptionTier = SubscriptionTier.NONE;
        }
        if (premium && subscriptionTier == SubscriptionTier.NONE) {
            subscriptionTier = SubscriptionTier.AUTOSYNC;
        }

        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider;
        this.role = role == null ? "USER" : role;
        this.emailVerified = emailVerified;
        this.seedData = seedData;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.subscriptionTier = subscriptionTier;
        this.premium = subscriptionTier.isPaid();
        this.referralRewardGiven = referralRewardGiven;
        this.referralCode = referralCode;
        this.referredByUserId = referredByUserId;
        this.stripeCustomerId = stripeCustomerId;
        this.utmSource = utmSource;
        this.utmMedium = utmMedium;
        this.utmCampaign = utmCampaign;
        this.referrerSource = referrerSource;
        this.registrationLocale = registrationLocale;
        this.country = country;
        this.subscriptionPeriodEnd = subscriptionPeriodEnd;
        this.trialUsed = trialUsed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Roles allowed to activate Live-Sync (Tesla Telemetry, Smartcar Webhooks) without an AutoSync subscription. */
    private static final java.util.Set<String> TELEMETRY_PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER", "TESLA_FOUNDER");

    /**
     * Centralised gate for Live-Sync activation. Returns true if the user is allowed to
     * push a Telemetry config to their vehicle - either via a privileged role
     * (TESLA_FOUNDER grandfathering, BETA_TESTER, ADMIN) or via an active AutoSync
     * subscription.
     */
    public boolean canActivateTelemetry() {
        return premium || TELEMETRY_PRIVILEGED_ROLES.contains(role);
    }

    /**
     * Centralised gate for viewing live-detected trips (SMARTCAR_LIVE, TESLA_LIVE,
     * TESLA_INFERRED) in the dashboard. Same gate as {@link #canUseTripPush()}: the
     * Tier-2 entitlement covers both the streaming/push and the display side.
     * Tessie-imported trips bypass this gate (handled in TripService by data_source
     * whitelist), because those are the user's own historical data.
     */
    public boolean canViewLiveTrips() {
        return subscriptionTier == SubscriptionTier.AUTOSYNC_LIVE
                || TRIP_PUSH_PRIVILEGED_ROLES.contains(role);
    }

    /**
     * True if the user is allowed to see live-detected trips even for car models that
     * are NOT on the live-trip eligibility whitelist (see {@code TripDetectionEligibility}).
     * Granted to ADMIN + BETA_TESTER for data-quality audit via impersonation.
     * AutoSync-Live customers stay bound to the whitelist - if their car model is
     * BLOCKED, live trips are silently filtered out of their UI even though they pay
     * for Tier-2. The data itself is still collected in the DB so eligibility can be
     * flipped retroactively when OEM data quality improves.
     */
    public boolean canBypassEligibilityGate() {
        return ELIGIBILITY_BYPASS_ROLES.contains(role);
    }

    /** Roles that bypass the car-model eligibility filter (audit access only). */
    private static final java.util.Set<String> ELIGIBILITY_BYPASS_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER");

    /** Roles that always stream the FULL profile regardless of subscription tier. */
    private static final java.util.Set<String> FULL_PROFILE_PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER");

    /** Roles that may push trips/phantom-drain regardless of subscription tier. */
    private static final java.util.Set<String> TRIP_PUSH_PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER");

    /**
     * Telemetry profile this user is entitled to. Drives the connectors-service
     * profile push. Caller MUST have already verified {@link #canActivateTelemetry()}.
     *
     * <ul>
     *   <li>Live-tier subscribers (AUTOSYNC_LIVE) → FULL</li>
     *   <li>BETA_TESTER, ADMIN → FULL (privileged roles always get trip-streaming)</li>
     *   <li>everyone else (AUTOSYNC, TESLA_FOUNDER, etc.) → CHARGING_ONLY</li>
     * </ul>
     */
    public TelemetryProfile preferredTelemetryProfile() {
        if (subscriptionTier == SubscriptionTier.AUTOSYNC_LIVE) {
            return TelemetryProfile.FULL;
        }
        if (FULL_PROFILE_PRIVILEGED_ROLES.contains(role)) {
            return TelemetryProfile.FULL;
        }
        return TelemetryProfile.CHARGING_ONLY;
    }

    /**
     * True if the user may push live trips and phantom-drain reports. Distinct
     * from {@link #canActivateTelemetry()} which only gates "may stream charging
     * data at all". TESLA_FOUNDER grandfathering does NOT cover trip-push.
     */
    public boolean canUseTripPush() {
        return subscriptionTier == SubscriptionTier.AUTOSYNC_LIVE
                || TRIP_PUSH_PRIVILEGED_ROLES.contains(role);
    }

    private static String generateReferralCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public static User createNewLocalUser(String email, String username, String passwordHash) {
        return newLocalUserBuilder(email, username, passwordHash).build();
    }

    public static User createNewLocalUserWithReferrer(String email, String username, String passwordHash, UUID referredByUserId) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .build();
    }

    public static User createNewLocalUserWithCampaign(String email, String username, String passwordHash,
            UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .utmSource(utmSource).utmMedium(utmMedium).utmCampaign(utmCampaign).referrerSource(referrerSource)
                .build();
    }

    public static User createNewLocalUserWithLocale(String email, String username, String passwordHash,
            UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .utmSource(utmSource).utmMedium(utmMedium).utmCampaign(utmCampaign).referrerSource(referrerSource)
                .registrationLocale(registrationLocale)
                .build();
    }

    public static User createNewLocalUserWithLocaleAndCountry(String email, String username, String passwordHash,
            UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale, String country) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .utmSource(utmSource).utmMedium(utmMedium).utmCampaign(utmCampaign).referrerSource(referrerSource)
                .registrationLocale(registrationLocale).country(country)
                .build();
    }

    public static User createVerifiedLocalUser(String email, String username, String passwordHash) {
        return newLocalUserBuilder(email, username, passwordHash)
                .emailVerified(true)
                .build();
    }

    public static User createNewSsoUser(String email, String username, AuthProvider authProvider) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email(email).username(username)
                .authProvider(authProvider).role("USER")
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode(generateReferralCode())
                .createdAt(now).updatedAt(now)
                .build();
    }

    private static UserBuilder newLocalUserBuilder(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email(email).username(username).passwordHash(passwordHash)
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailNotificationsEnabled(true)
                .referralCode(generateReferralCode())
                .createdAt(now).updatedAt(now);
    }
}
