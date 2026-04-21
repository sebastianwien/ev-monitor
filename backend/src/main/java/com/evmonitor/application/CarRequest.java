package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

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
        BigDecimal powerKw,

        @DecimalMin(value = "0.0", message = "Battery degradation must be >= 0")
        @DecimalMax(value = "50.0", message = "Battery degradation must be <= 50")
        BigDecimal batteryDegradationPercent,

        boolean hasHeatPump,

        UUID vehicleSpecificationId) {
}
