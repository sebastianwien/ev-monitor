package com.evmonitor.application;

import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code EU_DATA_ACT_SYNC} kommt über beide Türen: Historie und Feed über Tür 1
 * ({@code /api/internal/eu-data-act/import}), laufende Drops über Tür 2 ({@code /api/internal/logs}).
 * Die Regeln hängen heute an der Tür, nicht an der Quelle. Deshalb ist die IngestPolicy in R2 nach
 * Quelle und Tür geschlüsselt (Entscheidung 25.09.2026); zusammengelegt wird erst, wenn die Quelle
 * nur noch eine Tür nutzt.
 */
class EuDataActSyncBothDoorsCharacterizationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicApiImportService importService;

    @Autowired
    private EvLogService evLogService;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("ch-sync-doors-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("40.0"), null, 30, null, null, null,
                new BigDecimal("80"), LocalDateTime.of(2026, 9, 1, 8, 0), ChargingType.AC,
                RouteType.HIGHWAY, TireType.WINTER, false, null));
    }

    @Test
    void importBatchDoor_paysApiCoins_andDoesNotInherit() {
        var result = importService.importSessions(user.getId(), new PublicApiSessionRequest(car.getId(), List.of(
                new PublicApiSessionRequest.SessionEntry("2026-09-10T10:00:00Z", 20.0, null, null, null, null, null,
                        null, null, null, null, null, null, null, false, null, null, null))), DataSource.EU_DATA_ACT_SYNC);

        EvLog imported = evLogRepository.findById(result.results().get(0).id()).orElseThrow();
        assertThat(imported.getTireType()).isNull();
        assertThat(imported.getRouteType()).isNull();
        assertThat(coinLogRepository.findAllByUserId(user.getId())).extracting(CoinLog::getSourceEntityId)
                .containsExactly(imported.getId());
    }

    @Test
    void connectorPushDoor_paysNoCoins_andInherits() {
        EvLogResponse created = evLogService.createInternalLog(new InternalEvLogRequest(car.getId(), user.getId(),
                new BigDecimal("20.0"), 60, LocalDateTime.of(2026, 9, 10, 10, 0), null, null, null,
                DataSource.EU_DATA_ACT_SYNC.name(), null, "AC", false, null, null, null, null, null,
                null, null, null, null, null, null, null, null));

        EvLog imported = evLogRepository.findById(created.id()).orElseThrow();
        assertThat(imported.getTireType()).isEqualTo(TireType.WINTER);
        assertThat(imported.getRouteType()).isEqualTo(RouteType.HIGHWAY);
        assertThat(coinLogRepository.findAllByUserId(user.getId())).isEmpty();
    }
}
