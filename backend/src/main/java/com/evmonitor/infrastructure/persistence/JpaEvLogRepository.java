package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaEvLogRepository extends JpaRepository<EvLogEntity, UUID> {
    Optional<EvLogEntity> findByIdAndCarId(UUID id, UUID carId);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId = :carId")
    List<EvLogEntity> findAllByCarId(@Param("carId") UUID carId);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId = :carId AND e.costEur IS NULL ORDER BY e.loggedAt DESC")
    List<EvLogEntity> findByCarIdAndCostEurIsNullOrderByLoggedAtDesc(@Param("carId") UUID carId);

    @Query("""
        SELECT e FROM EvLogEntity e
        WHERE e.carId = :carId
          AND (e.measurementType = 'AT_VEHICLE' OR e.kwhAtVehicle IS NOT NULL)
          AND e.socBeforeChargePercent IS NOT NULL
          AND e.socAfterChargePercent IS NOT NULL
          AND e.includeInStatistics = true
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findRecentAtVehicleLogsWithSoc(
            @Param("carId") UUID carId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * SoH detection candidates. Deliberately separate from
     * {@link #findRecentAtVehicleLogsWithSoc} - that one is shared with the missed-start-SoC
     * estimator, which needs logs of any SoC hub. Filtering here rather than in Java keeps
     * the payload at the window size even when qualifying charges are rare.
     */
    @Query("""
        SELECT e FROM EvLogEntity e
        WHERE e.carId = :carId
          AND (e.measurementType = 'AT_VEHICLE' OR e.kwhAtVehicle IS NOT NULL)
          AND e.socBeforeChargePercent IS NOT NULL
          AND e.socAfterChargePercent IS NOT NULL
          AND e.includeInStatistics = true
          AND (e.energySource IS NULL OR e.energySource <> 'SOC_INFERRED')
          AND (e.socAfterChargePercent - e.socBeforeChargePercent) >= :minSocHub
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findSohCandidateLogs(
            @Param("carId") UUID carId,
            @Param("minSocHub") int minSocHub,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Largest SoC hub the car ever recorded on an otherwise usable log. Drives the
     * "why is there no measurement yet" explanation in the UI.
     */
    @Query("""
        SELECT MAX(e.socAfterChargePercent - e.socBeforeChargePercent) FROM EvLogEntity e
        WHERE e.carId = :carId
          AND (e.measurementType = 'AT_VEHICLE' OR e.kwhAtVehicle IS NOT NULL)
          AND e.socBeforeChargePercent IS NOT NULL
          AND e.socAfterChargePercent IS NOT NULL
          AND e.includeInStatistics = true
          AND (e.energySource IS NULL OR e.energySource <> 'SOC_INFERRED')
        """)
    java.math.BigDecimal findLargestSocHub(@Param("carId") UUID carId);

    @Query("SELECT e.geohash, COALESCE(e.kwhAtVehicle, e.kwhCharged) FROM EvLogEntity e WHERE e.carId = :carId AND e.geohash IS NOT NULL")
    List<Object[]> findGeohashDataByCarId(@Param("carId") UUID carId);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId IN :carIds")
    List<EvLogEntity> findAllByCarIdIn(@Param("carIds") List<UUID> carIds);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId = :carId ORDER BY e.loggedAt DESC")
    List<EvLogEntity> findAllByCarIdOrderByLoggedAtDesc(@Param("carId") UUID carId, org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT e FROM EvLogEntity e
        WHERE e.carId = :carId
          AND (cast(:from as timestamp) IS NULL OR e.loggedAt >= :from)
          AND (cast(:to as timestamp) IS NULL OR e.loggedAt <= :to)
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findPagedByCarId(
            @Param("carId") UUID carId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT COUNT(e) FROM EvLogEntity e
        WHERE e.carId = :carId
          AND (cast(:from as timestamp) IS NULL OR e.loggedAt >= :from)
          AND (cast(:to as timestamp) IS NULL OR e.loggedAt <= :to)
        """)
    long countByCarIdAndDateRange(
            @Param("carId") UUID carId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT e FROM EvLogEntity e
        WHERE e.carId IN :carIds
          AND (cast(:from as timestamp) IS NULL OR e.loggedAt >= :from)
          AND (cast(:to as timestamp) IS NULL OR e.loggedAt <= :to)
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findPagedByCarIds(
            @Param("carIds") List<UUID> carIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT COUNT(e) FROM EvLogEntity e
        WHERE e.carId IN :carIds
          AND (cast(:from as timestamp) IS NULL OR e.loggedAt >= :from)
          AND (cast(:to as timestamp) IS NULL OR e.loggedAt <= :to)
        """)
    long countByCarIdsAndDateRange(
            @Param("carIds") List<UUID> carIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT e.tireType FROM EvLogEntity e
        WHERE e.carId = :carId
          AND e.tireType IS NOT NULL
          AND e.loggedAt < :before
        ORDER BY e.loggedAt DESC
        """)
    List<String> findMostRecentTireTypeBefore(
            @Param("carId") UUID carId,
            @Param("before") LocalDateTime before,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT e.routeType FROM EvLogEntity e
        WHERE e.carId = :carId
          AND e.routeType IS NOT NULL
          AND e.loggedAt < :before
        ORDER BY e.loggedAt DESC
        """)
    List<String> findMostRecentRouteTypeBefore(
            @Param("carId") UUID carId,
            @Param("before") LocalDateTime before,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE c.userId = :userId")
    List<EvLogEntity> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(e) FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE c.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    boolean existsByCarIdAndLoggedAtBetween(UUID carId, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndOdometerKmAndLoggedAtBetween(UUID carId, Integer odometerKm, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndLoggedAtAndDataSource(UUID carId, LocalDateTime loggedAt, String dataSource);

    boolean existsByCarIdAndLoggedAtAndKwhCharged(UUID carId, LocalDateTime loggedAt, BigDecimal kwhCharged);

    @Query("""
            SELECT e FROM EvLogEntity e
            WHERE e.carId = :carId
              AND e.loggedAt >= :timeMin
              AND e.loggedAt <= :timeMax
              AND (:odoKm IS NULL
                   OR e.odometerKm IS NULL
                   OR (e.odometerKm >= :odoMin AND e.odometerKm <= :odoMax))
            """)
    List<EvLogEntity> findChargeMatchCandidates(
            @Param("carId") UUID carId,
            @Param("timeMin") LocalDateTime timeMin,
            @Param("timeMax") LocalDateTime timeMax,
            @Param("odoKm") Integer odoKm,
            @Param("odoMin") Integer odoMin,
            @Param("odoMax") Integer odoMax);

    Optional<EvLogEntity> findByCarIdAndLoggedAt(UUID carId, LocalDateTime loggedAt);

    @Query("""
        SELECT e.id, e.geohash, e.loggedAt FROM EvLogEntity e
        WHERE e.geohash IS NOT NULL
          AND (e.temperatureCelsius IS NULL OR e.temperatureSource IS NULL)
        ORDER BY e.loggedAt DESC
        """)
    List<Object[]> findTemperatureCandidates(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.temperatureCelsius = :temp, e.temperatureSource = :source WHERE e.id = :id")
    void updateTemperatureAndSource(@Param("id") UUID id,
                                    @Param("temp") Double temp,
                                    @Param("source") com.evmonitor.domain.weather.TemperatureSource source);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.temperatureSource = :source WHERE e.id = :id")
    void updateTemperatureSource(@Param("id") UUID id,
                                 @Param("source") com.evmonitor.domain.weather.TemperatureSource source);

    @Query("""
        SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id
        WHERE c.userId = :userId
          AND e.geohash LIKE :geohashPrefix
          AND e.costEur IS NOT NULL
          AND e.costEur > 0
          AND (e.kwhCharged > 0 OR e.kwhAtVehicle > 0)
          AND (:chargingType IS NULL OR e.chargingType = :chargingType)
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findRecentPricedByUserIdAndGeohash(@Param("userId") UUID userId, @Param("geohashPrefix") String geohashPrefix,
            @Param("chargingType") String chargingType,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id
        WHERE c.userId = :userId
          AND e.geohash LIKE :geohashPrefix
          AND e.chargingProviderId IS NOT NULL
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findRecentWithProviderByUserIdAndGeohash(@Param("userId") UUID userId, @Param("geohashPrefix") String geohashPrefix,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id
        WHERE c.userId = :userId
          AND e.geohash = :geohash
          AND e.costEur IS NULL
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findPricelessByUserIdAndGeohash(@Param("userId") UUID userId, @Param("geohash") String geohash);

    @Query("""
            SELECT e FROM EvLogEntity e
            WHERE e.carId = :carId
              AND e.publicCharging = false
              AND e.loggedAt >= :from
              AND e.loggedAt < :to
            ORDER BY e.loggedAt ASC
            """)
    List<EvLogEntity> findHomeChargingSessionsForExport(
            @Param("carId") UUID carId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Modifying
    @Query("DELETE FROM EvLogEntity e WHERE e.carId IN (SELECT c.id FROM CarEntity c WHERE c.userId = :userId) AND e.dataSource = :dataSource")
    void deleteAllByUserIdAndDataSource(@Param("userId") UUID userId, @Param("dataSource") String dataSource);

    @Query("SELECT COUNT(e) FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE c.userId = :userId AND e.dataSource = :dataSource")
    int countByUserIdAndDataSource(@Param("userId") UUID userId, @Param("dataSource") String dataSource);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.telemetryExtras = :json WHERE e.carId = :carId AND e.loggedAt = :loggedAt")
    int updateTelemetryExtras(@Param("carId") UUID carId, @Param("loggedAt") LocalDateTime loggedAt, @Param("json") String json);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.telemetryExtras = :json WHERE e.id = :id")
    int updateTelemetryExtrasById(@Param("id") UUID id, @Param("json") String json);

    /**
     * Schreibt die Power-Curve nur, solange noch keine gesetzt ist. Garantiert
     * Immutabilitaet nach Session-Finalize - damit haelt das 7d-ETag-/Cache-Control-
     * Header im EvLogController (ETag = "pc-<id>") seine Aussage. Spaetere Reruns
     * (z.B. zweiter Telemetry-Push) werden als No-Op verworfen.
     */
    @Modifying
    @Query("UPDATE EvLogEntity e SET e.powerCurvePoints = :json WHERE e.id = :id AND e.powerCurvePoints IS NULL")
    int updatePowerCurvePoints(@Param("id") UUID id, @Param("json") String json);

    /**
     * Single-query ownership-aware lookup: returns the owner-userId + power-curve JSON
     * fuer den gegebenen Log. Ersetzt drei einzelne Roundtrips (findLog + findCar +
     * findCurve) durch eine JOIN-Query. Interface-based Projection vermeidet die
     * fehleranfaellige Optional<Object[]>-Variante.
     */
    interface OwnerCurveRow {
        UUID getOwnerUserId();
        String getPowerCurveJson();
        String getSocCurveJson();
    }

    @Query("""
            SELECT c.userId AS ownerUserId,
                   e.powerCurvePoints AS powerCurveJson,
                   e.socCurvePoints AS socCurveJson
            FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE e.id = :id
            """)
    Optional<OwnerCurveRow> findOwnerIdAndPowerCurveJson(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.socCurvePoints = :json WHERE e.id = :id AND e.socCurvePoints IS NULL")
    int updateSocCurvePoints(@Param("id") UUID id, @Param("json") String json);

    // ── Oeffentlich geteilte Ladekurven ──────────────────────────────────────

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.shareToken = :token, e.shareCreatedAt = :createdAt WHERE e.id = :id")
    int updateShareToken(@Param("id") UUID id,
                         @Param("token") String token,
                         @Param("createdAt") java.time.LocalDateTime createdAt);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.shareToken = NULL, e.shareCreatedAt = NULL WHERE e.id = :id")
    int clearShareToken(@Param("id") UUID id);

    /**
     * Oeffentlicher Lookup ueber den Share-Token. Ein JOIN statt Log- plus
     * Car-Roundtrip; der Endpunkt haengt an keiner Session und wird von
     * Crawlern in Schueben aufgerufen.
     */
    interface PublicCurveRow {
        String getPowerCurveJson();
        com.evmonitor.domain.CarBrand.CarModel getCarModel();
        java.math.BigDecimal getKwhCharged();
        java.math.BigDecimal getKwhAtVehicle();
        Integer getChargeDurationMinutes();
        java.math.BigDecimal getSocBefore();
        java.math.BigDecimal getSocAfter();
        java.math.BigDecimal getMaxChargingPowerKw();
        String getCpoName();
        Boolean getPublicCharging();
        String getChargingType();
        java.time.LocalDateTime getLoggedAt();
    }

    @Query("""
            SELECT e.powerCurvePoints AS powerCurveJson,
                   c.model AS carModel,
                   e.kwhCharged AS kwhCharged,
                   e.kwhAtVehicle AS kwhAtVehicle,
                   e.chargeDurationMinutes AS chargeDurationMinutes,
                   e.socBeforeChargePercent AS socBefore,
                   e.socAfterChargePercent AS socAfter,
                   e.maxChargingPowerKw AS maxChargingPowerKw,
                   e.cpoName AS cpoName,
                   e.publicCharging AS publicCharging,
                   e.chargingType AS chargingType,
                   e.loggedAt AS loggedAt
            FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id
            WHERE e.shareToken = :token
            """)
    Optional<PublicCurveRow> findPublicCurveByShareToken(@Param("token") String token);

    @Query("SELECT e.shareToken FROM EvLogEntity e WHERE e.id = :id")
    Optional<String> findShareToken(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM EvLogEntity e WHERE e.carId IN (SELECT c.id FROM CarEntity c WHERE c.userId = :userId) AND e.dataSource IN :dataSources")
    void deleteAllByUserIdAndDataSourceIn(@Param("userId") UUID userId, @Param("dataSources") List<String> dataSources);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.includeInStatistics = :includeInStatistics, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void updateIncludeInStatistics(@Param("id") UUID id, @Param("includeInStatistics") boolean includeInStatistics);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EvLogEntity e SET e.carId = :targetCarId WHERE e.id = :logId")
    void updateCarIdForLog(@Param("logId") UUID logId, @Param("targetCarId") UUID targetCarId);

    @Query("""
            SELECT e FROM EvLogEntity e
            WHERE e.carId = :carId
              AND e.loggedAt >= :startOfDay
              AND e.loggedAt < :endOfDay
              AND e.kwhCharged = :kwhCharged
              AND e.dataSource = :dataSource
            """)
    List<EvLogEntity> findByCarIdAndDateRangeAndKwhChargedAndDataSource(
            @Param("carId") UUID carId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            @Param("kwhCharged") BigDecimal kwhCharged,
            @Param("dataSource") String dataSource);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EvLogEntity e SET e.rawImportData = :rawJson WHERE e.id = :id")
    void updateRawImportData(@Param("id") UUID id, @Param("rawJson") String rawJson);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE ev_log SET route_type = :routeType WHERE id = :id", nativeQuery = true)
    void updateRouteType(@Param("id") UUID id, @Param("routeType") String routeType);

    /** Tesla-eigene Quellen - nur diese Logs darf die Billing-Anreicherung anfassen. */
    List<String> TESLA_DATA_SOURCES = List.of("TESLA_LIVE", "TESLA_FLEET_IMPORT");

    /**
     * Findet Tesla-Ladevorgaenge ohne Kosten, die aus Teslas Billing-API stammen koennten.
     *
     * <p>Bewusst NICHT nach cpoName gefiltert: der Marker 'Tesla Supercharger' entsteht nur, wenn
     * die Telemetrie beim Sessionende einen FastChargerType kennt, und fehlt daher im gesamten
     * Altbestand. Ob eine Ladung ein Supercharger war, weiss ohnehin nur Teslas Billing-API - der
     * Aufrufer gleicht diese Kandidaten dort gegen und markiert die Treffer.
     *
     * <p>Eingegrenzt wird stattdessen auf das, was ueberhaupt in Frage kommt: Ladungen des Users
     * aus einer Tesla-Quelle, ohne Kosten, oeffentlich oder per DC geladen. Heimladen faellt damit
     * heraus - fuer Supercharger gilt beides immer.
     */
    @Query("""
            SELECT e FROM EvLogEntity e, CarEntity c
            WHERE e.carId = c.id
              AND c.userId = :userId
              AND e.costEur IS NULL
              AND e.dataSource IN :dataSources
              AND (e.publicCharging = true OR e.chargingType = 'DC')
              AND e.loggedAt >= :cutoff
            ORDER BY e.loggedAt DESC
            """)
    List<EvLogEntity> findEnrichableTeslaLogs(
            @Param("userId") UUID userId,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("dataSources") List<String> dataSources);

    /**
     * Defense-in-Depth: schreibt nur auf Logs aus einer Tesla-Quelle, die noch keine Kosten tragen.
     * Ein fehlerhafter connectors-service oder ein kompromittiertes X-Internal-Token kann darueber
     * also weder fremde Quellen (Wallbox, manuelle Eintraege) noch bereits bepreiste Ladungen
     * ueberschreiben. Eine falsche id ist ein stiller No-Op statt eines Datenverlusts.
     *
     * <p>Frueher hing die Bedingung an cpoName='Tesla Supercharger'. Das schuetzte nicht besser -
     * cpoName ist ein Anzeigefeld, das der Nutzer aendern kann - schloss aber den gesamten
     * Altbestand ohne Marker dauerhaft von der Anreicherung aus.
     *
     * <p>Zusaetzlich muss das Log dem User gehoeren, in dessen Namen angereichert wird. Der
     * connectors-service kennt die Zuordnung Verbindung -> User aus seiner eigenen Datenhaltung;
     * die Pruefung hier stellt sicher, dass eine dort fehlerhafte Zuordnung nicht in fremde Konten
     * schreibt.
     *
     * @return 1 wenn geschrieben wurde, 0 wenn das Log bereits Kosten trug, nicht von Tesla stammt
     *         oder einem anderen User gehoert
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE EvLogEntity e
               SET e.costEur = :costEur,
                   e.cpoName = COALESCE(:cpoName, e.cpoName),
                   e.updatedAt = CURRENT_TIMESTAMP
             WHERE e.id = :id
               AND e.costEur IS NULL
               AND e.dataSource IN :dataSources
               AND e.carId IN (SELECT c.id FROM CarEntity c WHERE c.userId = :userId)
            """)
    int enrichWithTeslaPricing(@Param("id") UUID id,
                               @Param("userId") UUID userId,
                               @Param("costEur") BigDecimal costEur,
                               @Param("cpoName") String cpoName,
                               @Param("dataSources") List<String> dataSources);

    /**
     * Aggregated basic stats for a car model.
     * Returns: [logCount, uniqueContributors, avgCostPerKwh, avgKwhPerSession]
     * Demo Mode: If isSeedUser=true, includes ALL seed data (from all seed users), not just current user.
     * Uses native SQL because JPQL doesn't support dividing two columns directly in aggregation.
     */
    @Query(value = """
            WITH ranked AS (
                SELECT
                    odometer_km,
                    COALESCE(kwh_at_vehicle, kwh_charged) AS kwh,
                    soc_after_charge_percent,
                    LAG(odometer_km)              OVER (PARTITION BY car_id ORDER BY logged_at) AS prev_odometer,
                    LAG(soc_after_charge_percent) OVER (PARTITION BY car_id ORDER BY logged_at) AS prev_soc
                FROM ev_log
                WHERE include_in_statistics = true
            )
            SELECT COUNT(*) FROM ranked
            WHERE odometer_km IS NOT NULL
              AND kwh IS NOT NULL
              AND soc_after_charge_percent IS NOT NULL
              AND prev_odometer IS NOT NULL
              AND prev_soc IS NOT NULL
              AND (odometer_km - prev_odometer) >= 20
            """, nativeQuery = true)
    long countValidTrips();

    @Query(value = """
            WITH filtered AS (
                SELECT l.id,
                       COALESCE(l.kwh_charged,
                           l.kwh_at_vehicle / CASE
                               WHEN l.charging_type = 'DC'  THEN 0.95
                               WHEN l.charging_type = 'AC'  THEN 0.90
                               WHEN l.max_charging_power_kw > 22 THEN 0.95
                               WHEN l.max_charging_power_kw IS NOT NULL THEN 0.90
                               WHEN l.charge_duration_minutes > 0
                                    AND l.kwh_at_vehicle / (l.charge_duration_minutes / 60.0) > 22 THEN 0.95
                               WHEN l.charge_duration_minutes > 0 THEN 0.90
                               WHEN l.is_public_charging = true THEN 0.95
                               ELSE 0.90
                           END
                       ) AS kwh_for_cost,
                       l.cost_eur,
                       c.user_id,
                       l.car_id,
                       l.odometer_km,
                       LAG(l.odometer_km) OVER (PARTITION BY l.car_id ORDER BY l.logged_at) AS prev_odometer
                FROM ev_log l
                JOIN car c ON c.id = l.car_id
                WHERE c.model = :model
                  AND (l.include_in_statistics = true
                       OR (:isSeedUser = true
                           AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            )
            SELECT
                COUNT(id) FILTER (WHERE prev_odometer IS NULL OR odometer_km IS NULL OR odometer_km != prev_odometer) AS log_count,
                COUNT(DISTINCT user_id)                                                                                AS unique_contributors,
                AVG(CASE WHEN cost_eur > 0 THEN cost_eur / NULLIF(kwh_for_cost, 0) END)                              AS avg_cost_per_kwh,
                AVG(kwh_for_cost) FILTER (WHERE prev_odometer IS NULL OR odometer_km IS NULL OR odometer_km != prev_odometer) AS avg_kwh_per_session,
                COUNT(DISTINCT car_id)                                                                                 AS unique_cars
            FROM filtered
            """, nativeQuery = true)
    Object[] findPublicBasicStatsByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

    /**
     * Community-wide average charging price (EUR/kWh) split by private vs public charging.
     * Same kwh-for-cost estimation and statistics filter as the per-model stats, but across all
     * models - used as the normalized reference prices for the model comparison slider.
     * Returns [home_price, public_price, home_count, public_count].
     */
    @Query(value = """
            WITH filtered AS (
                SELECT COALESCE(l.kwh_charged,
                           l.kwh_at_vehicle / CASE
                               WHEN l.charging_type = 'DC'  THEN 0.95
                               WHEN l.charging_type = 'AC'  THEN 0.90
                               WHEN l.max_charging_power_kw > 22 THEN 0.95
                               WHEN l.max_charging_power_kw IS NOT NULL THEN 0.90
                               WHEN l.charge_duration_minutes > 0
                                    AND l.kwh_at_vehicle / (l.charge_duration_minutes / 60.0) > 22 THEN 0.95
                               WHEN l.charge_duration_minutes > 0 THEN 0.90
                               WHEN l.is_public_charging = true THEN 0.95
                               ELSE 0.90
                           END
                       ) AS kwh_for_cost,
                       l.cost_eur,
                       l.is_public_charging
                FROM ev_log l
                JOIN car c ON c.id = l.car_id
                WHERE l.cost_eur > 0
                  AND (l.include_in_statistics = true
                       OR (:isSeedUser = true
                           AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            )
            SELECT
                AVG(cost_eur / NULLIF(kwh_for_cost, 0)) FILTER (WHERE is_public_charging = false) AS home_price,
                AVG(cost_eur / NULLIF(kwh_for_cost, 0)) FILTER (WHERE is_public_charging = true)  AS public_price,
                COUNT(*) FILTER (WHERE is_public_charging = false AND cost_eur / NULLIF(kwh_for_cost, 0) IS NOT NULL) AS home_count,
                COUNT(*) FILTER (WHERE is_public_charging = true  AND cost_eur / NULLIF(kwh_for_cost, 0) IS NOT NULL) AS public_count
            FROM filtered
            """, nativeQuery = true)
    Object[] findCommunityChargingPrices(@Param("isSeedUser") boolean isSeedUser);

    @Query(value = """
            SELECT c.manufacture_year, COUNT(DISTINCT l.car_id) AS car_count
            FROM ev_log l
            JOIN car c ON c.id = l.car_id
            WHERE c.model = :model
              AND c.manufacture_year IS NOT NULL
              AND (l.include_in_statistics = true
                   OR (:isSeedUser = true
                       AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            GROUP BY c.manufacture_year
            ORDER BY c.manufacture_year
            """, nativeQuery = true)
    List<Object[]> findYearDistributionByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

    @Query(value = """
            SELECT COALESCE(l.route_type, 'UNKNOWN') AS route_type, COUNT(*) AS cnt
            FROM ev_log l JOIN car c ON c.id = l.car_id
            WHERE c.model = :model
              AND (l.include_in_statistics = true OR (:isSeedUser = true
                   AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            GROUP BY l.route_type
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> findRouteTypeDistributionByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

    /**
     * Average DC charging power (kW) for a model, calculated from real DC fast-charging sessions.
     * Uses energy-weighted approach: total kWh / total hours charging.
     * Only includes sessions with charging_type = 'DC' and at least 5 sessions to filter outliers.
     * Returns null if fewer than 5 qualifying DC sessions exist.
     */
    @Query(value = """
            SELECT
                CASE WHEN COUNT(*) >= 5
                     THEN SUM(COALESCE(kwh_at_vehicle, kwh_charged)) / NULLIF(SUM(charge_duration_minutes) / 60.0, 0)
                     ELSE NULL
                END
            FROM ev_log l
            JOIN car c ON c.id = l.car_id
            WHERE c.model = :model
              AND l.charge_duration_minutes > 0
              AND COALESCE(l.kwh_at_vehicle, l.kwh_charged) > 0
              AND l.charging_type = 'DC'
              AND (l.include_in_statistics = true
                   OR (:isSeedUser = true
                       AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            """, nativeQuery = true)
    BigDecimal findAvgDcChargingPowerKwByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

    /**
     * Returns AC and DC average cost per kWh for a model.
     * Only included if at least 5 sessions with cost data exist per type.
     * Returns: [acAvgCostPerKwh, acCount, dcAvgCostPerKwh, dcCount]
     */
    @Query(value = """
            SELECT
                CASE WHEN COUNT(*) FILTER (WHERE l.charging_type = 'AC' AND l.cost_eur > 0 AND COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.90) > 0) >= 5
                     THEN AVG(l.cost_eur / COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.90)) FILTER (WHERE l.charging_type = 'AC' AND l.cost_eur > 0 AND COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.90) > 0)
                     ELSE NULL END AS ac_avg_cost,
                COUNT(*) FILTER (WHERE l.charging_type = 'AC' AND l.cost_eur > 0 AND COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.90) > 0) AS ac_count,
                CASE WHEN COUNT(*) FILTER (WHERE l.charging_type = 'DC' AND l.cost_eur > 0 AND COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.95) > 0) >= 5
                     THEN AVG(l.cost_eur / COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.95)) FILTER (WHERE l.charging_type = 'DC' AND l.cost_eur > 0 AND COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.95) > 0)
                     ELSE NULL END AS dc_avg_cost,
                COUNT(*) FILTER (WHERE l.charging_type = 'DC' AND l.cost_eur > 0 AND COALESCE(l.kwh_charged, l.kwh_at_vehicle / 0.95) > 0) AS dc_count
            FROM ev_log l
            JOIN car c ON c.id = l.car_id
            WHERE c.model = :model
              AND (l.include_in_statistics = true
                   OR (:isSeedUser = true
                       AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            """, nativeQuery = true)
    Object[] findAcDcCostStatsByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

}
