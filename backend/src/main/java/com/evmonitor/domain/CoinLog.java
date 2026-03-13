package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a coin reward entry in the gamification system.
 * Tracks when a user earned coins, what type, how many, and why.
 */
public class CoinLog {

    private final UUID id;
    private final UUID userId;
    private final CoinType coinType;
    private final Integer amount;
    private final String actionDescription; // Human-readable description of what triggered the reward
    private final UUID sourceEntityId;      // Optional: EvLog ID that triggered this award (for deletion deduction)
    private final LocalDateTime createdAt;

    public CoinLog(UUID id, UUID userId, CoinType coinType, Integer amount,
                   String actionDescription, UUID sourceEntityId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.coinType = coinType;
        this.amount = amount;
        this.actionDescription = actionDescription;
        this.sourceEntityId = sourceEntityId;
        this.createdAt = createdAt;
    }

    public static CoinLog createNew(UUID userId, CoinType coinType, Integer amount,
                                     String actionDescription) {
        return createNew(userId, coinType, amount, actionDescription, null);
    }

    public static CoinLog createNew(UUID userId, CoinType coinType, Integer amount,
                                     String actionDescription, UUID sourceEntityId) {
        return new CoinLog(
                UUID.randomUUID(),
                userId,
                coinType,
                amount,
                actionDescription,
                sourceEntityId,
                LocalDateTime.now()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public CoinType getCoinType() {
        return coinType;
    }

    public Integer getAmount() {
        return amount;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
