package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.evmonitor.domain.weather.TemperatureSource;

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

    List<EvLog> findRecentAtVehicleLogsWithSoc(UUID carId, int limit);

    /** Most recent logs usable for SoH detection, already filtered by minimum SoC hub. */
    List<EvLog> findSohCandidateLogs(UUID carId, int minSocHub, int limit);

    /** Largest SoC hub on any usable log, or null if the car has none. */
    java.math.BigDecimal findLargestSocHub(UUID carId);

    List<EvLog> findAllByCarIds(List<UUID> carIds);

    List<EvLog> findLatestByCarId(UUID carId, int limit, int page);

    List<EvLog> findPagedByCarId(UUID carId, LocalDateTime from, LocalDateTime to, int size, int offset);

    long countByCarIdAndDateRange(UUID carId, LocalDateTime from, LocalDateTime to);

    List<EvLog> findPagedByCarIds(List<UUID> carIds, LocalDateTime from, LocalDateTime to, int size, int offset);

    long countByCarIdsAndDateRange(List<UUID> carIds, LocalDateTime from, LocalDateTime to);

    List<EvLog> findAllByUserId(UUID userId);

    boolean existsByCarIdAndLoggedAtBetween(UUID carId, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndOdometerKmAndLoggedAtBetween(UUID carId, Integer odometerKm, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndLoggedAtAndDataSource(UUID carId, LocalDateTime loggedAt, DataSource dataSource);

    boolean existsByCarIdAndLoggedAtAndKwhCharged(UUID carId, LocalDateTime loggedAt, BigDecimal kwhCharged);

    /**
     * Findet existierende Logs, die durch XPeng-Telemetrie angereichert werden koennten.
     *
     * Reine Filter-Query: Log gehoert zum Auto, loggedAt liegt im Zeitfenster,
     * Odometer entweder NULL oder in [odoMin, odoMax]. Tie-Breaking + Skip-Logik
     * geschieht im Service ({@link com.evmonitor.application.imports.xpeng.XpengChargeMatcher}).
     *
     * Wird mit {@code odoMin == null && odoMax == null} aufgerufen, ist der Odo-Filter aus -
     * dann zaehlen alle Logs im Zeitfenster (Session ohne Odometer).
     */
    List<EvLog> findChargeMatchCandidates(UUID carId, Integer odoMin, Integer odoMax,
                                           LocalDateTime timeMin, LocalDateTime timeMax);

    long countByUserId(UUID userId);

    void deleteById(UUID id);

    void deleteAllByUserIdAndDataSource(UUID userId, DataSource dataSource);

    void deleteAllByUserIdAndDataSourceIn(UUID userId, List<DataSource> dataSources);

    int countByUserIdAndDataSource(UUID userId, DataSource dataSource);

    /** Set the JSON-blob telemetry_extras on the unique (carId, loggedAt) entry, if it exists. */
    void updateTelemetryExtras(UUID carId, LocalDateTime loggedAt, String telemetryExtrasJson);

    /** Set the JSON-blob telemetry_extras on a known log id. Preferred over the (carId, loggedAt)
     *  variant when the caller already has the id - vermeidet Mismatches bei nicht-eindeutigem
     *  Composite-Key (z.B. wenn loggedAt nachtraeglich angepasst wird). */
    void updateTelemetryExtrasById(UUID logId, String telemetryExtrasJson);

    /** Persist the downsampled power-curve JSON for the given log id. No-op if id not found. */
    void updatePowerCurvePoints(UUID id, String powerCurvePointsJson);

    /** Returns the raw JSON-Array string of the power-curve, or null if the log has no curve. */
    Optional<String> findPowerCurvePointsJson(UUID id);

    /**
     * Single-query ownership-aware lookup. Returns the owner userId + curve-JSON
     * for the log in one Postgres round-trip.
     */
    Optional<PowerCurveLookup> findOwnerIdAndPowerCurveJson(UUID logId);

    record PowerCurveLookup(UUID ownerUserId, String powerCurvePointsJson) {}

    Optional<EvLog> updateGeohash(UUID carId, LocalDateTime loggedAt, String geohash);

    /**
     * Arbeitsvorrat des Temperatur-Backfills: Logs mit Ort, deren Temperatur fehlt ODER deren
     * Herkunft unbekannt ist. Neueste zuerst - bricht ein Lauf ab, sind die relevanten Daten
     * bereits korrigiert. Der Job schreibt jede fertige Zeile sofort, der Vorrat schrumpft also
     * mit jedem Lauf und ist irgendwann leer.
     */
    List<TemperatureCandidate> findTemperatureCandidates(int limit);

    /** Ein Log ohne belastbare Temperatur-Herkunft. */
    record TemperatureCandidate(UUID id, String geohash, LocalDateTime at) {}

    void updateTemperature(UUID id, Double temperatureCelsius, TemperatureSource source);

    /** Haelt nur die Herkunft fest, ohne den Wert anzufassen. */
    void updateTemperatureSource(UUID id, TemperatureSource source);

    /**
     * Sets temperature on the log identified by (carId, loggedAt) only if it has none yet.
     * Returns {@code true} when a log was updated, {@code false} when no match or already set.
     * Used by the connector temperature-backfill to fill measured OutsideTemp without
     * overwriting an existing value.
     */
    boolean updateTemperatureIfAbsent(UUID carId, LocalDateTime loggedAt, Double temperatureCelsius);

    List<GeohashPoint> findGeohashDataByCarId(UUID carId);


    /**
     * The most recent log at this location that carries a price. It is the anchor for both the
     * cost and - when it has one - the charging card of a new log at the same place.
     */
    Optional<EvLog> findMostRecentPricedLogAtGeohash(UUID userId, String geohash);

    Optional<UUID> findMostRecentChargingProviderAtGeohash(UUID userId, String geohash);

    /**
     * All logs of this user at exactly this geohash that still have no cost.
     *
     * Exact match, not the 6-char prefix used by {@link #findMostRecentChargingProviderAtGeohash}:
     * public chargers are stored with 7 chars and private ones with 6, so an exact match keeps a
     * public tariff from ever landing on a home-charging log ~600m away.
     */
    List<EvLog> findPricelessLogsAtGeohash(UUID userId, String geohash);

    /**
     * Most recent {@link TireType} set on any log of this car with {@code logged_at < before}.
     * Used by auto-log paths (Tesla/Wallbox/SmartCar) to inherit the user's last known tire setting
     * instead of writing {@code NULL} into every auto-created log.
     */
    Optional<TireType> findMostRecentTireTypeBefore(UUID carId, LocalDateTime before);

    /**
     * Most recent {@link RouteType} set on any log of this car with {@code logged_at < before}.
     * Same rationale as {@link #findMostRecentTireTypeBefore}.
     */
    Optional<RouteType> findMostRecentRouteTypeBefore(UUID carId, LocalDateTime before);

    void updateCarIdForLog(UUID logId, UUID targetCarId);

    List<EvLog> findByCarIdAndDateAndKwhChargedAndDataSource(UUID carId, LocalDate date, BigDecimal kwhCharged, DataSource dataSource);

    void updateRawImportData(UUID id, String rawJson);

    void updateRouteType(UUID id, RouteType routeType);

    /**
     * Tesla Supercharger sessions submitted via Telemetry that still lack {@code cost_eur}
     * (Tesla billing data hasn't been merged yet) and are within the enrichment cutoff.
     * Used by the daily enrichment job in connectors-service.
     */
    List<EvLog> findPendingTeslaSuperchargerEnrichment(UUID userId, LocalDateTime cutoff);

    /**
     * Enriches a pending Tesla-SuC log with billing data. Returns the number of affected rows
     * (0 = already enriched or wrong id - idempotent). Implementation must filter by
     * cpoName='Tesla Supercharger' AND costEur IS NULL to prevent collateral updates.
     */
    int enrichWithTeslaPricing(UUID id, BigDecimal costEur, String cpoName);
}
