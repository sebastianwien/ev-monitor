package com.evmonitor.infrastructure.persistence.xpeng;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface XpengConnectionRepository extends JpaRepository<XpengConnection, UUID> {
    Optional<XpengConnection> findByCarId(UUID carId);
    List<XpengConnection> findAllByUserIdAndConsentRevokedAtIsNull(UUID userId);
}
