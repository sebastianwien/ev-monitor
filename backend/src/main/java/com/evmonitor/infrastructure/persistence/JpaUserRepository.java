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

    @Modifying
    @Query("UPDATE UserEntity u SET u.passwordHash = :passwordHash WHERE u.id = :userId")
    void updatePassword(@Param("userId") UUID userId, @Param("passwordHash") String passwordHash);

    @Modifying
    @Query("UPDATE UserEntity u SET u.lastSeen = :now WHERE u.id IN :ids")
    void batchUpdateLastSeen(@Param("ids") List<UUID> ids, @Param("now") LocalDateTime now);
}
