package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaTariffProposalRepository extends JpaRepository<TariffProposalEntity, UUID> {

    List<TariffProposalEntity> findBySourceIdAndStatus(UUID sourceId, TariffProposalEntity.Status status);

    List<TariffProposalEntity> findByStatusOrderByFirstSeenAtAsc(TariffProposalEntity.Status status);

    Optional<TariffProposalEntity> findBySourceIdAndFingerprintAndStatus(UUID sourceId, String fingerprint, TariffProposalEntity.Status status);

    long countByStatus(TariffProposalEntity.Status status);
}
