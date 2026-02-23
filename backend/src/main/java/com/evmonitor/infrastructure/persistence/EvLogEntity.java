package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.DrivingStyle;
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

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "consumption_kwh_per_100km", precision = 10, scale = 2)
    private BigDecimal consumptionKwhPer100km;

    @Column(name = "outside_temp_c", precision = 10, scale = 2)
    private BigDecimal outsideTempC;

    @Enumerated(EnumType.STRING)
    @Column(name = "driving_style", length = 20)
    private DrivingStyle drivingStyle;

    @Column(name = "geohash", length = 5)
    private String geohash;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public EvLogEntity() {
    }

    public EvLogEntity(UUID id, UUID carId, BigDecimal distanceKm, BigDecimal consumptionKwhPer100km,
            BigDecimal outsideTempC, DrivingStyle drivingStyle, String geohash, LocalDateTime loggedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.carId = carId;
        this.distanceKm = distanceKm;
        this.consumptionKwhPer100km = consumptionKwhPer100km;
        this.outsideTempC = outsideTempC;
        this.drivingStyle = drivingStyle;
        this.geohash = geohash;
        this.loggedAt = loggedAt;
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

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public BigDecimal getConsumptionKwhPer100km() {
        return consumptionKwhPer100km;
    }

    public void setConsumptionKwhPer100km(BigDecimal consumptionKwhPer100km) {
        this.consumptionKwhPer100km = consumptionKwhPer100km;
    }

    public BigDecimal getOutsideTempC() {
        return outsideTempC;
    }

    public void setOutsideTempC(BigDecimal outsideTempC) {
        this.outsideTempC = outsideTempC;
    }

    public DrivingStyle getDrivingStyle() {
        return drivingStyle;
    }

    public void setDrivingStyle(DrivingStyle drivingStyle) {
        this.drivingStyle = drivingStyle;
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
}
