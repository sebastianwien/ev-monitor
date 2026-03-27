package com.evmonitor.application;

import java.math.BigDecimal;

public record AdminChargingActivityRow(
        String day,
        long chargeCount,
        BigDecimal kwhTotal,
        BigDecimal costTotal,
        String dataSources,
        long durationMinutesTotal,
        BigDecimal kwhAvg,
        BigDecimal costAvg,
        long durationMinutesAvg
) {}
