package com.evmonitor.application.feedback;

import com.evmonitor.domain.feedback.DataFeedback;
import com.evmonitor.domain.feedback.FeedbackRating;
import com.evmonitor.domain.feedback.FeedbackSubjectType;
import com.evmonitor.domain.feedback.FeedbackTag;

import java.util.List;
import java.util.UUID;

public record DataFeedbackResponse(
        FeedbackSubjectType subjectType,
        UUID subjectId,
        FeedbackRating rating,
        List<FeedbackTag> tags,
        String comment
) {
    static DataFeedbackResponse from(DataFeedback f) {
        return new DataFeedbackResponse(f.getSubjectType(), f.getSubjectId(), f.getRating(), f.tagList(), f.getComment());
    }
}
