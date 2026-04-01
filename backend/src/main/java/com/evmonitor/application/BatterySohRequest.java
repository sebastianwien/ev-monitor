package com.evmonitor.application;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BatterySohRequest(
        @NotNull(message = "sohPercent is required")
        @DecimalMin(value = "50.0", message = "SoH must be >= 50%")
        @DecimalMax(value = "100.0", message = "SoH must be <= 100%")
        BigDecimal sohPercent,

        @NotNull(message = "recordedAt is required")
        LocalDate recordedAt
) {}
