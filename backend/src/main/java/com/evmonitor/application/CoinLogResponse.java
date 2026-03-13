package com.evmonitor.application;

import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CoinLogResponse(
        UUID id,
        UUID userId,
        CoinType coinType,
        Integer amount,
        String actionDescription,
        UUID sourceEntityId,
        LocalDateTime createdAt
) {
    public static CoinLogResponse fromDomain(CoinLog coinLog) {
        return new CoinLogResponse(
                coinLog.getId(),
                coinLog.getUserId(),
                coinLog.getCoinType(),
                coinLog.getAmount(),
                coinLog.getActionDescription(),
                coinLog.getSourceEntityId(),
                coinLog.getCreatedAt()
        );
    }
}
