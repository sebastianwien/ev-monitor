package com.evmonitor.application;

import java.math.BigDecimal;

public record InternalSmartcarWebhookLogRequest(
        String eventId,
        String smartcarVehicleId,
        String make,
        String model,
        Integer year,
        String triggersJson,
        String signalsJson,
        Integer socPercent,
        BigDecimal odometerKm,
        String locationGeohash,
        BigDecimal outsideTempCelsius,
        String mode
) {}
