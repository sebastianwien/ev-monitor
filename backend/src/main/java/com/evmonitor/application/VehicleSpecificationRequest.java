package com.evmonitor.application;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record VehicleSpecificationRequest(
        @NotBlank(message = "Car brand is required")
        @Size(max = 100, message = "Brand name too long")
        String carBrand,

        @NotBlank(message = "Car model is required")
        @Size(max = 100, message = "Model name too long")
        String carModel,

        @NotNull(message = "Battery capacity is required")
        @Positive(message = "Battery capacity must be positive")
        @DecimalMax(value = "500.0", message = "Battery capacity unrealistic (max 500 kWh)")
        BigDecimal batteryCapacityKwh,

        @NotNull(message = "WLTP range is required")
        @Positive(message = "WLTP range must be positive")
        @DecimalMax(value = "2000.0", message = "WLTP range unrealistic (max 2000 km)")
        BigDecimal wltpRangeKm,

        @NotNull(message = "WLTP consumption is required")
        @Positive(message = "WLTP consumption must be positive")
        @DecimalMax(value = "100.0", message = "WLTP consumption unrealistic (max 100 kWh/100km)")
        BigDecimal wltpConsumptionKwhPer100km
) {
}
