package com.evmonitor.application.publicapi;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String keyPrefix,
        String name,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt,
        boolean mergeSessions
) {}
