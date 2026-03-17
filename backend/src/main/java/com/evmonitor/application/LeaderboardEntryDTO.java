package com.evmonitor.application;

import java.math.BigDecimal;

public record LeaderboardEntryDTO(
        int rank,
        String username,
        BigDecimal value,
        String unit,
        Integer previousRank,
        Integer rankDelta,   // positive = moved up (e.g. was 5th, now 3rd -> delta +2), negative = moved down
        boolean isNew        // true if this user was not in top 10 yesterday (only meaningful if top10)
) {
}
