package com.evmonitor.infrastructure.persistence.waitlist;

import com.evmonitor.domain.WaitlistFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface FeatureWaitlistRepository extends JpaRepository<FeatureWaitlistEntry, UUID> {

    Optional<FeatureWaitlistEntry> findByUserIdAndFeature(UUID userId, WaitlistFeature feature);

    long countByFeature(WaitlistFeature feature);

    @Transactional
    void deleteByUserIdAndFeature(UUID userId, WaitlistFeature feature);
}
