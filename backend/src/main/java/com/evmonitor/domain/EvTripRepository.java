package com.evmonitor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvTripRepository extends JpaRepository<EvTrip, UUID> {

    Optional<EvTrip> findByExternalId(UUID externalId);

    List<EvTrip> findByCarIdAndTripStartedAtBetweenOrderByTripStartedAtAsc(
            UUID carId, OffsetDateTime from, OffsetDateTime to);
}
