package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvLogRepository {
    EvLog save(EvLog evLog);

    Optional<EvLog> findById(UUID id);

    Optional<EvLog> findByIdAndCarId(UUID id, UUID carId);

    List<EvLog> findAll();

    List<EvLog> findAllByCarId(UUID carId);

    List<EvLog> findAllByCarIds(List<UUID> carIds);

    List<EvLog> findLatestByCarId(UUID carId, int limit, int page);

    List<EvLog> findAllByUserId(UUID userId);

    boolean existsByCarIdAndLoggedAtBetween(UUID carId, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndOdometerKmAndLoggedAtBetween(UUID carId, Integer odometerKm, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndLoggedAtAndDataSource(UUID carId, LocalDateTime loggedAt, DataSource dataSource);

    boolean existsByCarIdAndLoggedAtAndKwhCharged(UUID carId, LocalDateTime loggedAt, BigDecimal kwhCharged);

    long countByUserId(UUID userId);

    void deleteById(UUID id);

    void deleteAllByUserIdAndDataSource(UUID userId, DataSource dataSource);

    void deleteAllByUserIdAndDataSourceIn(UUID userId, List<DataSource> dataSources);

    boolean updateGeohash(UUID carId, LocalDateTime loggedAt, String geohash);

    List<EvLog> findAllWithGeohashAndNoTemperature();

    void updateTemperature(UUID id, Double temperatureCelsius);

    List<EvLog> findImportLogsInTimeWindow(UUID carId, LocalDateTime from, LocalDateTime to,
                                            BigDecimal kwhMin, BigDecimal kwhMax);

    List<EvLog> findUserLoggedInTimeWindow(UUID carId, LocalDateTime from, LocalDateTime to,
                                           BigDecimal kwhMin, BigDecimal kwhMax);

    void markAsSuperseded(UUID id, UUID supersededById);

    void clearSupersededByReferences(UUID supersededById);

    List<GeohashPoint> findGeohashDataByCarId(UUID carId);

    /**
     * Setzt die session_group_id für einen einzelnen Log-Eintrag (Sub-Session Linking).
     */
    void setSessionGroupId(UUID logId, UUID groupId);

    /**
     * Gibt alle Logs zurück, die zu einer bestimmten Gruppe gehören (Sub-Sessions).
     */
    List<EvLog> findAllBySessionGroupId(UUID groupId);

    /**
     * Gibt alle Logs zurück, die KEINE Sub-Session sind (session_group_id IS NULL).
     * Für Dashboard-Anzeige: Sub-Sessions werden durch Gruppen-Einträge ersetzt.
     */
    List<EvLog> findAllByCarIdExcludingSubSessions(UUID carId);

    Optional<BigDecimal> findMostRecentCostPerKwhByUserIdAndGeohash(UUID userId, String geohash);

    void updateCarIdBySessionGroupId(UUID groupId, UUID targetCarId);

    void updateCarIdForLog(UUID logId, UUID targetCarId);
}
