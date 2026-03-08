package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.AuthProvider;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_user")
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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserEntity() {
    }

    public UserEntity(UUID id, String email, String username, String passwordHash, AuthProvider authProvider, String role,
            boolean emailVerified, boolean seedData, boolean emailNotificationsEnabled,
            String referralCode, UUID referredByUserId,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider;
        this.role = role;
        this.emailVerified = emailVerified;
        this.seedData = seedData;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.referralCode = referralCode;
        this.referredByUserId = referredByUserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isSeedData() {
        return seedData;
    }

    public void setSeedData(boolean seedData) {
        this.seedData = seedData;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public UUID getReferredByUserId() {
        return referredByUserId;
    }

    public void setReferredByUserId(UUID referredByUserId) {
        this.referredByUserId = referredByUserId;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getUtmSource() {
        return utmSource;
    }

    public void setUtmSource(String utmSource) {
        this.utmSource = utmSource;
    }

    public String getUtmMedium() {
        return utmMedium;
    }

    public void setUtmMedium(String utmMedium) {
        this.utmMedium = utmMedium;
    }

    public String getUtmCampaign() {
        return utmCampaign;
    }

    public void setUtmCampaign(String utmCampaign) {
        this.utmCampaign = utmCampaign;
    }

}
