package com.evmonitor.application;

import com.evmonitor.domain.FixedCost;
import com.evmonitor.domain.FixedCostCategory;
import com.evmonitor.domain.FixedCostRecurrence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FixedCostResponse(
        UUID id,
        UUID carId,
        String description,
        BigDecimal amount,
        FixedCostCategory category,
        FixedCostRecurrence recurrence,
        LocalDate date,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt
) {
    public static FixedCostResponse fromDomain(FixedCost fc) {
        return new FixedCostResponse(
                fc.getId(), fc.getCarId(), fc.getDescription(), fc.getAmount(),
                fc.getCategory(), fc.getRecurrence(), fc.getDate(),
                fc.getStartDate(), fc.getEndDate(), fc.getCreatedAt()
        );
    }
}
