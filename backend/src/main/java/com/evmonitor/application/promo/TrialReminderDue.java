package com.evmonitor.application.promo;

import java.time.LocalDateTime;
import java.util.UUID;

/** A user whose trial-ending reminder is due; the trial ends 7 days after {@code dueAt}. */
public record TrialReminderDue(UUID userId, String email, String username, String locale, LocalDateTime dueAt) {}
