package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaPromoRedemptionRepository extends JpaRepository<PromoRedemptionEntity, UUID> {

    long countByCode(String code);
}
