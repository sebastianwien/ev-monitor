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
    private final LocalDateTime loggedAt; // When the charge happened (user-provided or now)
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EvLog(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, LocalDateTime loggedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.carId = carId;
        this.kwhCharged = kwhCharged;
        this.costEur = costEur;
        this.chargeDurationMinutes = chargeDurationMinutes;
        this.geohash = geohash;
        this.loggedAt = loggedAt != null ? loggedAt : LocalDateTime.now();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static EvLog createNew(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, LocalDateTime loggedAt) {
        LocalDateTime now = LocalDateTime.now();
        return new EvLog(UUID.randomUUID(), carId, kwhCharged, costEur,
                chargeDurationMinutes, geohash, loggedAt, now, now);
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
}
