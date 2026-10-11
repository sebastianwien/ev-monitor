package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface JpaEmailLoginCodeRepository extends JpaRepository<EmailLoginCodeEntity, UUID> {

    Optional<EmailLoginCodeEntity> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("DELETE FROM EmailLoginCodeEntity c WHERE c.email = :email")
    void deleteByEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM EmailLoginCodeEntity c WHERE c.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
