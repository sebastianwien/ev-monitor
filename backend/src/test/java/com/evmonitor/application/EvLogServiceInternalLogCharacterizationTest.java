package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Charakterisierung von Tür 2 ({@link EvLogService#createInternalLog}, Connectors-Live-Pfade) vor
 * dem Umbau auf ein gemeinsames IngestGateway (Herstellerarchitektur R2). Beschreibt das heutige
 * Verhalten; wer eine Regel bewusst ändert, passt den Test mit an.
 */
class EvLogServiceInternalLogCharacterizationTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    private User user;
    /** 75 kWh Netto-Kapazität ohne Degradation. */
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("ch-door2-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    @Test
    void foreignCar_isRejected_andNothingIsStored() {
        User stranger = createAndSaveUser("ch-stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");

        assertThatThrownBy(() -> evLogService.createInternalLog(request(stranger.getId(), "TESLA_LIVE", at(10))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Car does not belong to user");
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();
    }

    @Test
    void unknownDataSource_isStoredAsWallboxOcpp() {
        evLogService.createInternalLog(request(user.getId(), "NOT_A_SOURCE", at(10)));

        assertThat(singleLog().getDataSource()).isEqualTo(DataSource.WALLBOX_OCPP);
    }

    @Test
    void missingDataSource_isStoredAsWallboxOcpp() {
        evLogService.createInternalLog(request(user.getId(), null, at(10)));

        assertThat(singleLog().getDataSource()).isEqualTo(DataSource.WALLBOX_OCPP);
    }

    /** R15 ohne saubere Vorladungen: Fallback SoH-bereinigte Kapazität / 100 (R2f). */
    @Test
    void missedStart_withoutCleanCharges_derivesSocBeforeFromEffectiveCapacity() {
        car = carRepository.save(car.toBuilder().batteryDegradationPercent(new BigDecimal("20")).build());

        evLogService.createInternalLog(missedStart(at(10), "15.0", "80"));

        // 75 kWh bei 20 % Degradation = 60 kWh, 15 kWh / 0,6 kWh je Punkt = 25 Punkte → 80 - 25
        assertThat(singleLog().getSocBeforeChargePercent()).isEqualByComparingTo("55");
    }

    /** R15 mit sauberen Vorladungen: Median kWh je SoC-Punkt aus eigenen AT_VEHICLE-Ladungen. */
    @Test
    void missedStart_withCleanCharges_derivesSocBeforeFromMedianOfOwnCharges() {
        // 25 kWh für 20 → 70 = 0,5 kWh je Punkt
        evLogService.createInternalLog(withSoc(request(user.getId(), "SMARTCAR_LIVE", at(8)), "25.0", "20", "70", null));

        EvLogResponse missed = evLogService.createInternalLog(missedStart(at(10), "15.0", "80"));

        // 15 kWh / 0,5 kWh je Punkt = 30 Punkte → 80 - 30
        assertThat(evLogRepository.findById(missed.id()).orElseThrow().getSocBeforeChargePercent())
                .isEqualByComparingTo("50");
    }

    @Test
    void socStartNotMissed_leavesSocBeforeEmpty() {
        evLogService.createInternalLog(withSoc(request(user.getId(), "SMARTCAR_LIVE", at(10)), "15.0", null, "80", false));

        assertThat(singleLog().getSocBeforeChargePercent()).isNull();
    }

    @Test
    void coins_areAwardedOnlyForTeslaSources() {
        EvLogResponse live = evLogService.createInternalLog(request(user.getId(), "TESLA_LIVE", at(8)));
        EvLogResponse fleet = evLogService.createInternalLog(request(user.getId(), "TESLA_FLEET_IMPORT", at(9)));
        evLogService.createInternalLog(request(user.getId(), "SMARTCAR_LIVE", at(10)));
        evLogService.createInternalLog(request(user.getId(), "WALLBOX_GOE", at(11)));

        assertThat(coinLogRepository.findAllByUserId(user.getId())).extracting(CoinLog::getSourceEntityId)
                .containsExactlyInAnyOrder(live.id(), fleet.id());
    }

    /** Ein vom Nutzer gelöschter Vorgang wird vom nächsten Live-Sync nicht neu angelegt. */
    @Test
    void softDeletedLog_blocksRecreationThroughTheDoor() {
        EvLogResponse first = evLogService.createInternalLog(request(user.getId(), "SMARTCAR_LIVE", at(10)));
        evLogRepository.softDelete(first.id());

        EvLogResponse again = evLogService.createInternalLog(request(user.getId(), "SMARTCAR_LIVE", at(10)));

        assertThat(again).isNull();
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();
    }

    @Test
    void powerCurve_isPersistedThroughTheDoor() {
        String curve = "[{\"ts\":1757498400000,\"kw\":11.0}]";
        EvLogResponse saved = evLogService.createInternalLog(withCurves(request(user.getId(), "TESLA_LIVE", at(10)), curve, null));

        assertThat(evLogRepository.findPowerCurvePointsJson(saved.id())).isPresent();
    }

    @Test
    void socCurve_isPersistedThroughTheDoor() {
        String curve = "[{\"ts\":1757498400000,\"soc\":40.0}]";
        EvLogResponse saved = evLogService.createInternalLog(withCurves(request(user.getId(), "SMARTCAR_LIVE", at(10)), null, curve));

        EvLog reloaded = evLogRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.isHasSocCurve()).isTrue();
        assertThat(reloaded.isHasPowerCurve()).isFalse();
    }

    private EvLog singleLog() {
        var logs = evLogRepository.findAllByCarId(car.getId());
        assertThat(logs).hasSize(1);
        return logs.get(0);
    }

    private static LocalDateTime at(int hour) {
        return LocalDateTime.of(2026, 9, 10, hour, 0);
    }

    private InternalEvLogRequest missedStart(LocalDateTime loggedAt, String kwh, String socAfter) {
        return withSoc(request(user.getId(), "SMARTCAR_LIVE", loggedAt), kwh, null, socAfter, true);
    }

    private InternalEvLogRequest request(UUID userId, String dataSource, LocalDateTime loggedAt) {
        return new InternalEvLogRequest(car.getId(), userId, new BigDecimal("20.0"), 60, loggedAt, null,
                null, null, dataSource, null, "AC", false, null, null, null, 15.0, null,
                null, null, null, null, null, null, null, null);
    }

    private static InternalEvLogRequest withSoc(InternalEvLogRequest r, String kwh, String socBefore, String socAfter,
                                                Boolean socStartMissed) {
        return new InternalEvLogRequest(r.carId(), r.userId(), new BigDecimal(kwh), r.chargeDurationMinutes(),
                r.loggedAt(), r.geohash(), null, null, r.dataSource(), null, r.chargingType(), false, null,
                socBefore == null ? null : new BigDecimal(socBefore), new BigDecimal(socAfter),
                r.temperatureCelsius(), null, null, null, null, null, null, null, socStartMissed, null);
    }

    private static InternalEvLogRequest withCurves(InternalEvLogRequest r, String powerCurve, String socCurve) {
        return new InternalEvLogRequest(r.carId(), r.userId(), r.kwhCharged(), r.chargeDurationMinutes(),
                r.loggedAt(), r.geohash(), null, null, r.dataSource(), null, r.chargingType(), false, null,
                null, null, r.temperatureCelsius(), null, null, null, powerCurve, socCurve, null, null, null, null);
    }
}
