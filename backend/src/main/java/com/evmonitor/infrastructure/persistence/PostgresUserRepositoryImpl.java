package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private UserEntity toEntity(User domain) {
        UserEntity entity = new UserEntity(
                domain.getId(),
                domain.getEmail(),
                domain.getUsername(),
                domain.getPasswordHash(),
                domain.getAuthProvider(),
                domain.getRole(),
                domain.isEmailVerified(),
                domain.isSeedData(),
                domain.isEmailNotificationsEnabled(),
                domain.getReferralCode(),
                domain.getReferredByUserId(),
                domain.getCreatedAt(),
                domain.getUpdatedAt());
        entity.setPremium(domain.isPremium());
        entity.setStripeCustomerId(domain.getStripeCustomerId());
        entity.setUtmSource(domain.getUtmSource());
        entity.setUtmMedium(domain.getUtmMedium());
        entity.setUtmCampaign(domain.getUtmCampaign());
        entity.setReferrerSource(domain.getReferrerSource());
        entity.setRegistrationLocale(domain.getRegistrationLocale());
        return entity;
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getAuthProvider(),
                entity.getRole(),
                entity.isEmailVerified(),
                entity.isSeedData(),
                entity.isEmailNotificationsEnabled(),
                entity.isPremium(),
                entity.getReferralCode(),
                entity.getReferredByUserId(),
                entity.getStripeCustomerId(),
                entity.getUtmSource(),
                entity.getUtmMedium(),
                entity.getUtmCampaign(),
                entity.getReferrerSource(),
                entity.getRegistrationLocale(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
