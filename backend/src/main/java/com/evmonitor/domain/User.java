package com.evmonitor.domain;

import lombok.Builder;
import lombok.Getter;

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
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Builder(toBuilder = true)
    private User(UUID id, String email, String username, String passwordHash, AuthProvider authProvider, String role,
            boolean emailVerified, boolean seedData, boolean emailNotificationsEnabled, boolean premium,
            boolean referralRewardGiven, String referralCode, UUID referredByUserId, String stripeCustomerId,
            String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale, String country, LocalDateTime createdAt, LocalDateTime updatedAt) {
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public static User createSeedUser(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email(email).username(username).passwordHash(passwordHash)
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailVerified(true).seedData(true).emailNotificationsEnabled(false)
                .referralCode(generateReferralCode())
                .createdAt(now).updatedAt(now)
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
