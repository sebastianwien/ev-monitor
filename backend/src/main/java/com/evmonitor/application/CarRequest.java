package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CarRequest(
        @NotNull(message = "Model is required")
        CarBrand.CarModel model,

        @NotNull(message = "Year is required")
        @Positive(message = "Year must be positive")
        Integer year,

        String licensePlate,

        String trim,

        @NotNull(message = "Battery capacity is required")
        @Positive(message = "Battery capacity must be positive")
        BigDecimal batteryCapacityKwh,

        @Positive(message = "Power must be positive")
        BigDecimal powerKw) {
}
