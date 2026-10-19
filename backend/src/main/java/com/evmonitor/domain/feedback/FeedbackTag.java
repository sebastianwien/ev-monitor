package com.evmonitor.domain.feedback;

import java.util.EnumSet;
import java.util.Set;

import static com.evmonitor.domain.feedback.FeedbackSubjectType.LOG;
import static com.evmonitor.domain.feedback.FeedbackSubjectType.TRIP;

/** Was an einer negativ bewerteten Erkennung nicht stimmte. Jeder Tag gilt nur für bestimmte Gegenstände. */
public enum FeedbackTag {
    KWH_WRONG(EnumSet.of(LOG)),
    SOC_WRONG(EnumSet.of(LOG)),
    DISTANCE_WRONG(EnumSet.of(TRIP)),
    TIME_WRONG(EnumSet.of(LOG, TRIP)),
    DUPLICATE(EnumSet.of(LOG, TRIP)),
    OTHER(EnumSet.of(LOG, TRIP));

    private final Set<FeedbackSubjectType> subjects;

    FeedbackTag(Set<FeedbackSubjectType> subjects) {
        this.subjects = subjects;
    }

    public boolean appliesTo(FeedbackSubjectType subject) {
        return subjects.contains(subject);
    }
}
