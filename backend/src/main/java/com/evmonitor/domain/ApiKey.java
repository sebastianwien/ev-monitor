package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ApiKey {

    private final UUID id;
    private final UUID userId;
    private final String keyHash;    // SHA-256 hex
    private final String keyPrefix;  // "evm_XXXX" - nur für UI
    private final String name;
    private final LocalDateTime lastUsedAt;
    private final LocalDateTime createdAt;
    private final boolean mergeSessions;

    public static ApiKey createNew(UUID userId, String keyHash, String keyPrefix, String name) {
        return new ApiKey(UUID.randomUUID(), userId, keyHash, keyPrefix, name, null, LocalDateTime.now(), false);
    }
}
