package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import org.springframework.stereotype.Component;

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
    public boolean existsByEmail(String email) {
        return jpaUserRepository.existsByEmail(email);
    }

    private UserEntity toEntity(User domain) {
        return new UserEntity(
                domain.getId(),
                domain.getEmail(),
                domain.getPasswordHash(),
                domain.getAuthProvider(),
                domain.getRole(),
                domain.getCreatedAt(),
                domain.getUpdatedAt());
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getAuthProvider(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
