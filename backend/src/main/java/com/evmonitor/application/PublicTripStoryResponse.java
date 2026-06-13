package com.evmonitor.application;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record PublicTripStoryResponse(
        String title,
        String slug,
        String summary,
        String language,
        String authorUsername,
        OffsetDateTime publishedAt,
        JsonNode blocks
) {
}
