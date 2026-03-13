package com.evmonitor.application.tesla;

import com.evmonitor.application.CoinLogService;
import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that TESLA_CONNECTED is awarded exactly once, even when saveConnection()
 * is called multiple times (e.g. user re-connects with a new token).
 *
 * saveConnection() makes a real HTTP call to Tesla API which fails in tests — the
 * try-catch in TeslaApiService swallows the error and continues, so the coin logic still runs.
 */
class TeslaConnectedCoinIdempotencyTest extends AbstractIntegrationTest {

    @Autowired private TeslaApiService teslaApiService;

    @Test
    void teslaConnected_awardedOnlyOnce_onReconnect() {
        User user = createAndSaveUser("tesla-idempotency-" + System.nanoTime() + "@ev-monitor.net");

        // First connection — Tesla HTTP call fails silently, coin still awarded
        teslaApiService.saveConnection(user.getId(), "token-1", "vehicle-1", "My Tesla");

        // Second connection (re-connect with new token)
        teslaApiService.saveConnection(user.getId(), "token-2", "vehicle-1", "My Tesla");

        List<CoinLog> connectedCoins = coinLogRepository.findAllByUserId(user.getId()).stream()
                .filter(c -> CoinLogService.CoinEvent.TESLA_CONNECTED.getDescription().equals(c.getActionDescription()))
                .toList();

        assertThat(connectedCoins).hasSize(1);
        assertThat(connectedCoins.get(0).getAmount()).isEqualTo(CoinLogService.CoinEvent.TESLA_CONNECTED.getDefaultAmount());
    }
}
