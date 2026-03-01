package com.evmonitor.application.user;

import java.time.LocalDateTime;

public record UserStatsResponse(
        LocalDateTime registeredSince,
        int totalLogs,
        double totalKwh,
        double totalCostEur
) {
}
