package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface JpaEmailLoginCodeRepository extends JpaRepository<EmailLoginCodeEntity, UUID> {

    // Sperre bis Transaktionsende: parallele Fehlversuche dürfen den Zähler nicht überschreiben
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailLoginCodeEntity> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("DELETE FROM EmailLoginCodeEntity c WHERE c.email = :email")
    void deleteByEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM EmailLoginCodeEntity c WHERE c.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
