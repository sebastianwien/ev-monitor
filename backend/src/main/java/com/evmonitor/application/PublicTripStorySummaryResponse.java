package com.evmonitor.application;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record PublicTripStorySummaryResponse(
        String title,
        String slug,
        String summary,
        String language,
        String authorUsername,
        OffsetDateTime publishedAt
) {
}
