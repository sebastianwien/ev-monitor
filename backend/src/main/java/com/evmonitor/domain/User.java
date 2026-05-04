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

        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider;
        this.role = role == null ? "USER" : role;
        this.emailVerified = emailVerified;
        this.seedData = seedData;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.premium = premium;
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

    /** Roles allowed to create trips manually without an AutoSync Live subscription. */
    private static final java.util.Set<String> MANUAL_TRIP_PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER");

    /**
     * Centralised gate for manual trip creation (POST /api/trips). Returns true if the
     * user is allowed to log a trip by hand. Manual creation is part of AutoSync Live
     * (the trip-detection product) - non-subscribers can still read, edit, merge and
     * delete imported trips, only creation is gated. TESLA_FOUNDER is intentionally
     * NOT included: their grandfathering covers Live-Sync only, not manual trip CRUD.
     */
    public boolean canCreateTripsManually() {
        return premium || MANUAL_TRIP_PRIVILEGED_ROLES.contains(role);
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
