package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class BatterySohEntry {

    private final UUID id;
    private final UUID carId;
    private final BigDecimal sohPercent;
    private final LocalDate recordedAt;
    private final LocalDateTime createdAt;

    public BatterySohEntry(UUID id, UUID carId, BigDecimal sohPercent, LocalDate recordedAt, LocalDateTime createdAt) {
        this.id = id;
        this.carId = carId;
        this.sohPercent = sohPercent;
        this.recordedAt = recordedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCarId() { return carId; }
    public BigDecimal getSohPercent() { return sohPercent; }
    public LocalDate getRecordedAt() { return recordedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
