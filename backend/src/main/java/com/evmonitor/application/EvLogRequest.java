package com.evmonitor.application;

import com.evmonitor.domain.DrivingStyle;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvLogRequest(
                UUID carId,
                BigDecimal distanceKm,
                BigDecimal consumptionKwhPer100km,
                BigDecimal outsideTempC,
                DrivingStyle drivingStyle,
                Double latitude,  // Optional: for geolocation (not stored, converted to geohash)
                Double longitude, // Optional: for geolocation (not stored, converted to geohash)
                LocalDateTime loggedAt) { // Optional: when the drive happened
}
