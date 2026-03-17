package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal projection for leaderboard query results.
 * Not exposed in API responses.
 */
public record LeaderboardRankRow(UUID userId, String username, BigDecimal value) {
}
