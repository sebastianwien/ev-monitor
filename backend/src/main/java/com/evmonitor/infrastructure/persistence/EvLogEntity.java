package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ev_log")
public class EvLogEntity {

    @Id
    private UUID id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "kwh_charged", nullable = false, precision = 10, scale = 2)
    private BigDecimal kwhCharged;

    @Column(name = "cost_eur", nullable = true, precision = 10, scale = 2)
    private BigDecimal costEur;

    @Column(name = "charge_duration_minutes")
    private Integer chargeDurationMinutes;

    @Column(name = "geohash", length = 5)
    private String geohash;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Column(name = "max_charging_power_kw", precision = 10, scale = 2)
    private BigDecimal maxChargingPowerKw;

    @Column(name = "soc_after_charge_percent")
    private Integer socAfterChargePercent;

    @Column(name = "soc_start_percent")
    private Integer socBeforeChargePercent;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    @Column(name = "data_source", length = 50)
    private String dataSource;

    @Column(name = "include_in_statistics", nullable = false)
    private boolean includeInStatistics;

    @Column(name = "odometer_suggestion_min_km")
    private Integer odometerSuggestionMinKm;

    @Column(name = "odometer_suggestion_max_km")
    private Integer odometerSuggestionMaxKm;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "charging_type", length = 10)
    private String chargingType;

    @Column(name = "raw_import_data")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String rawImportData;

    @Column(name = "route_type", length = 20)
    private String routeType;

    @Column(name = "tire_type", length = 20)
    private String tireType;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public EvLogEntity() {
    }

    public EvLogEntity(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt, String dataSource,
            boolean includeInStatistics, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.carId = carId;
        this.kwhCharged = kwhCharged;
        this.costEur = costEur;
        this.chargeDurationMinutes = chargeDurationMinutes;
        this.geohash = geohash;
        this.odometerKm = odometerKm;
        this.maxChargingPowerKw = maxChargingPowerKw;
        this.socAfterChargePercent = socAfterChargePercent;
        this.loggedAt = loggedAt;
        this.dataSource = dataSource;
        this.includeInStatistics = includeInStatistics;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCarId() {
        return carId;
    }

    public void setCarId(UUID carId) {
        this.carId = carId;
    }

    public BigDecimal getKwhCharged() {
        return kwhCharged;
    }

    public void setKwhCharged(BigDecimal kwhCharged) {
        this.kwhCharged = kwhCharged;
    }

    public BigDecimal getCostEur() {
        return costEur;
    }

    public void setCostEur(BigDecimal costEur) {
        this.costEur = costEur;
    }

    public Integer getChargeDurationMinutes() {
        return chargeDurationMinutes;
    }

    public void setChargeDurationMinutes(Integer chargeDurationMinutes) {
        this.chargeDurationMinutes = chargeDurationMinutes;
    }

    public String getGeohash() {
        return geohash;
    }

    public void setGeohash(String geohash) {
        this.geohash = geohash;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(LocalDateTime loggedAt) {
        this.loggedAt = loggedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getOdometerKm() {
        return odometerKm;
    }

    public void setOdometerKm(Integer odometerKm) {
        this.odometerKm = odometerKm;
    }

    public BigDecimal getMaxChargingPowerKw() {
        return maxChargingPowerKw;
    }

    public void setMaxChargingPowerKw(BigDecimal maxChargingPowerKw) {
        this.maxChargingPowerKw = maxChargingPowerKw;
    }

    public Integer getSocAfterChargePercent() {
        return socAfterChargePercent;
    }

    public void setSocAfterChargePercent(Integer socAfterChargePercent) {
        this.socAfterChargePercent = socAfterChargePercent;
    }

    public Integer getSocBeforeChargePercent() {
        return socBeforeChargePercent;
    }

    public void setSocBeforeChargePercent(Integer socBeforeChargePercent) {
        this.socBeforeChargePercent = socBeforeChargePercent;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isIncludeInStatistics() {
        return includeInStatistics;
    }

    public void setIncludeInStatistics(boolean includeInStatistics) {
        this.includeInStatistics = includeInStatistics;
    }

    public Integer getOdometerSuggestionMinKm() {
        return odometerSuggestionMinKm;
    }

    public void setOdometerSuggestionMinKm(Integer odometerSuggestionMinKm) {
        this.odometerSuggestionMinKm = odometerSuggestionMinKm;
    }

    public Integer getOdometerSuggestionMaxKm() {
        return odometerSuggestionMaxKm;
    }

    public void setOdometerSuggestionMaxKm(Integer odometerSuggestionMaxKm) {
        this.odometerSuggestionMaxKm = odometerSuggestionMaxKm;
    }

    public Double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(Double temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public String getChargingType() {
        return chargingType;
    }

    public void setChargingType(String chargingType) {
        this.chargingType = chargingType;
    }

    public String getRawImportData() {
        return rawImportData;
    }

    public void setRawImportData(String rawImportData) {
        this.rawImportData = rawImportData;
    }

    public String getRouteType() {
        return routeType;
    }

    public void setRouteType(String routeType) {
        this.routeType = routeType;
    }

    public String getTireType() {
        return tireType;
    }

    public void setTireType(String tireType) {
        this.tireType = tireType;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(UUID supersededBy) {
        this.supersededBy = supersededBy;
    }
}
