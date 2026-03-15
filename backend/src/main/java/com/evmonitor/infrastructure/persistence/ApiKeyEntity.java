package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_key")
public class ApiKeyEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ApiKeyEntity() {}

    public ApiKeyEntity(UUID id, UUID userId, String keyHash, String keyPrefix,
                        String name, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.name = name;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
