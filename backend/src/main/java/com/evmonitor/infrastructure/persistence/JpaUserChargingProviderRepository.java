package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaUserChargingProviderRepository extends JpaRepository<UserChargingProviderEntity, UUID> {

    /** The user's portfolio. Soft-deleted cards stay in the table but leave the wallet. */
    List<UserChargingProviderEntity> findByUserIdAndDeletedAtIsNullOrderByActiveFromDesc(UUID userId);

    boolean existsByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
