package com.evmonitor.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.evmonitor.domain.TripLoadExtra;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateTripRequest(
        @NotNull UUID carId,
        @NotNull OffsetDateTime tripStartedAt,
        @NotNull OffsetDateTime tripEndedAt,
        @DecimalMin("0.0") @DecimalMax("9999.9") BigDecimal distanceKm,
        @Pattern(regexp = "^(CITY|COMBINED|HIGHWAY)?$") String routeType,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal socStart,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal socEnd,
        @Size(max = 10) List<TripLoadExtra> loadExtras
) {}
