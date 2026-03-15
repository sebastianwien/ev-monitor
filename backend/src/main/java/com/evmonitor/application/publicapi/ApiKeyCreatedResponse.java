package com.evmonitor.application.publicapi;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response when a new API key is created. The {@code plaintextKey} field is returned
 * ONCE and never stored — the caller must save it immediately.
 */
public record ApiKeyCreatedResponse(
        UUID id,
        String keyPrefix,
        String name,
        LocalDateTime createdAt,
        String plaintextKey
) {}
