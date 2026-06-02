package com.evmonitor.application;

import com.evmonitor.domain.FixedCostCategory;
import com.evmonitor.domain.FixedCostRecurrence;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedCostRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotNull FixedCostCategory category,
        @NotNull FixedCostRecurrence recurrence,
        LocalDate date,
        LocalDate startDate,
        LocalDate endDate
) {}
