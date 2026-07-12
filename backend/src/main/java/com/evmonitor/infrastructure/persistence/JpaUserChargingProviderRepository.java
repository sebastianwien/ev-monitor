package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaUserChargingProviderRepository extends JpaRepository<UserChargingProviderEntity, UUID> {

    List<UserChargingProviderEntity> findByUserIdOrderByActiveFromDesc(UUID userId);

    /** Cards the user currently holds. active_until IS NULL is the "still in my wallet" contract. */
    List<UserChargingProviderEntity> findByUserIdAndActiveUntilIsNull(UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
