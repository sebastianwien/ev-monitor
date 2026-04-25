package com.evmonitor.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateTripRequest(
        OffsetDateTime tripStartedAt,
        OffsetDateTime tripEndedAt,
        @DecimalMin("0.0") @DecimalMax("9999.9") BigDecimal distanceKm,
        @Pattern(regexp = "^(CITY|COMBINED|HIGHWAY)?$") String routeType,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal socStart,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal socEnd,
        @Size(max = 1000) String feedback
) {}
