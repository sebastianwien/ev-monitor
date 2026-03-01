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
    private final LocalDateTime loggedAt; // When the charge happened (user-provided or now)
    private final String dataSource; // Source of data: USER_LOGGED, SPRITMONITOR_IMPORT, etc.
    private final boolean includeInStatistics; // Whether to include in public stats/aggregations
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EvLog(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, LocalDateTime loggedAt, String dataSource,
            boolean includeInStatistics, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.carId = carId;
        this.kwhCharged = kwhCharged;
        this.costEur = costEur;
        this.chargeDurationMinutes = chargeDurationMinutes;
        this.geohash = geohash;
        this.odometerKm = odometerKm;
        this.maxChargingPowerKw = maxChargingPowerKw;
        this.loggedAt = loggedAt != null ? loggedAt : LocalDateTime.now();
        this.dataSource = dataSource != null ? dataSource : "USER_LOGGED";
        this.includeInStatistics = includeInStatistics;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static EvLog createNew(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, LocalDateTime loggedAt) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, loggedAt,
                "USER_LOGGED", true, now, now); // USER_LOGGED always included in stats
    }

    public static EvLog createNewWithSource(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, LocalDateTime loggedAt, String dataSource) {
        LocalDateTime now = LocalDateTime.now();
        // Determine if should be included in statistics based on data source
        boolean includeInStats = shouldIncludeInStatistics(dataSource);
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, odometerKm, maxChargingPowerKw, loggedAt,
                dataSource, includeInStats, now, now);
    }

    private static boolean shouldIncludeInStatistics(String dataSource) {
        // Exclude incomplete/test data sources
        return dataSource == null ||
               (!dataSource.equals("TESLA_IMPORT") &&
                !dataSource.equals("SEED_DATA") &&
                !dataSource.startsWith("TEST_"));
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

    public String getDataSource() {
        return dataSource;
    }

    public boolean isIncludeInStatistics() {
        return includeInStatistics;
    }
}
