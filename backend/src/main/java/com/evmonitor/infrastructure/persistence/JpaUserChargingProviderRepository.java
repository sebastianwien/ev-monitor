package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaUserChargingProviderRepository extends JpaRepository<UserChargingProviderEntity, UUID> {

    List<UserChargingProviderEntity> findByUserIdOrderByActiveFromDesc(UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
