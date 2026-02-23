package com.evmonitor.application;

import com.evmonitor.domain.CoinType;

import java.util.Map;

/**
 * Summary of a user's coin balance.
 * Contains total coins and breakdown by coin type.
 */
public record CoinBalanceResponse(
        Integer totalCoins,
        Map<CoinType, Integer> coinsByType
) {
}
