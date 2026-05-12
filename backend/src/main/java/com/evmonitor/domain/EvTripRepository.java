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

    @Query("SELECT t FROM EvTrip t WHERE t.userId = :userId AND t.carId = :carId "
            + "AND t.deletedAt IS NULL AND t.dataSource NOT IN :excludedSources "
            + "ORDER BY t.tripEndedAt DESC")
    List<EvTrip> findByUserIdAndCarIdExcludingSourcesAndDeletedAtIsNull(
            @Param("userId") UUID userId,
            @Param("carId") UUID carId,
            @Param("excludedSources") java.util.Collection<String> excludedSources,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE EvTrip t SET t.outsideTempCelsius = :temp WHERE t.id = :id")
    void updateTemperature(@Param("id") UUID id, @Param("temp") BigDecimal temp);

    @Query("SELECT t FROM EvTrip t WHERE t.locationStartGeohash IS NOT NULL AND t.outsideTempCelsius IS NULL AND t.deletedAt IS NULL")
    List<EvTrip> findAllWithGeohashAndNoTemperature();

    /** Soft-delete all not-yet-deleted trips of user with given data_source. Returns affected row count. */
    @Modifying
    @Transactional
    @Query("UPDATE EvTrip t SET t.deletedAt = :now "
            + "WHERE t.userId = :userId AND t.dataSource = :dataSource AND t.deletedAt IS NULL")
    int softDeleteByUserIdAndDataSourceAt(@Param("userId") UUID userId, @Param("dataSource") String dataSource,
                                           @Param("now") OffsetDateTime now);

    default int softDeleteByUserIdAndDataSource(UUID userId, String dataSource) {
        return softDeleteByUserIdAndDataSourceAt(userId, dataSource, OffsetDateTime.now());
    }
}
