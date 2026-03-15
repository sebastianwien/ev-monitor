package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charging_session_group")
public class ChargingSessionGroupEntity {

    @Id
    private UUID id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "total_kwh_charged", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalKwhCharged;

    @Column(name = "total_duration_minutes")
    private Integer totalDurationMinutes;

    @Column(name = "session_start", nullable = false)
    private LocalDateTime sessionStart;

    @Column(name = "session_end", nullable = false)
    private LocalDateTime sessionEnd;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "geohash", length = 5)
    private String geohash;

    @Column(name = "cost_eur", precision = 10, scale = 2)
    private BigDecimal costEur;

    @Column(name = "data_source", length = 50, nullable = false)
    private String dataSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ChargingSessionGroupEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCarId() { return carId; }
    public void setCarId(UUID carId) { this.carId = carId; }

    public BigDecimal getTotalKwhCharged() { return totalKwhCharged; }
    public void setTotalKwhCharged(BigDecimal totalKwhCharged) { this.totalKwhCharged = totalKwhCharged; }

    public Integer getTotalDurationMinutes() { return totalDurationMinutes; }
    public void setTotalDurationMinutes(Integer totalDurationMinutes) { this.totalDurationMinutes = totalDurationMinutes; }

    public LocalDateTime getSessionStart() { return sessionStart; }
    public void setSessionStart(LocalDateTime sessionStart) { this.sessionStart = sessionStart; }

    public LocalDateTime getSessionEnd() { return sessionEnd; }
    public void setSessionEnd(LocalDateTime sessionEnd) { this.sessionEnd = sessionEnd; }

    public int getSessionCount() { return sessionCount; }
    public void setSessionCount(int sessionCount) { this.sessionCount = sessionCount; }

    public String getGeohash() { return geohash; }
    public void setGeohash(String geohash) { this.geohash = geohash; }

    public BigDecimal getCostEur() { return costEur; }
    public void setCostEur(BigDecimal costEur) { this.costEur = costEur; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
