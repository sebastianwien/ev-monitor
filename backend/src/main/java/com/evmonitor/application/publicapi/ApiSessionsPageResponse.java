package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated list of charging sessions")
public record ApiSessionsPageResponse(
        @JsonProperty("sessions") List<ApiSessionResponse> sessions,
        @JsonProperty("total") long total,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("has_more") boolean hasMore
) {}
