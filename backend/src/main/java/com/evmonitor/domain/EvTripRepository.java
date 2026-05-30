package com.evmonitor.domain;

import org.springframework.data.domain.Page;
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

    @Query("""
            SELECT t FROM EvTrip t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND (:carId IS NULL OR t.carId = :carId)
              AND (:from IS NULL OR t.tripStartedAt >= :from)
              AND (:to IS NULL OR t.tripStartedAt <= :to)
            ORDER BY t.tripStartedAt DESC
            """)
    Page<EvTrip> findByUserIdAndFilters(
            @Param("userId") UUID userId,
            @Param("carId") UUID carId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    /**
     * Hard-delete aller Trips eines Users mit gegebener data_source. Liefert die geloeschte
     * Zeilenzahl. Genutzt fuer Bulk-Import-Resets (z.B. "Alle XPENG-Imports loeschen"). Soft-Delete
     * waere hier schaedlich: der UNIQUE-Index auf external_id ignoriert deleted_at, sodass ein
     * Re-Import desselben Trips an einem Constraint-Violation scheitert. Daher hart loeschen.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EvTrip t WHERE t.userId = :userId AND t.dataSource = :dataSource")
    int deleteAllByUserIdAndDataSource(@Param("userId") UUID userId, @Param("dataSource") String dataSource);
}
