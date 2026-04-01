package com.evmonitor.application;

import com.evmonitor.domain.BatterySohEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BatterySohResponse(
        UUID id,
        UUID carId,
        BigDecimal sohPercent,
        LocalDate recordedAt,
        LocalDateTime createdAt
) {
    public static BatterySohResponse fromDomain(BatterySohEntry entry) {
        return new BatterySohResponse(
                entry.getId(),
                entry.getCarId(),
                entry.getSohPercent(),
                entry.getRecordedAt(),
                entry.getCreatedAt());
    }
}
