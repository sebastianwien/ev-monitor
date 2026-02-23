package com.evmonitor.application;

import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinLogRepository;
import com.evmonitor.domain.CoinType;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CoinLogService {

    private final CoinLogRepository coinLogRepository;

    public CoinLogService(CoinLogRepository coinLogRepository) {
        this.coinLogRepository = coinLogRepository;
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

        Map<CoinType, Integer> coinsByType = Arrays.stream(CoinType.values())
                .collect(Collectors.toMap(
                        coinType -> coinType,
                        coinType -> coinLogRepository.getTotalCoinsByUserIdAndCoinType(userId, coinType)
                ));

        return new CoinBalanceResponse(totalCoins, coinsByType);
    }
}
