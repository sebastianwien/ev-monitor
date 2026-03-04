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

    void delete(User user);

    Optional<User> findByReferralCode(String referralCode);

    long countVerifiedReferrals(UUID referrerId);
}
