package com.evmonitor.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

/** Projection: a user whose trial-ending reminder is due; the trial ends 7 days after {@code dueAt}. */
public record TrialReminderRow(UUID userId, String email, String username, String locale, LocalDateTime dueAt) {}
