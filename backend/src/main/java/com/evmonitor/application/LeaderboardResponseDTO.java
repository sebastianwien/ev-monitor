package com.evmonitor.application;

import com.evmonitor.domain.LeaderboardCategory;

import java.util.List;

public record LeaderboardResponseDTO(
        LeaderboardCategory category,
        String displayName,
        String unit,
        boolean lowerIsBetter,
        String period,
        List<LeaderboardEntryDTO> entries,
        LeaderboardEntryDTO ownEntry  // null if user is in top 10, not logged in, or has no data
) {
}
