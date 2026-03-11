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
    private final String geohash; // 5-character geohash (~5km precision) for privacy
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
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EvLog(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, DataSource dataSource,
            boolean includeInStatistics, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            Double temperatureCelsius, LocalDateTime createdAt, LocalDateTime updatedAt) {
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static EvLog createNew(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, socAfterChargePercent, null, loggedAt,
                DataSource.USER_LOGGED, true, null, null, null, now, now);
    }

    public static EvLog createNewWithSource(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt, DataSource dataSource) {
        LocalDateTime now = LocalDateTime.now();
        boolean includeInStats = dataSource.includeInStatistics();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, socAfterChargePercent, null, loggedAt,
                dataSource, includeInStats, null, null, null, now, now);
    }

    public static EvLog createNewWithSourceAndSocBefore(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, DataSource dataSource) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, socAfterChargePercent,
                socBeforeChargePercent, loggedAt, dataSource, dataSource.includeInStatistics(),
                null, null, null, now, now);
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
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, null, null, null, null, loggedAt,
                dataSource, dataSource.includeInStatistics(),
                odometerSuggestionMinKm, odometerSuggestionMaxKm, null, now, now);
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
