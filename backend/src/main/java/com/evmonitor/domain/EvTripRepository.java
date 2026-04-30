package com.evmonitor.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvTripRepository extends JpaRepository<EvTrip, UUID> {

    Optional<EvTrip> findByExternalIdAndDeletedAtIsNull(UUID externalId);

    @Query("SELECT t FROM EvTrip t WHERE t.carId = :carId AND t.tripStartedAt BETWEEN :from AND :to AND t.deletedAt IS NULL ORDER BY t.tripStartedAt ASC")
    List<EvTrip> findByCarIdAndTripStartedAtBetweenOrderByTripStartedAtAsc(
            @Param("carId") UUID carId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    List<EvTrip> findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(UUID userId, UUID carId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE EvTrip t SET t.outsideTempCelsius = :temp WHERE t.id = :id")
    void updateTemperature(@Param("id") UUID id, @Param("temp") BigDecimal temp);

    @Query("SELECT t FROM EvTrip t WHERE t.locationStartGeohash IS NOT NULL AND t.outsideTempCelsius IS NULL AND t.deletedAt IS NULL")
    List<EvTrip> findAllWithGeohashAndNoTemperature();
}
