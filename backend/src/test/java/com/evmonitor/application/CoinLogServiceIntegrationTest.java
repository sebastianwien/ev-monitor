package com.evmonitor.application;

import com.evmonitor.application.manualimport.ManualImportService;
import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.application.spritmonitor.SpritMonitorFuelingDTO;
import com.evmonitor.application.spritmonitor.SpritMonitorImportService;
import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for coin award logic across all import paths.
 *
 * Tests are at the service layer (no HTTP) — all relevant services are injected
 * directly, real DB is used. This is the single source of truth for "which action
 * awards how many coins, with which sourceEntityId".
 *
 * HTTP-level coin tests (response body, API wiring) live in CoinRewardIntegrationTest.
 */
class CoinLogServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SpritMonitorImportService spritMonitorImportService;
    @Autowired private ManualImportService manualImportService;
    @Autowired private EvLogService evLogService;
    @Autowired private CoinLogService coinLogService;

    @MockitoBean private SpritMonitorClient spritMonitorClient;

    private static final int KWH_UNIT = 5;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("coinservice-" + System.nanoTime() + "@ev-monitor.net");
        car  = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    // ── SpritMonitor import ───────────────────────────────────────────────────

    @Test
    void spritMonitor_awards2CoinsPerImportedLog_WithSourceEntityId() {
        mockFuelings(
                fueling("10.01.2024", "50.0"),
                fueling("15.01.2024", "40.0"),
                fueling("20.01.2024", "30.0")
        );

        spritMonitorImportService.importFuelings(user.getId(), "token", 123, 1, car.getId());

        List<CoinLog> perLogCoins = perLogCoins(CoinLogService.CoinEvent.SPRITMONITOR_LOG.getDescription());
        assertThat(perLogCoins).hasSize(3);
        perLogCoins.forEach(coin -> {
            assertThat(coin.getAmount()).isEqualTo(2);
            assertThat(coin.getSourceEntityId()).isNotNull();
        });

        // Each sourceEntityId references an actual EvLog
        List<UUID> logIds = evLogRepository.findAllByCarId(car.getId()).stream().map(EvLog::getId).toList();
        perLogCoins.forEach(coin ->
                assertThat(logIds).contains(coin.getSourceEntityId()));
    }

    @Test
    void spritMonitor_awards50OnceBonus_OnFirstImport() {
        mockFuelings(fueling("10.01.2024", "50.0"));

        spritMonitorImportService.importFuelings(user.getId(), "token", 123, 1, car.getId());

        List<CoinLog> bonus = perLogCoins(CoinLogService.CoinEvent.SPRITMONITOR_CONNECTED.getDescription());
        assertThat(bonus).hasSize(1);
        assertThat(bonus.get(0).getAmount()).isEqualTo(50);
        assertThat(bonus.get(0).getSourceEntityId()).isNull();
    }

    @Test
    void spritMonitor_doesNotAward50BonusAgain_OnSecondImport() {
        when(spritMonitorClient.getFuelings(eq("token"), eq(123), any()))
                .thenReturn(List.of(fueling("10.01.2024", "50.0")))
                .thenReturn(List.of(fueling("11.01.2024", "40.0")));

        spritMonitorImportService.importFuelings(user.getId(), "token", 123, 1, car.getId());
        spritMonitorImportService.importFuelings(user.getId(), "token", 123, 1, car.getId());

        assertThat(perLogCoins(CoinLogService.CoinEvent.SPRITMONITOR_CONNECTED.getDescription())).hasSize(1);
    }

    @Test
    void spritMonitor_doesNotAwardCoins_ForSkippedFuelings() {
        // One liter fueling (skipped) + one kWh fueling (imported)
        mockFuelings(
                new SpritMonitorFuelingDTO("10.01.2024", new BigDecimal("50.0"), 1 /* Liter */, null, new BigDecimal("12.50"), 60, null, null, null, null, null, null),
                fueling("15.01.2024", "40.0")
        );

        spritMonitorImportService.importFuelings(user.getId(), "token", 123, 1, car.getId());

        assertThat(perLogCoins(CoinLogService.CoinEvent.SPRITMONITOR_LOG.getDescription())).hasSize(1);
    }

    // ── Manual import ─────────────────────────────────────────────────────────

    @Test
    void manualImport_awards2CoinsPerImportedLog_WithSourceEntityId() {
        String csv = """
                date,kwh,odometer_km,soc_after
                2025-08-20T10:00:00,24.5,12000,80
                2025-09-01T10:00:00,18.0,12800,72
                2025-10-01T10:00:00,21.0,13500,85
                """;

        manualImportService.importData(user.getId(), car.getId(), "csv", csv);

        List<CoinLog> perLogCoins = perLogCoins(CoinLogService.CoinEvent.API_UPLOAD_LOG.getDescription());
        assertThat(perLogCoins).hasSize(3);
        perLogCoins.forEach(coin -> {
            assertThat(coin.getAmount()).isEqualTo(2);
            assertThat(coin.getSourceEntityId()).isNotNull();
        });

        List<UUID> logIds = evLogRepository.findAllByCarId(car.getId()).stream().map(EvLog::getId).toList();
        perLogCoins.forEach(coin ->
                assertThat(logIds).contains(coin.getSourceEntityId()));
    }

    @Test
    void manualImport_doesNotAwardCoins_ForSkippedRows() {
        String csv = """
                date,kwh,odometer_km,soc_after
                2025-08-20T10:00:00,24.5,12000,80
                ,18.0,12800,72
                """;

        manualImportService.importData(user.getId(), car.getId(), "csv", csv);

        assertThat(perLogCoins(CoinLogService.CoinEvent.API_UPLOAD_LOG.getDescription())).hasSize(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<CoinLog> perLogCoins(String actionDescription) {
        return coinLogRepository.findAllByUserId(user.getId()).stream()
                .filter(c -> actionDescription.equals(c.getActionDescription()))
                .toList();
    }

    private void mockFuelings(SpritMonitorFuelingDTO... fuelings) {
        when(spritMonitorClient.getFuelings(eq("token"), eq(123), any()))
                .thenReturn(List.of(fuelings));
    }

    private SpritMonitorFuelingDTO fueling(String date, String kwh) {
        return new SpritMonitorFuelingDTO(date, new BigDecimal(kwh), KWH_UNIT,
                null, new BigDecimal("10.00"), 45, null, null, null, null, null, null);
    }
}
