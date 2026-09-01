package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE UserEntity u SET u.emailVerified = true WHERE u.id = :userId")
    void markEmailVerified(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.emailNotificationsEnabled = false WHERE u.id = :userId")
    void disableEmailNotifications(@Param("userId") UUID userId);

    @Query("SELECT u FROM UserEntity u WHERE u.emailVerified = true AND u.emailNotificationsEnabled = true AND u.seedData = false AND cast(u.createdAt as LocalDate) = :day")
    List<UserEntity> findRegisteredOnDay(@Param("day") LocalDate day);

    /**
     * Users whose last log/trip is on or before {@code day}, not yet mailed (see
     * {@link #markReEngagementEmailSent}).
     *
     * <p>{@code <=} plus the {@code re_engagement_email_sent_at IS NULL} guard replaces an
     * exact-day match on purpose - see {@link #findDormantAutoSyncUsersDue} for why: it
     * absorbs any backlog of users whose threshold day predates this feature or was missed by
     * a deploy, and the flag guarantees each user is mailed at most once.
     */
    @Query(value = """
            SELECT u.* FROM app_user u
            WHERE u.email_verified = true
              AND u.email_notifications_enabled = true
              AND u.is_seed_data = false
              AND u.re_engagement_email_sent_at IS NULL
              AND GREATEST(
                (
                  SELECT MAX(e.logged_at)::date
                  FROM ev_log e
                  JOIN car c ON c.id = e.car_id
                  WHERE c.user_id = u.id
                ),
                (
                  SELECT MAX(t.trip_ended_at)::date
                  FROM ev_trip t
                  WHERE t.user_id = u.id
                    AND t.deleted_at IS NULL
                )
              ) <= :day
            """, nativeQuery = true)
    List<UserEntity> findUsersDueForReEngagement(@Param("day") LocalDate day);

    @Modifying
    @Query("UPDATE UserEntity u SET u.reEngagementEmailSentAt = :now WHERE u.id = :userId")
    void markReEngagementEmailSent(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Users last seen on or before {@code day} whose car is still logging via a live connector -
     * data sources here must stay in sync with the "continuous auto-sync" group in
     * {@link com.evmonitor.domain.DataSource} (TESLA_LIVE, SMARTCAR_LIVE, VWGROUP_LIVE,
     * XPENG_LIVE), the connector-driven sources that keep writing logs without any user action,
     * as opposed to one-off imports like TESLA_FLEET_IMPORT.
     *
     * <p>{@code <=} plus the {@code dormant_autosync_email_sent_at IS NULL} guard (set once via
     * {@link #markDormantAutoSyncEmailSent}) replaces an exact-day match on purpose: with an
     * exact match, anyone whose last_seen already crossed the threshold before this feature
     * existed - or whose exact-day run got missed by a deploy - would never be mailed. This way
     * the regular daily run absorbs any backlog on its own; the flag guarantees each user is
     * mailed at most once regardless of how many days match the {@code <=}.
     */
    @Query(value = """
            SELECT u.* FROM app_user u
            WHERE u.email_verified = true
              AND u.email_notifications_enabled = true
              AND u.is_seed_data = false
              AND u.dormant_autosync_email_sent_at IS NULL
              AND u.last_seen::date <= :day
              AND EXISTS (
                SELECT 1 FROM ev_log e
                JOIN car c ON c.id = e.car_id
                WHERE c.user_id = u.id
                  AND e.data_source IN ('TESLA_LIVE', 'SMARTCAR_LIVE', 'VWGROUP_LIVE', 'XPENG_LIVE')
                  AND e.logged_at >= (CURRENT_DATE - 7)
              )
            """, nativeQuery = true)
    List<UserEntity> findDormantAutoSyncUsersDue(@Param("day") LocalDate day);

    @Modifying
    @Query("UPDATE UserEntity u SET u.dormantAutoSyncEmailSentAt = :now WHERE u.id = :userId")
    void markDormantAutoSyncEmailSent(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    long countBySeedDataFalseAndEmailVerifiedTrue();

    Optional<UserEntity> findByReferralCode(String referralCode);

    long countByReferredByUserIdAndEmailVerifiedTrue(UUID referredByUserId);

    Optional<UserEntity> findByStripeCustomerId(String stripeCustomerId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.premium = :premium WHERE u.id = :userId")
    void setPremium(@Param("userId") UUID userId, @Param("premium") boolean premium);

    /**
     * Atomic tier-flip that also keeps the legacy is_premium flag in sync. New
     * code (Stripe webhook, upgrade/downgrade endpoints) should call this; the
     * standalone setPremium remains for transitional callers but is deprecated.
     */
    @Modifying
    @Query("UPDATE UserEntity u SET u.subscriptionTier = :tier, u.premium = :paid WHERE u.id = :userId")
    void setSubscriptionTier(@Param("userId") UUID userId,
                             @Param("tier") String tier,
                             @Param("paid") boolean paid);

    @Modifying
    @Query("UPDATE UserEntity u SET u.stripeCustomerId = :customerId WHERE u.id = :userId")
    void setStripeCustomerId(@Param("userId") UUID userId, @Param("customerId") String customerId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.subscriptionPeriodEnd = :periodEnd WHERE u.id = :userId")
    void setSubscriptionPeriodEnd(@Param("userId") UUID userId, @Param("periodEnd") Instant periodEnd);

    @Modifying
    @Query("UPDATE UserEntity u SET u.trialUsed = true WHERE u.id = :userId")
    void markTrialUsed(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.autosyncStartedAt = :startedAt WHERE u.id = :userId AND u.autosyncStartedAt IS NULL")
    void setAutoSyncStartedAtIfNull(@Param("userId") UUID userId, @Param("startedAt") Instant startedAt);

    @Query("SELECT u FROM UserEntity u WHERE u.emailNotificationsEnabled = true AND u.seedData = false "
            + "AND u.subscriptionTier IN ('AUTOSYNC', 'AUTOSYNC_LIVE') AND cast(u.autosyncStartedAt as LocalDate) = :day")
    List<UserEntity> findAutoSyncSurveyCandidates(@Param("day") LocalDate day);

    /**
     * Atomically claims the referral reward for a user.
     * Only updates if referral_reward_given is currently false.
     * Returns 1 if the claim succeeded (this thread "won"), 0 if already claimed.
     * This prevents double-crediting under concurrent webhook delivery.
     */
    @Modifying
    @Query("UPDATE UserEntity u SET u.referralRewardGiven = true WHERE u.id = :userId AND u.referralRewardGiven = false")
    int claimReferralReward(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.passwordHash = :passwordHash WHERE u.id = :userId")
    void updatePassword(@Param("userId") UUID userId, @Param("passwordHash") String passwordHash);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.email = :email, u.emailVerified = false, u.updatedAt = current_timestamp WHERE u.id = :userId")
    void updateEmail(@Param("userId") UUID userId, @Param("email") String email);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.username = :username, u.updatedAt = current_timestamp WHERE u.id = :userId")
    void updateUsername(@Param("userId") UUID userId, @Param("username") String username);

    @Query("SELECT u.leaderboardVisible FROM UserEntity u WHERE u.id = :userId")
    boolean isLeaderboardVisible(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.leaderboardVisible = :visible, u.updatedAt = current_timestamp WHERE u.id = :userId")
    void setLeaderboardVisible(@Param("userId") UUID userId, @Param("visible") boolean visible);

    @Modifying
    @Query("UPDATE UserEntity u SET u.lastSeen = :now WHERE u.id IN :ids")
    void batchUpdateLastSeen(@Param("ids") List<UUID> ids, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserEntity u SET u.country = :country, u.updatedAt = current_timestamp WHERE u.id = :userId")
    void updateCountry(@Param("userId") UUID userId, @Param("country") String country);
}
