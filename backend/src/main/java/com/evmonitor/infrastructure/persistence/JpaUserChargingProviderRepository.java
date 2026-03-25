package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaUserChargingProviderRepository extends JpaRepository<UserChargingProviderEntity, UUID> {

    List<UserChargingProviderEntity> findByUserIdOrderByActiveFromDesc(UUID userId);

    Optional<UserChargingProviderEntity> findByUserIdAndActiveUntilIsNull(UUID userId);

    @Modifying
    @Query("UPDATE UserChargingProviderEntity e SET e.activeUntil = :until WHERE e.userId = :userId AND e.activeUntil IS NULL")
    void deactivateCurrent(@Param("userId") UUID userId, @Param("until") LocalDate until);
}
