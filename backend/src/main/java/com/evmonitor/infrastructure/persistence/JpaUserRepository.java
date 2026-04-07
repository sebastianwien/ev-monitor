package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query(value = """
            SELECT u.* FROM app_user u
            WHERE u.email_verified = true
              AND u.email_notifications_enabled = true
              AND u.is_seed_data = false
              AND (
                SELECT MAX(e.logged_at)::date
                FROM ev_log e
                JOIN car c ON c.id = e.car_id
                WHERE c.user_id = u.id
              ) = :day
            """, nativeQuery = true)
    List<UserEntity> findUsersWithLastLogOnDay(@Param("day") LocalDate day);

    long countBySeedDataFalseAndEmailVerifiedTrue();

    Optional<UserEntity> findByReferralCode(String referralCode);

    long countByReferredByUserIdAndEmailVerifiedTrue(UUID referredByUserId);

    Optional<UserEntity> findByStripeCustomerId(String stripeCustomerId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.premium = :premium WHERE u.id = :userId")
    void setPremium(@Param("userId") UUID userId, @Param("premium") boolean premium);

    @Modifying
    @Query("UPDATE UserEntity u SET u.stripeCustomerId = :customerId WHERE u.id = :userId")
    void setStripeCustomerId(@Param("userId") UUID userId, @Param("customerId") String customerId);

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
