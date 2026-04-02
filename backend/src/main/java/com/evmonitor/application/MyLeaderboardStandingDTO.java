package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;

import java.math.BigDecimal;

public record MyLeaderboardStandingDTO(
        LeaderboardCategory category,
        String displayName,
        String unit,
        boolean lowerIsBetter,
        Integer rank,         // null = not ranked this month
        BigDecimal value,     // null = not ranked this month
        Integer rankDelta,
        boolean isNew,
        String carLabel       // nullable - "Tesla Model 3", null for user-based categories
) {}
