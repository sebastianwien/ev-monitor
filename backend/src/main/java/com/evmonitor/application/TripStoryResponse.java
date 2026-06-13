package com.evmonitor.application;

import com.evmonitor.domain.TripStory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TripStoryResponse(
        UUID id,
        String title,
        String slug,
        String summary,
        String language,
        String status,
        JsonNode blocks,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime publishedAt
) {
    public static TripStoryResponse fromDomain(TripStory story, ObjectMapper objectMapper) {
        return TripStoryResponse.builder()
                .id(story.getId())
                .title(story.getTitle())
                .slug(story.getSlug())
                .summary(story.getSummary())
                .language(story.getLanguage())
                .status(story.getStatus())
                .blocks(TripStoryService.parseBlocks(story.getBlocks(), objectMapper))
                .createdAt(story.getCreatedAt())
                .updatedAt(story.getUpdatedAt())
                .publishedAt(story.getPublishedAt())
                .build();
    }
}
