package com.evmonitor.domain;

import java.time.LocalDate;
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

    void delete(User user);

    Optional<User> findByReferralCode(String referralCode);

    long countVerifiedReferrals(UUID referrerId);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    void setPremium(UUID userId, boolean premium);

    void setStripeCustomerId(UUID userId, String stripeCustomerId);

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
}
