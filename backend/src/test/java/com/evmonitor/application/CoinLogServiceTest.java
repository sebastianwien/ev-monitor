package com.evmonitor.application;

import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinLogRepository;
import com.evmonitor.domain.CoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CoinLogService — especially the new awardCoinsForEvent() and CoinEvent enum.
 * Core invariant: coin awards are idempotent for first-time events, and deletion creates the correct deduction.
 */
@ExtendWith(MockitoExtension.class)
class CoinLogServiceTest {

    @Mock
    private CoinLogRepository coinLogRepository;

    private CoinLogService coinLogService;

    private UUID userId;
    private UUID logId;

    @BeforeEach
    void setUp() {
        coinLogService = new CoinLogService(coinLogRepository);
        userId = UUID.randomUUID();
        logId = UUID.randomUUID();

        // Default: repository.save returns the domain object passed in (lenient to avoid UnnecessaryStubbingException)
        lenient().when(coinLogRepository.save(any(CoinLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // Datenqualitaet: einmal pro Ladung und Feld (Nachtrag-Modal)
    // -------------------------------------------------------------------------

    @Test
    void awardOncePerEntity_awardsTheFirstTimeForThisLog() {
        when(coinLogRepository.existsByUserIdAndActionDescriptionAndSourceEntityId(
                userId, CoinLogService.CoinEvent.PRICE_ADDED.getDescription(), logId)).thenReturn(false);

        int awarded = coinLogService.awardOncePerEntity(userId, CoinLogService.CoinEvent.PRICE_ADDED, logId);

        assertThat(awarded).isEqualTo(3);
        verify(coinLogRepository).save(any(CoinLog.class));
    }

    @Test
    void awardOncePerEntity_skipsWhenThisLogWasAlreadyRewarded() {
        when(coinLogRepository.existsByUserIdAndActionDescriptionAndSourceEntityId(
                userId, CoinLogService.CoinEvent.PRICE_ADDED.getDescription(), logId)).thenReturn(true);

        int awarded = coinLogService.awardOncePerEntity(userId, CoinLogService.CoinEvent.PRICE_ADDED, logId);

        assertThat(awarded).isZero();
        verify(coinLogRepository, never()).save(any(CoinLog.class));
    }

    @Test
    void catalog_listsEveryEventWithAmountAndWhetherAOneTimeBonusIsAlreadyClaimed() {
        when(coinLogRepository.existsByUserIdAndActionDescription(eq(userId), anyString()))
                .thenAnswer(inv -> CoinLogService.CoinEvent.CARD_CREATED.getDescription().equals(inv.getArgument(1)));

        var catalog = coinLogService.catalog(userId);

        var cardCreated = catalog.stream().filter(e -> e.event().equals("CARD_CREATED")).findFirst().orElseThrow();
        assertThat(cardCreated.amount()).isEqualTo(5);
        assertThat(cardCreated.oneTime()).isTrue();
        assertThat(cardCreated.claimed()).isTrue();
        var priceAdded = catalog.stream().filter(e -> e.event().equals("PRICE_ADDED")).findFirst().orElseThrow();
        assertThat(priceAdded.claimed()).isFalse();
        assertThat(priceAdded.amount()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // CoinEvent enum sanity
    // -------------------------------------------------------------------------

    @Test
    void coinEvent_manualLogFirst_has25Coins() {
        assertThat(CoinLogService.CoinEvent.MANUAL_LOG_FIRST.getDefaultAmount()).isEqualTo(25);
    }

    @Test
    void coinEvent_manualLogSubsequent_has5Coins() {
        assertThat(CoinLogService.CoinEvent.MANUAL_LOG_SUBSEQUENT.getDefaultAmount()).isEqualTo(5);
    }

    @Test
    void coinEvent_manualLogFirstOcr_has27Coins() {
        assertThat(CoinLogService.CoinEvent.MANUAL_LOG_FIRST_OCR.getDefaultAmount()).isEqualTo(27);
    }

    @Test
    void coinEvent_manualLogOcr_has7Coins() {
        assertThat(CoinLogService.CoinEvent.MANUAL_LOG_OCR.getDefaultAmount()).isEqualTo(7);
    }

    @Test
    void coinEvent_spritmonitorLog_has2Coins() {
        assertThat(CoinLogService.CoinEvent.SPRITMONITOR_LOG.getDefaultAmount()).isEqualTo(2);
    }

    @Test
    void coinEvent_teslaHistoryLog_has2Coins() {
        assertThat(CoinLogService.CoinEvent.TESLA_HISTORY_LOG.getDefaultAmount()).isEqualTo(2);
    }

    @Test
    void coinEvent_teslaDailyLog_has5Coins() {
        assertThat(CoinLogService.CoinEvent.TESLA_DAILY_LOG.getDefaultAmount()).isEqualTo(5);
    }

    @Test
    void coinEvent_teslaConnected_has50Coins() {
        assertThat(CoinLogService.CoinEvent.TESLA_CONNECTED.getDefaultAmount()).isEqualTo(50);
    }

    @Test
    void coinEvent_spritmonitorConnected_has50Coins() {
        assertThat(CoinLogService.CoinEvent.SPRITMONITOR_CONNECTED.getDefaultAmount()).isEqualTo(50);
    }

    // -------------------------------------------------------------------------
    // awardCoinsForEvent — saves correct amount and sourceEntityId
    // -------------------------------------------------------------------------

    @Test
    void awardCoinsForEvent_manualLogFirst_saves25CoinsWithSourceEntityId() {
        coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.MANUAL_LOG_FIRST, logId);

        ArgumentCaptor<CoinLog> captor = ArgumentCaptor.forClass(CoinLog.class);
        verify(coinLogRepository).save(captor.capture());
        CoinLog saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualTo(25);
        assertThat(saved.getSourceEntityId()).isEqualTo(logId);
        assertThat(saved.getCoinType()).isEqualTo(CoinType.ACHIEVEMENT_COIN);
        assertThat(saved.getActionDescription()).isEqualTo("Ladevorgang erfasst");
    }

    @Test
    void awardCoinsForEvent_spritmonitorLog_saves2CoinsWithSourceEntityId() {
        coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.SPRITMONITOR_LOG, logId);

        ArgumentCaptor<CoinLog> captor = ArgumentCaptor.forClass(CoinLog.class);
        verify(coinLogRepository).save(captor.capture());
        CoinLog saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualTo(2);
        assertThat(saved.getSourceEntityId()).isEqualTo(logId);
        assertThat(saved.getActionDescription()).isEqualTo("Ladevorgang importiert (Sprit-Monitor)");
    }

    @Test
    void awardCoinsForEvent_teslaHistoryLog_saves2CoinsWithSourceEntityId() {
        coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.TESLA_HISTORY_LOG, logId);

        ArgumentCaptor<CoinLog> captor = ArgumentCaptor.forClass(CoinLog.class);
        verify(coinLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(2);
        assertThat(captor.getValue().getSourceEntityId()).isEqualTo(logId);
    }

    @Test
    void awardCoinsForEvent_teslaDailyLog_saves5CoinsWithSourceEntityId() {
        coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.TESLA_DAILY_LOG, logId);

        ArgumentCaptor<CoinLog> captor = ArgumentCaptor.forClass(CoinLog.class);
        verify(coinLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(5);
        assertThat(captor.getValue().getSourceEntityId()).isEqualTo(logId);
    }

    @Test
    void awardCoinsForEvent_teslaConnected_nullSourceEntityId() {
        coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.TESLA_CONNECTED, null);

        ArgumentCaptor<CoinLog> captor = ArgumentCaptor.forClass(CoinLog.class);
        verify(coinLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(50);
        assertThat(captor.getValue().getSourceEntityId()).isNull();
    }

    // -------------------------------------------------------------------------
    // sumCoinsForSourceEntity — delegates to repository
    // -------------------------------------------------------------------------

    @Test
    void sumCoinsForSourceEntity_returnsRepositoryResult() {
        when(coinLogRepository.sumCoinsForSourceEntity(logId)).thenReturn(7);

        int result = coinLogService.sumCoinsForSourceEntity(logId);

        assertThat(result).isEqualTo(7);
        verify(coinLogRepository).sumCoinsForSourceEntity(logId);
    }

    @Test
    void sumCoinsForSourceEntity_returnsZeroWhenNoEntries() {
        when(coinLogRepository.sumCoinsForSourceEntity(logId)).thenReturn(0);

        assertThat(coinLogService.sumCoinsForSourceEntity(logId)).isZero();
    }

    // -------------------------------------------------------------------------
    // Deduction logic (LOG_DELETED_DEDUCTION used via awardCoins)
    // -------------------------------------------------------------------------

    @Test
    void logDeletion_deductionAmountIsNegativeSumOfAwardedCoins() {
        // Simulate: log had 5 coins awarded
        when(coinLogRepository.sumCoinsForSourceEntity(logId)).thenReturn(5);
        int coinSum = coinLogService.sumCoinsForSourceEntity(logId);

        // The caller (EvLogService) passes -coinSum to awardCoins
        coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, -coinSum,
                CoinLogService.CoinEvent.LOG_DELETED_DEDUCTION.getDescription(), logId);

        ArgumentCaptor<CoinLog> captor = ArgumentCaptor.forClass(CoinLog.class);
        verify(coinLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(-5);
        assertThat(captor.getValue().getSourceEntityId()).isEqualTo(logId);
        assertThat(captor.getValue().getActionDescription()).isEqualTo("Ladevorgang gelöscht");
    }

    @Test
    void logDeletion_noCoinEntryWhenSumIsZero() {
        // If no coins were ever awarded for this log, no deduction entry should be created
        when(coinLogRepository.sumCoinsForSourceEntity(logId)).thenReturn(0);
        int coinSum = coinLogService.sumCoinsForSourceEntity(logId);

        // EvLogService only calls awardCoins if coinSum > 0 — simulate that guard
        if (coinSum > 0) {
            coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, -coinSum,
                    CoinLogService.CoinEvent.LOG_DELETED_DEDUCTION.getDescription(), logId);
        }

        verify(coinLogRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // hasEverReceivedCoinForAction — first-time guard
    // -------------------------------------------------------------------------

    @Test
    void hasEverReceivedCoinForAction_returnsTrueWhenExists() {
        when(coinLogRepository.existsByUserIdAndActionDescription(userId, "Ladevorgang erfasst"))
                .thenReturn(true);

        assertThat(coinLogService.hasEverReceivedCoinForAction(userId, "Ladevorgang erfasst")).isTrue();
    }

    @Test
    void hasEverReceivedCoinForAction_returnsFalseWhenNotExists() {
        when(coinLogRepository.existsByUserIdAndActionDescription(userId, "Ladevorgang erfasst"))
                .thenReturn(false);

        assertThat(coinLogService.hasEverReceivedCoinForAction(userId, "Ladevorgang erfasst")).isFalse();
    }
}
