package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal projection for leaderboard query results.
 * Not exposed in API responses.
 * entityId = carId for car-based categories, userId for user-based (coins).
 * carLabel = "Tesla Model 3" for car-based, null for user-based.
 */
public record LeaderboardRankRow(UUID entityId, UUID userId, String username, String carLabel, BigDecimal value,
                                 BigDecimal kwhTotal, Long sessionCount) {
}
