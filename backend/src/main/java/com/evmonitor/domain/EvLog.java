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
    private final String geohash; // 6-char geohash (~600m) for private charging, 7-char (~150m) for public chargers
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
    private final EnergyMeasurementType measurementType; // At which point energy is measured (derived from dataSource)
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    // Backward-compatible constructor - derives measurementType from dataSource
    public EvLog(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, DataSource dataSource,
            boolean includeInStatistics, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            Double temperatureCelsius, ChargingType chargingType, String rawImportData,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            RouteType routeType, TireType tireType, UUID supersededBy, UUID sessionGroupId,
            boolean isPublicCharging, String cpoName) {
        this(id, carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent, loggedAt,
                dataSource, includeInStatistics, odometerSuggestionMinKm, odometerSuggestionMaxKm,
                temperatureCelsius, chargingType, rawImportData, createdAt, updatedAt,
                routeType, tireType, supersededBy, sessionGroupId, isPublicCharging, cpoName, null);
    }

    // Full constructor - accepts explicit measurementType (null = derive from dataSource)
    public EvLog(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, DataSource dataSource,
            boolean includeInStatistics, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            Double temperatureCelsius, ChargingType chargingType, String rawImportData,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            RouteType routeType, TireType tireType, UUID supersededBy, UUID sessionGroupId,
            boolean isPublicCharging, String cpoName, EnergyMeasurementType measurementType) {
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
        LocalDateTime base = loggedAt != null ? loggedAt : LocalDateTime.now();
        this.loggedAt = base.withSecond(0).withNano(0);
        this.dataSource = dataSource != null ? dataSource : DataSource.USER_LOGGED;
        this.measurementType = measurementType != null ? measurementType : this.dataSource.measurementType();
        this.includeInStatistics = includeInStatistics;
        this.odometerSuggestionMinKm = odometerSuggestionMinKm;
        this.odometerSuggestionMaxKm = odometerSuggestionMaxKm;
        this.temperatureCelsius = temperatureCelsius;
        this.chargingType = inferChargingType(chargingType, maxChargingPowerKw, kwhCharged, chargeDurationMinutes);
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
        return createFromInternal(carId, kwhCharged, chargeDurationMinutes, geohash,
                loggedAt, odometerSuggestionMinKm, odometerSuggestionMaxKm,
                dataSource, costEur, chargingType, null, null, null, null);
    }

    public static EvLog createFromInternal(UUID carId, BigDecimal kwhCharged,
            Integer chargeDurationMinutes, String geohash,
            LocalDateTime loggedAt, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            DataSource dataSource, BigDecimal costEur, ChargingType chargingType,
            Integer odometerKm, Integer socBefore, Integer socAfter, Double temperatureCelsius) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, null, socAfter, socBefore, loggedAt,
                dataSource, dataSource.includeInStatistics(),
                odometerSuggestionMinKm, odometerSuggestionMaxKm, temperatureCelsius, chargingType, null, now, now, null, null, null, null,
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
        return createFromPublicApi(carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent, loggedAt,
                chargingType, routeType, tireType, dataSource, rawImportData, false, null);
    }

    public static EvLog createFromPublicApi(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType, RouteType routeType, TireType tireType,
            DataSource dataSource, String rawImportData, boolean isPublicCharging, String cpoName) {
        return createFromPublicApi(carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent, loggedAt,
                chargingType, routeType, tireType, dataSource, rawImportData, isPublicCharging, cpoName, null);
    }

    public static EvLog createFromPublicApi(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType, RouteType routeType, TireType tireType,
            DataSource dataSource, String rawImportData, boolean isPublicCharging, String cpoName,
            EnergyMeasurementType measurementType) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw,
                socAfterChargePercent, socBeforeChargePercent, loggedAt,
                dataSource, dataSource.includeInStatistics(),
                null, null, null, chargingType, rawImportData, now, now, routeType, tireType, null, null,
                isPublicCharging, cpoName, measurementType);
    }

    /**
     * Infers AC/DC if type is unknown, using two heuristics in order:
     * 1. max_charging_power_kw if available
     * 2. avg power calculated from kWh / duration (only if duration >= 5 min to avoid noise)
     * Threshold: >22 kW = DC, ≤22 kW = AC.
     */
    private static ChargingType inferChargingType(ChargingType given, BigDecimal maxPowerKw) {
        if (given != null && given != ChargingType.UNKNOWN) return given;
        if (maxPowerKw != null) {
            return maxPowerKw.compareTo(BigDecimal.valueOf(22)) > 0 ? ChargingType.DC : ChargingType.AC;
        }
        return ChargingType.UNKNOWN;
    }

    private static ChargingType inferChargingType(ChargingType given, BigDecimal maxPowerKw,
                                                   BigDecimal kwhCharged, Integer durationMinutes) {
        if (given != null && given != ChargingType.UNKNOWN) return given;
        if (maxPowerKw != null) {
            return maxPowerKw.compareTo(BigDecimal.valueOf(22)) > 0 ? ChargingType.DC : ChargingType.AC;
        }
        if (kwhCharged != null && durationMinutes != null && durationMinutes >= 1) {
            double avgKw = kwhCharged.doubleValue() / (durationMinutes / 60.0);
            return avgKw > 22 ? ChargingType.DC : ChargingType.AC;
        }
        return ChargingType.UNKNOWN;
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

    public EnergyMeasurementType getMeasurementType() {
        return measurementType;
    }

    public EvLog withPatch(BigDecimal kwh, BigDecimal costEur, Integer durationMin,
            String geohash, Integer odometerKm, Integer socBefore, Integer socAfter,
            BigDecimal maxChargingPowerKw, ChargingType chargingType,
            RouteType routeType, TireType tireType, Boolean isPublicCharging, String cpoName) {
        return withPatch(kwh, costEur, durationMin, geohash, odometerKm, socBefore, socAfter,
                maxChargingPowerKw, chargingType, routeType, tireType, isPublicCharging, cpoName, null);
    }

    public EvLog withPatch(BigDecimal kwh, BigDecimal costEur, Integer durationMin,
            String geohash, Integer odometerKm, Integer socBefore, Integer socAfter,
            BigDecimal maxChargingPowerKw, ChargingType chargingType,
            RouteType routeType, TireType tireType, Boolean isPublicCharging, String cpoName,
            EnergyMeasurementType measurementType) {
        return new EvLog(id, carId,
                kwh != null ? kwh : this.kwhCharged,
                costEur != null ? costEur : this.costEur,
                durationMin != null ? durationMin : this.chargeDurationMinutes,
                geohash != null ? geohash : this.geohash,
                odometerKm != null ? odometerKm : this.odometerKm,
                maxChargingPowerKw != null ? maxChargingPowerKw : this.maxChargingPowerKw,
                socAfter != null ? socAfter : this.socAfterChargePercent,
                socBefore != null ? socBefore : this.socBeforeChargePercent,
                this.loggedAt, this.dataSource, this.includeInStatistics,
                this.odometerSuggestionMinKm, this.odometerSuggestionMaxKm, this.temperatureCelsius,
                chargingType != null ? chargingType : this.chargingType,
                this.rawImportData, this.createdAt, LocalDateTime.now(),
                routeType != null ? routeType : this.routeType,
                tireType != null ? tireType : this.tireType,
                this.supersededBy, this.sessionGroupId,
                isPublicCharging != null ? isPublicCharging : this.isPublicCharging,
                cpoName != null ? cpoName : this.cpoName,
                measurementType != null ? measurementType : this.measurementType);
    }

    public EvLog withIncludeInStatistics(boolean includeInStatistics) {
        return new EvLog(id, carId, kwhCharged, costEur, chargeDurationMinutes, geohash, odometerKm,
                maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent, loggedAt,
                dataSource, includeInStatistics, odometerSuggestionMinKm, odometerSuggestionMaxKm,
                temperatureCelsius, chargingType, rawImportData, createdAt, LocalDateTime.now(),
                routeType, tireType, supersededBy, sessionGroupId, isPublicCharging, cpoName,
                this.measurementType);
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
