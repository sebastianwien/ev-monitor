package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaEmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {
    Optional<EmailVerificationTokenEntity> findByToken(String token);
    void deleteByUserId(UUID userId);

    @Query("SELECT t FROM EmailVerificationTokenEntity t WHERE t.userId = :userId ORDER BY t.createdAt DESC LIMIT 1")
    Optional<EmailVerificationTokenEntity> findMostRecentByUserId(@Param("userId") UUID userId);
}
