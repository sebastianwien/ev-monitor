package com.evmonitor.application.feedback;

import com.evmonitor.domain.feedback.FeedbackRating;
import com.evmonitor.domain.feedback.FeedbackTag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DataFeedbackRequest(
        @NotNull FeedbackRating rating,
        @Size(max = 6) List<FeedbackTag> tags,
        @Size(max = 1000) String comment
) {
    public List<FeedbackTag> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }
}
