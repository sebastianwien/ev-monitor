package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresUserRepositoryImpl implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    public PostgresUserRepositoryImpl(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity savedEntity = jpaUserRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaUserRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaUserRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaUserRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaUserRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaUserRepository.existsByUsername(username);
    }

    @Override
    @Transactional
    public void markEmailVerified(UUID userId) {
        jpaUserRepository.markEmailVerified(userId);
    }

    @Override
    @Transactional
    public void disableEmailNotifications(UUID userId) {
        jpaUserRepository.disableEmailNotifications(userId);
    }

    @Override
    public List<User> findRegisteredOnDay(LocalDate day) {
        return jpaUserRepository.findRegisteredOnDay(day)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<User> findUsersWithLastLogOnDay(LocalDate day) {
        return jpaUserRepository.findUsersWithLastLogOnDay(day)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(User user) {
        jpaUserRepository.deleteById(user.getId());
    }

    @Override
    public Optional<User> findByReferralCode(String referralCode) {
        return jpaUserRepository.findByReferralCode(referralCode).map(this::toDomain);
    }

    @Override
    public long countVerifiedReferrals(UUID referrerId) {
        return jpaUserRepository.countByReferredByUserIdAndEmailVerifiedTrue(referrerId);
    }


    @Override
    public Optional<User> findByStripeCustomerId(String stripeCustomerId) {
        return jpaUserRepository.findByStripeCustomerId(stripeCustomerId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void setPremium(UUID userId, boolean premium) {
        jpaUserRepository.setPremium(userId, premium);
    }

    @Override
    @Transactional
    public void setStripeCustomerId(UUID userId, String stripeCustomerId) {
        jpaUserRepository.setStripeCustomerId(userId, stripeCustomerId);
    }

    @Override
    @Transactional
    public void updatePassword(UUID userId, String passwordHash) {
        jpaUserRepository.updatePassword(userId, passwordHash);
    }

    @Override
    @Transactional
    public void updateEmail(UUID userId, String email) {
        jpaUserRepository.updateEmail(userId, email);
    }

    @Override
    @Transactional
    public void updateUsername(UUID userId, String username) {
        jpaUserRepository.updateUsername(userId, username);
    }

    @Override
    public boolean isLeaderboardVisible(UUID userId) {
        return jpaUserRepository.isLeaderboardVisible(userId);
    }

    @Override
    @Transactional
    public void setLeaderboardVisible(UUID userId, boolean visible) {
        jpaUserRepository.setLeaderboardVisible(userId, visible);
    }

    @Override
    @Transactional
    public void updateCountry(UUID userId, String country) {
        jpaUserRepository.updateCountry(userId, country);
    }

    @Override
    @Transactional
    public void setSubscriptionPeriodEnd(UUID userId, Instant periodEnd) {
        jpaUserRepository.setSubscriptionPeriodEnd(userId, periodEnd);
    }

    private UserEntity toEntity(User domain) {
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setEmail(domain.getEmail());
        entity.setUsername(domain.getUsername());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setAuthProvider(domain.getAuthProvider());
        entity.setRole(domain.getRole());
        entity.setEmailVerified(domain.isEmailVerified());
        entity.setSeedData(domain.isSeedData());
        entity.setEmailNotificationsEnabled(domain.isEmailNotificationsEnabled());
        entity.setPremium(domain.isPremium());
        entity.setReferralCode(domain.getReferralCode());
        entity.setReferredByUserId(domain.getReferredByUserId());
        entity.setStripeCustomerId(domain.getStripeCustomerId());
        entity.setUtmSource(domain.getUtmSource());
        entity.setUtmMedium(domain.getUtmMedium());
        entity.setUtmCampaign(domain.getUtmCampaign());
        entity.setReferrerSource(domain.getReferrerSource());
        entity.setRegistrationLocale(domain.getRegistrationLocale());
        entity.setCountry(domain.getCountry());
        entity.setSubscriptionPeriodEnd(domain.getSubscriptionPeriodEnd());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    @Override
    @Transactional
    public boolean claimReferralReward(UUID userId) {
        return jpaUserRepository.claimReferralReward(userId) > 0;
    }

    private User toDomain(UserEntity entity) {
        return User.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .username(entity.getUsername())
                .passwordHash(entity.getPasswordHash())
                .authProvider(entity.getAuthProvider())
                .role(entity.getRole())
                .emailVerified(entity.isEmailVerified())
                .seedData(entity.isSeedData())
                .emailNotificationsEnabled(entity.isEmailNotificationsEnabled())
                .premium(entity.isPremium())
                .referralRewardGiven(entity.isReferralRewardGiven())
                .referralCode(entity.getReferralCode())
                .referredByUserId(entity.getReferredByUserId())
                .stripeCustomerId(entity.getStripeCustomerId())
                .utmSource(entity.getUtmSource())
                .utmMedium(entity.getUtmMedium())
                .utmCampaign(entity.getUtmCampaign())
                .referrerSource(entity.getReferrerSource())
                .registrationLocale(entity.getRegistrationLocale())
                .country(entity.getCountry())
                .subscriptionPeriodEnd(entity.getSubscriptionPeriodEnd())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
