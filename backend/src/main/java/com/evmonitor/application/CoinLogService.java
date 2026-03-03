package com.evmonitor.application;

import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinLogRepository;
import com.evmonitor.domain.CoinType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CoinLogService {

    /** Canonical action description constants — used for both awarding and first-time checks. */
    public static final String ACTION_CAR_CREATED           = "Fahrzeug hinzugefügt";
    public static final String ACTION_LOG_CREATED           = "Ladevorgang erfasst";
    public static final String ACTION_SPRITMONITOR_IMPORTED = "Sprit-Monitor Import";
    public static final String ACTION_IMAGE_UPLOADED        = "Erstes Auto-Bild hochgeladen";
    public static final String ACTION_IMAGE_PUBLIC          = "Auto-Bild öffentlich geteilt";

    private final CoinLogRepository coinLogRepository;

    public CoinLogService(CoinLogRepository coinLogRepository) {
        this.coinLogRepository = coinLogRepository;
    }

    /**
     * Returns true if the user has EVER received a coin for this action.
     * Used to prevent first-time bonuses from being re-farmed via delete-and-recreate.
     * CoinLogs are never deleted, so the history is a reliable source of truth.
     */
    public boolean hasEverReceivedCoinForAction(UUID userId, String actionDescription) {
        return coinLogRepository.existsByUserIdAndActionDescription(userId, actionDescription);
    }

    /**
     * Award coins to a user for a specific action.
     */
    public CoinLogResponse awardCoins(UUID userId, CoinType coinType, Integer amount, String actionDescription) {
        CoinLog newLog = CoinLog.createNew(userId, coinType, amount, actionDescription);
        CoinLog saved = coinLogRepository.save(newLog);
        return CoinLogResponse.fromDomain(saved);
    }

    /**
     * Get all coin logs for a user.
     */
    public List<CoinLogResponse> getCoinLogsForUser(UUID userId) {
        return coinLogRepository.findAllByUserId(userId).stream()
                .map(CoinLogResponse::fromDomain)
                .toList();
    }

    /**
     * Get coin logs filtered by coin type.
     */
    public List<CoinLogResponse> getCoinLogsByType(UUID userId, CoinType coinType) {
        return coinLogRepository.findAllByUserIdAndCoinType(userId, coinType).stream()
                .map(CoinLogResponse::fromDomain)
                .toList();
    }

    /**
     * Get total coin balance for a user.
     */
    public CoinBalanceResponse getCoinBalance(UUID userId) {
        Integer totalCoins = coinLogRepository.getTotalCoinsByUserId(userId);

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Integer coinsThisMonth = coinLogRepository.getTotalCoinsByUserIdSince(userId, startOfMonth);

        Map<CoinType, Integer> coinsByType = Arrays.stream(CoinType.values())
                .collect(Collectors.toMap(
                        coinType -> coinType,
                        coinType -> coinLogRepository.getTotalCoinsByUserIdAndCoinType(userId, coinType)
                ));

        return new CoinBalanceResponse(totalCoins, coinsThisMonth, coinsByType);
    }
}
