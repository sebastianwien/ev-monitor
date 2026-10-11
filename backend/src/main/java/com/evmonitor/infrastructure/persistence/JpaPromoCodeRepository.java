package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaPromoCodeRepository extends JpaRepository<PromoCodeEntity, String> {

    Optional<PromoCodeEntity> findFirstByRequiredUtmSourceIgnoreCaseAndActiveTrue(String utmSource);
}
