package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class ApiKey {

    private final UUID id;
    private final UUID userId;
    private final String keyHash;    // SHA-256 hex
    private final String keyPrefix;  // "evm_XXXX" — nur für UI
    private final String name;
    private final LocalDateTime lastUsedAt;
    private final LocalDateTime createdAt;

    public ApiKey(UUID id, UUID userId, String keyHash, String keyPrefix,
                  String name, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.name = name;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
    }

    public static ApiKey createNew(UUID userId, String keyHash, String keyPrefix, String name) {
        return new ApiKey(UUID.randomUUID(), userId, keyHash, keyPrefix, name, null, LocalDateTime.now());
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getName() { return name; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
