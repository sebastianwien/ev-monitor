package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class EvLog {

    private final UUID id;
    private final UUID carId;
    private final BigDecimal kwhCharged;
    private final BigDecimal costEur;
    private final Integer chargeDurationMinutes;
    private final String geohash; // 5-char geohash (~5km) for private charging, 7-char (~150m) for public chargers
    private final Integer odometerKm; // Optional: odometer reading in km
    private final BigDecimal maxChargingPowerKw; // Optional: max charging power in kW
    private final Integer socAfterChargePercent; // Optional: State of Charge after charging (0-100%)
    private final Integer socBeforeChargePercent; // Optional: State of Charge before charging (0-100%)
    private final LocalDateTime loggedAt; // When the charge happened (user-provided or now)
    private final DataSource dataSource;
    private final boolean includeInStatistics; // Whether to include in public stats/aggregations
    private final Integer odometerSuggestionMinKm; // Optional: km estimate min from wallbox service
    private final Integer odometerSuggestionMaxKm; // Optional: km estimate max from wallbox service
    private final Double temperatureCelsius; // Optional: ambient temperature at charging time/location (Open-Meteo)
    private final ChargingType chargingType; // Optional: AC or DC charging
    private final String rawImportData;      // Optional: raw JSON data from import source (e.g. SpritMonitor fueling)
    private final RouteType routeType;       // Optional: CITY, COMBINED, or HIGHWAY
    private final TireType tireType;         // Optional: SUMMER, ALL_YEAR, or WINTER
    private final UUID supersededBy;         // Optional: ID of the USER_LOGGED log that supersedes this import
    private final UUID sessionGroupId;       // Optional: ID of the charging_session_group (sub-sessions only)
    private final boolean isPublicCharging;  // Whether this was at a public charger (CPO)
    private final String cpoName;            // Optional: CPO name (e.g. IONITY, EnBW) — only when isPublicCharging
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EvLog(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, DataSource dataSource,
            boolean includeInStatistics, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            Double temperatureCelsius, ChargingType chargingType, String rawImportData,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            RouteType routeType, TireType tireType, UUID supersededBy, UUID sessionGroupId,
            boolean isPublicCharging, String cpoName) {
        this.id = id;
        this.carId = carId;
        this.kwhCharged = kwhCharged;
        this.costEur = costEur;
        this.chargeDurationMinutes = chargeDurationMinutes;
        this.geohash = geohash;
        this.odometerKm = odometerKm;
        this.maxChargingPowerKw = maxChargingPowerKw;
        this.socAfterChargePercent = socAfterChargePercent;
        this.socBeforeChargePercent = socBeforeChargePercent;
        this.loggedAt = loggedAt != null ? loggedAt : LocalDateTime.now();
        this.dataSource = dataSource != null ? dataSource : DataSource.USER_LOGGED;
        this.includeInStatistics = includeInStatistics;
        this.odometerSuggestionMinKm = odometerSuggestionMinKm;
        this.odometerSuggestionMaxKm = odometerSuggestionMaxKm;
        this.temperatureCelsius = temperatureCelsius;
        this.chargingType = chargingType != null ? chargingType : ChargingType.UNKNOWN;
        this.rawImportData = rawImportData;
        this.routeType = routeType;
        this.tireType = tireType;
        this.supersededBy = supersededBy;
        this.sessionGroupId = sessionGroupId;
        this.isPublicCharging = isPublicCharging;
        this.cpoName = cpoName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Backward-compatible overload for existing callers (tests, imports)
    public static EvLog createNew(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt,
            ChargingType chargingType, RouteType routeType, TireType tireType) {
        return createNew(carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, loggedAt, chargingType, routeType, tireType,
                false, null);
    }

    public static EvLog createNew(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt,
            ChargingType chargingType, RouteType routeType, TireType tireType,
            boolean isPublicCharging, String cpoName) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, socAfterChargePercent, null, loggedAt,
                DataSource.USER_LOGGED, true, null, null, null, chargingType, null, now, now, routeType, tireType, null, null,
                isPublicCharging, cpoName);
    }

    public static EvLog createNewWithSource(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt,
            DataSource dataSource, ChargingType chargingType, String rawImportData) {
        LocalDateTime now = LocalDateTime.now();
        boolean includeInStats = dataSource.includeInStatistics();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, socAfterChargePercent, null, loggedAt,
                dataSource, includeInStats, null, null, null, chargingType, rawImportData, now, now, null, null, null, null,
                false, null);
    }

    public static EvLog createNewWithSourceAndSocBefore(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, DataSource dataSource, ChargingType chargingType,
            Double temperatureCelsius, String rawImportData) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, socAfterChargePercent,
                socBeforeChargePercent, loggedAt, dataSource, dataSource.includeInStatistics(),
                null, null, temperatureCelsius, chargingType, rawImportData, now, now, null, null, null, null,
                false, null);
    }

    public static EvLog createFromOcpp(UUID carId, BigDecimal kwhCharged,
            Integer chargeDurationMinutes, String geohash,
            LocalDateTime loggedAt, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm) {
        return createFromInternal(carId, kwhCharged, chargeDurationMinutes, geohash,
                loggedAt, odometerSuggestionMinKm, odometerSuggestionMaxKm, DataSource.WALLBOX_OCPP, null);
    }

    public static EvLog createFromInternal(UUID carId, BigDecimal kwhCharged,
            Integer chargeDurationMinutes, String geohash,
            LocalDateTime loggedAt, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            DataSource dataSource, BigDecimal costEur) {
        return createFromInternal(carId, kwhCharged, chargeDurationMinutes, geohash,
                loggedAt, odometerSuggestionMinKm, odometerSuggestionMaxKm, dataSource, costEur, null);
    }

    public static EvLog createFromInternal(UUID carId, BigDecimal kwhCharged,
            Integer chargeDurationMinutes, String geohash,
            LocalDateTime loggedAt, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            DataSource dataSource, BigDecimal costEur, ChargingType chargingType) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, null, null, null, null, loggedAt,
                dataSource, dataSource.includeInStatistics(),
                odometerSuggestionMinKm, odometerSuggestionMaxKm, null, chargingType, null, now, now, null, null, null, null,
                false, null);
    }

    public static EvLog createFromPublicApi(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType, RouteType routeType, TireType tireType) {
        return createFromPublicApi(carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent, loggedAt,
                chargingType, routeType, tireType, DataSource.API_UPLOAD);
    }

    public static EvLog createFromPublicApi(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType, RouteType routeType, TireType tireType,
            DataSource dataSource) {
        return createFromPublicApi(carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent, loggedAt,
                chargingType, routeType, tireType, dataSource, null);
    }

    public static EvLog createFromPublicApi(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType, RouteType routeType, TireType tireType,
            DataSource dataSource, String rawImportData) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw,
                socAfterChargePercent, socBeforeChargePercent, loggedAt,
                dataSource, dataSource.includeInStatistics(),
                null, null, null, chargingType, rawImportData, now, now, routeType, tireType, null, null,
                false, null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCarId() {
        return carId;
    }

    public BigDecimal getKwhCharged() {
        return kwhCharged;
    }

    public BigDecimal getCostEur() {
        return costEur;
    }

    public Integer getChargeDurationMinutes() {
        return chargeDurationMinutes;
    }

    public String getGeohash() {
        return geohash;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Integer getOdometerKm() {
        return odometerKm;
    }

    public BigDecimal getMaxChargingPowerKw() {
        return maxChargingPowerKw;
    }

    public Integer getSocAfterChargePercent() {
        return socAfterChargePercent;
    }

    public Integer getSocBeforeChargePercent() {
        return socBeforeChargePercent;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public boolean isIncludeInStatistics() {
        return includeInStatistics;
    }

    public Integer getOdometerSuggestionMinKm() {
        return odometerSuggestionMinKm;
    }

    public Integer getOdometerSuggestionMaxKm() {
        return odometerSuggestionMaxKm;
    }

    public Double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public ChargingType getChargingType() {
        return chargingType;
    }

    public String getRawImportData() {
        return rawImportData;
    }

    public RouteType getRouteType() {
        return routeType;
    }

    public TireType getTireType() {
        return tireType;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public UUID getSessionGroupId() {
        return sessionGroupId;
    }

    public boolean isPublicCharging() {
        return isPublicCharging;
    }

    public String getCpoName() {
        return cpoName;
    }

    public EvLog withIncludeInStatistics(boolean includeInStatistics) {
        return new EvLog(id, carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent, loggedAt,
                dataSource, includeInStatistics, odometerSuggestionMinKm, odometerSuggestionMaxKm,
                temperatureCelsius, chargingType, rawImportData, createdAt, LocalDateTime.now(),
                routeType, tireType, supersededBy, sessionGroupId, isPublicCharging, cpoName);
    }

    /**
     * A log is complete when all fields required to act as logY in a consumption
     * calculation are present: odometer, kwhCharged, and socAfterChargePercent.
     */
    public boolean isComplete() {
        return odometerKm != null && kwhCharged != null && socAfterChargePercent != null;
    }

    /**
     * A log can act as logX (the trip starting point) when odometer and
     * socAfterChargePercent are present. kwhCharged is not required for this role.
     */
    public boolean canBeUsedAsLogX() {
        return odometerKm != null && socAfterChargePercent != null;
    }

    /**
     * True if kwhCharged is present — the minimum required for any energy accounting.
     */
    public boolean hasKwhCharged() {
        return kwhCharged != null;
    }

    /**
     * True if this log falls within the given date range (both bounds inclusive, both nullable).
     * A null startDate means no lower bound; a null endDate means no upper bound.
     */
    public boolean isLoggedWithin(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        java.time.LocalDate logDate = loggedAt.toLocalDate();
        boolean afterStart = startDate == null || !logDate.isBefore(startDate);
        boolean beforeEnd = endDate == null || !logDate.isAfter(endDate);
        return afterStart && beforeEnd;
    }
}
