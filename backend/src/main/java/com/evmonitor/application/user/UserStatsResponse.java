package com.evmonitor.application.user;

import java.time.LocalDateTime;

public record UserStatsResponse(
        LocalDateTime registeredSince,
        int totalLogs,
        double totalKwh,
        double totalCostEur,
        String referralCode,
        boolean leaderboardVisible,
        boolean tickerShareCharges,
        /** false for accounts created via mail code: settings then offer a set-password link */
        boolean hasPassword
) {
}
