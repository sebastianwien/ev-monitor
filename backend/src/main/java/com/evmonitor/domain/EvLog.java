package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class EvLog {

    private final UUID id;
    private final UUID carId;
    private final BigDecimal distanceKm;
    private final BigDecimal consumptionKwhPer100km;
    private final BigDecimal outsideTempC;
    private final DrivingStyle drivingStyle;
    private final String geohash; // 5-character geohash (~5km precision) for privacy
    private final LocalDateTime loggedAt; // When the drive happened (user-provided or now)
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EvLog(UUID id, UUID carId, BigDecimal distanceKm, BigDecimal consumptionKwhPer100km,
            BigDecimal outsideTempC, DrivingStyle drivingStyle, String geohash, LocalDateTime loggedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.carId = carId;
        this.distanceKm = distanceKm;
        this.consumptionKwhPer100km = consumptionKwhPer100km;
        this.outsideTempC = outsideTempC;
        this.drivingStyle = drivingStyle;
        this.geohash = geohash;
        this.loggedAt = loggedAt != null ? loggedAt : LocalDateTime.now();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static EvLog createNew(UUID carId, BigDecimal distanceKm, BigDecimal consumptionKwhPer100km,
            BigDecimal outsideTempC, DrivingStyle drivingStyle, String geohash, LocalDateTime loggedAt) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, distanceKm, consumptionKwhPer100km,
                outsideTempC, drivingStyle, geohash, loggedAt, now, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCarId() {
        return carId;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public BigDecimal getConsumptionKwhPer100km() {
        return consumptionKwhPer100km;
    }

    public BigDecimal getOutsideTempC() {
        return outsideTempC;
    }

    public DrivingStyle getDrivingStyle() {
        return drivingStyle;
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
}
