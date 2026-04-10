package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.AuthProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 50)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Column(nullable = false)
    private String role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "is_seed_data", nullable = false)
    private boolean seedData;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled;

    @Column(name = "is_premium", nullable = false)
    private boolean premium;

    @Column(name = "referral_reward_given", nullable = false)
    private boolean referralRewardGiven;

    @Column(name = "referral_code", nullable = false, unique = true)
    private String referralCode;

    @Column(name = "referred_by_user_id")
    private UUID referredByUserId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    @Column(name = "referrer_source", length = 255)
    private String referrerSource;

    @Column(name = "registration_locale", length = 10)
    private String registrationLocale;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "leaderboard_visible", nullable = false)
    private boolean leaderboardVisible = true;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "subscription_period_end")
    private Instant subscriptionPeriodEnd;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
