package com.evmonitor.domain.rating;

/** Grund einer Ablehnung. Bei {@link #OTHER} ist eine Notiz Pflicht. */
public enum RejectionReason {
    PERSONAL_DATA,
    INSULT,
    ADVERTISING,
    WRONG_MODEL,
    NO_EXPERIENCE,
    OTHER
}
