package com.evmonitor.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveTripStoryRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 300) String summary,
        @Size(max = 5) String language,
        @Size(max = 100) List<TripStoryBlockRequest> blocks
) {
}
