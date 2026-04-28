package com.evmonitor.application;

import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinLogRepository;
import com.evmonitor.domain.CoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoinLogService {

    /**
     * Centralised coin event definitions — single source of truth for all coin awards.
     *
     * Each event carries:
     * - description: the canonical action_description stored in coin_log (also used for first-time checks)
     * - defaultAmount: coins awarded for this event
     * - oneTime: if true, awardCoinsForEvent() automatically skips the award when the user already
     *   has a coin_log entry with this description. No caller-side guard needed.
     *   If false, the caller is responsible for any idempotency logic (e.g. FIRST/SUBSEQUENT pairs,
     *   count-based caps like referral invites).
     */
    public enum CoinEvent {
        // Manual charging logs — FIRST/SUBSEQUENT chosen by caller; oneTime handled via caller logic
        MANUAL_LOG_FIRST            ("Ladevorgang erfasst",                          25, false),
        MANUAL_LOG_SUBSEQUENT       ("Ladevorgang erfasst",                           5, false),
        MANUAL_LOG_FIRST_OCR        ("Ladevorgang erfasst (OCR)",                    27, false),
        MANUAL_LOG_OCR              ("Ladevorgang erfasst (OCR)",                     7, false),
        // Imports — per-log rewards never one-time; connection bonuses are one-time
        SPRITMONITOR_LOG            ("Ladevorgang importiert (Sprit-Monitor)",         2, false),
        SPRITMONITOR_CONNECTED      ("Sprit-Monitor Import",                          50, true),
        TESLA_HISTORY_LOG           ("Ladevorgang importiert (TeslaLogger)",           2, false),
        TESLA_LOGGER_CONNECTED      ("TeslaLogger Import",                            20, true),
        TESLA_DAILY_LOG             ("Ladevorgang importiert (Tesla Sync)",            5, false),
        TESLA_CONNECTED             ("Tesla Verbindung hinzugefügt",                  50, true),
        // Vehicle management — FIRST/SUBSEQUENT chosen by caller; image bonuses are one-time
        CAR_CREATED_FIRST           ("Fahrzeug hinzugefügt",                          20, false),
        CAR_CREATED_SUBSEQUENT      ("Fahrzeug hinzugefügt",                           5, false),
        IMAGE_UPLOADED              ("Erstes Auto-Bild hochgeladen",                  15, true),
        IMAGE_PUBLIC                ("Auto-Bild öffentlich geteilt",                  10, true),
        // Social — REFERRAL_WELCOME is one-time per referred user; REFERRAL_INVITED is count-capped by caller
        REFERRAL_INVITED            ("Freund eingeladen",                             100, false),
        REFERRAL_WELCOME            ("Willkommensbonus (eingeladen)",                  25, true),
        // Public API Upload — per-log reward
        API_UPLOAD_LOG              ("Ladevorgang importiert (API)",                    2, false),
        // Deductions
        LOG_DELETED_DEDUCTION       ("Ladevorgang gelöscht",                           0, false); // defaultAmount unused — caller passes dynamic negative amount

        private final String description;
        private final int defaultAmount;
        private final boolean oneTime;

        CoinEvent(String description, int defaultAmount, boolean oneTime) {
            this.description = description;
            this.defaultAmount = defaultAmount;
            this.oneTime = oneTime;
        }

        public String getDescription() {
            return description;
        }

        public int getDefaultAmount() {
            return defaultAmount;
        }

        public boolean isOneTime() {
            return oneTime;
        }
    }

    private final CoinLogRepository coinLogRepository;

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
        return awardCoins(userId, coinType, amount, actionDescription, null);
    }

    /**
     * Award coins with a source entity link (e.g. the EvLog ID that triggered this award).
     */
    public CoinLogResponse awardCoins(UUID userId, CoinType coinType, Integer amount,
                                      String actionDescription, @Nullable UUID sourceEntityId) {
        CoinLog newLog = CoinLog.createNew(userId, coinType, amount, actionDescription, sourceEntityId);
        CoinLog saved = coinLogRepository.save(newLog);
        return CoinLogResponse.fromDomain(saved);
    }

    /**
     * Award coins for a well-known CoinEvent, optionally linking to a source entity.
     * Uses the event's canonical description and default amount.
     *
     * For one-time events ({@link CoinEvent#isOneTime()} == true) the award is automatically
     * skipped if the user already has a coin_log entry for this event.
     *
     * @return coins actually awarded (0 when a one-time event was already claimed)
     */
    public int awardCoinsForEvent(UUID userId, CoinEvent event, @Nullable UUID sourceEntityId) {
        if (event.isOneTime() && hasEverReceivedCoinForAction(userId, event.getDescription())) {
            return 0;
        }
        awardCoins(userId, CoinType.ACHIEVEMENT_COIN, event.getDefaultAmount(),
                event.getDescription(), sourceEntityId);
        return event.getDefaultAmount();
    }

    /**
     * Returns the sum of all coin amounts linked to a source entity (e.g. an EvLog).
     * Used to calculate the deduction amount when a log is deleted.
     */
    public int sumCoinsForSourceEntity(UUID sourceEntityId) {
        return coinLogRepository.sumCoinsForSourceEntity(sourceEntityId);
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
