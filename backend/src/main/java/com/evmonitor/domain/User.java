package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private final String referralCode;
    private final UUID referredByUserId;
    private final String stripeCustomerId;
    private final String utmSource;
    private final String utmMedium;
    private final String utmCampaign;
    private final String referrerSource;
    private final String registrationLocale;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public User(UUID id, String email, String username, String passwordHash, AuthProvider authProvider, String role,
            boolean emailVerified, boolean seedData, boolean emailNotificationsEnabled, boolean premium,
            String referralCode, UUID referredByUserId, String stripeCustomerId,
            String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale, LocalDateTime createdAt, LocalDateTime updatedAt) {
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
        this.referralCode = referralCode;
        this.referredByUserId = referredByUserId;
        this.stripeCustomerId = stripeCustomerId;
        this.utmSource = utmSource;
        this.utmMedium = utmMedium;
        this.utmCampaign = utmCampaign;
        this.referrerSource = referrerSource;
        this.registrationLocale = registrationLocale;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private static String generateReferralCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public static User createNewLocalUser(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", false, false, true, false, generateReferralCode(), null, null, null, null, null, null, null, now, now);
    }

    public static User createNewLocalUserWithReferrer(String email, String username, String passwordHash, UUID referredByUserId) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", false, false, true, false, generateReferralCode(), referredByUserId, null, null, null, null, null, null, now, now);
    }

    public static User createNewLocalUserWithCampaign(String email, String username, String passwordHash, UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", false, false, true, false, generateReferralCode(), referredByUserId, null, utmSource, utmMedium, utmCampaign, referrerSource, null, now, now);
    }

    public static User createNewLocalUserWithLocale(String email, String username, String passwordHash, UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource, String registrationLocale) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", false, false, true, false, generateReferralCode(), referredByUserId, null, utmSource, utmMedium, utmCampaign, referrerSource, registrationLocale, now, now);
    }

    public static User createVerifiedLocalUser(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", true, false, true, false, generateReferralCode(), null, null, null, null, null, null, null, now, now);
    }

    public static User createSeedUser(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", true, true, false, false, generateReferralCode(), null, null, null, null, null, null, null, now, now);
    }

    public static User createNewSsoUser(String email, String username, AuthProvider authProvider) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, null, authProvider, "USER", true, false, true, false, generateReferralCode(), null, null, null, null, null, null, null, now, now);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public String getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getUsername() { return username; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isSeedData() { return seedData; }
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public boolean isPremium() { return premium; }
    public String getReferralCode() { return referralCode; }
    public UUID getReferredByUserId() { return referredByUserId; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getUtmSource() { return utmSource; }
    public String getUtmMedium() { return utmMedium; }
    public String getUtmCampaign() { return utmCampaign; }
    public String getReferrerSource() { return referrerSource; }
    public String getRegistrationLocale() { return registrationLocale; }
}
