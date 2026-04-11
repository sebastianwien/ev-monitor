package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a coin reward entry in the gamification system.
 * Tracks when a user earned coins, what type, how many, and why.
 */
@Getter
@Builder
@AllArgsConstructor
public class CoinLog {

    private final UUID id;
    private final UUID userId;
    private final CoinType coinType;
    private final Integer amount;
    private final String actionDescription; // Human-readable description of what triggered the reward
    private final UUID sourceEntityId;      // Optional: EvLog ID that triggered this award (for deletion deduction)
    private final LocalDateTime createdAt;

    public static CoinLog createNew(UUID userId, CoinType coinType, Integer amount, String actionDescription) {
        return createNew(userId, coinType, amount, actionDescription, null);
    }

    public static CoinLog createNew(UUID userId, CoinType coinType, Integer amount,
                                    String actionDescription, UUID sourceEntityId) {
        return new CoinLog(UUID.randomUUID(), userId, coinType, amount,
                actionDescription, sourceEntityId, LocalDateTime.now());
    }
}
