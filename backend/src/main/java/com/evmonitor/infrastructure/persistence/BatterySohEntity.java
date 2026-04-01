package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "car_battery_soh_log")
public class BatterySohEntity {

    @Id
    private UUID id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "soh_percent", nullable = false)
    private BigDecimal sohPercent;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public BatterySohEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCarId() { return carId; }
    public void setCarId(UUID carId) { this.carId = carId; }

    public BigDecimal getSohPercent() { return sohPercent; }
    public void setSohPercent(BigDecimal sohPercent) { this.sohPercent = sohPercent; }

    public LocalDate getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDate recordedAt) { this.recordedAt = recordedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
