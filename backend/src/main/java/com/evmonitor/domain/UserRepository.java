package com.evmonitor.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    void markEmailVerified(UUID userId);

    void disableEmailNotifications(UUID userId);

    List<User> findRegisteredOnDay(LocalDate day);

    List<User> findUsersWithLastLogOnDay(LocalDate day);

    /**
     * Users last seen on or before {@code day}, not yet mailed (see
     * {@link #markDormantAutoSyncEmailSent}), whose car is still logging via a live connector
     * (TESLA_LIVE/SMARTCAR_LIVE/VWGROUP_LIVE/XPENG_LIVE, see {@link DataSource}).
     * {@link #findUsersWithLastLogOnDay} never fires for this group - the connector keeps
     * producing fresh logs regardless of whether the person still opens the app.
     */
    List<User> findDormantAutoSyncUsersDue(LocalDate day);

    /**
     * Marks the dormant-AutoSync email as sent so {@link #findDormantAutoSyncUsersDue} never
     * selects this user again.
     */
    void markDormantAutoSyncEmailSent(UUID userId, LocalDateTime now);

    void delete(User user);

    Optional<User> findByReferralCode(String referralCode);

    long countVerifiedReferrals(UUID referrerId);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    void setPremium(UUID userId, boolean premium);

    /**
     * Atomically updates {@code subscription_tier} and keeps the legacy
     * {@code is_premium} flag in sync (premium = tier != NONE). Preferred
     * over {@link #setPremium} for new code.
     */
    void setSubscriptionTier(UUID userId, SubscriptionTier tier);

    void setStripeCustomerId(UUID userId, String stripeCustomerId);

    void setSubscriptionPeriodEnd(UUID userId, Instant periodEnd);

    void markTrialUsed(UUID userId);

    /**
     * Records the AutoSync purchase moment, but only the first time (atomic IF NULL).
     * Stable across renewals/upgrades so it can anchor the satisfaction survey mail.
     */
    void setAutoSyncStartedAtIfNull(UUID userId, Instant startedAt);

    /**
     * Active AutoSync subscribers whose purchase day equals {@code startedOnDay},
     * excluding seed data and users who opted out of email notifications. Drives the
     * "how is AutoSync working for you" survey scheduler.
     */
    List<User> findAutoSyncSurveyCandidates(LocalDate startedOnDay);

    /**
     * Atomically claims the referral reward. Returns true if this call won the race
     * (i.e. the reward was not yet given), false if already claimed.
     */
    boolean claimReferralReward(UUID userId);

    void updatePassword(UUID userId, String passwordHash);

    void updateEmail(UUID userId, String email);

    void updateUsername(UUID userId, String username);

    boolean isLeaderboardVisible(UUID userId);

    void setLeaderboardVisible(UUID userId, boolean visible);

    void updateCountry(UUID userId, String country);

    List<User> findAllByIds(List<UUID> ids);
}
