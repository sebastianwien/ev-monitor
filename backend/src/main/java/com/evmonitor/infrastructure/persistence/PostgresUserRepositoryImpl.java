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
    @Transactional
    public void delete(User user) {
        jpaUserRepository.deleteById(user.getId());
    }

    private UserEntity toEntity(User domain) {
        return new UserEntity(
                domain.getId(),
                domain.getEmail(),
                domain.getUsername(),
                domain.getPasswordHash(),
                domain.getAuthProvider(),
                domain.getRole(),
                domain.isEmailVerified(),
                domain.isSeedData(),
                domain.isEmailNotificationsEnabled(),
                domain.getCreatedAt(),
                domain.getUpdatedAt());
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
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
