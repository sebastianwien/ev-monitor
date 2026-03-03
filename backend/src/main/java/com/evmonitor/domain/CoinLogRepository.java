package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoinLogRepository {
    CoinLog save(CoinLog coinLog);

    Optional<CoinLog> findById(UUID id);

    List<CoinLog> findAllByUserId(UUID userId);

    List<CoinLog> findAllByUserIdAndCoinType(UUID userId, CoinType coinType);

    /**
     * Returns true if any coin log entry exists for this user with the given action description.
     * Used to prevent first-time bonuses from being re-farmed via delete-and-recreate attacks.
     */
    boolean existsByUserIdAndActionDescription(UUID userId, String actionDescription);

    /**
     * Get total coin balance for a user, optionally filtered by coin type.
     */
    Integer getTotalCoinsByUserId(UUID userId);

    Integer getTotalCoinsByUserIdAndCoinType(UUID userId, CoinType coinType);
}
