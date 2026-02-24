package com.evmonitor.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvLogRequest(
                @NotNull UUID carId,
                @NotNull @Positive BigDecimal kwhCharged,
                @NotNull @Positive BigDecimal costEur,
                @NotNull @Positive Integer chargeDurationMinutes,
                Double latitude,  // Optional: for geolocation (not stored, converted to geohash)
                Double longitude, // Optional: for geolocation (not stored, converted to geohash)
                LocalDateTime loggedAt) { // Optional: when the charge happened
}
