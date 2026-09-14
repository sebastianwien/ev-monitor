package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvLogServiceMergeTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("merge-test-" + System.currentTimeMillis() + "@ev-monitor.net");
        userId = user.getId();
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        carId = car.getId();
    }

    @Test
    void mergeLog_happyPath_mergesFieldsAndDeletesSource() {
        EvLog wallboxLog = buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null);
        EvLog vehicleLog = buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"),
                new BigDecimal("20.0"), new BigDecimal("80.0"));
        EvLog target = evLogRepository.save(wallboxLog);
        EvLog source = evLogRepository.save(vehicleLog);

        evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        EvLog merged = evLogRepository.findById(target.getId()).orElseThrow();
        assertThat(merged.getKwhCharged()).isEqualByComparingTo("22.5");
        assertThat(merged.getKwhAtVehicle()).isEqualByComparingTo("21.0");
        assertThat(merged.getSocBeforeChargePercent()).isEqualByComparingTo("20.0");
        assertThat(merged.getSocAfterChargePercent()).isEqualByComparingTo("80.0");
        assertThat(merged.getMeasurementType()).isEqualTo(EnergyMeasurementType.AT_CHARGER);
        assertThat(evLogRepository.findById(source.getId())).isEmpty();
    }

    @Test
    void mergeLog_targetNotFound_throwsNotFound() {
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(UUID.randomUUID(), source.getId(), userId, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void mergeLog_sourceNotFound_throwsNotFound() {
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), UUID.randomUUID(), userId, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void mergeLog_targetBelongsToDifferentUser_throwsForbidden() {
        User other = createAndSaveUser("other-" + System.currentTimeMillis() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(buildLog(otherCar.getId(), DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), source.getId(), userId, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void mergeLog_sourceBelongsToDifferentUser_throwsForbidden() {
        User other = createAndSaveUser("other2-" + System.currentTimeMillis() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(otherCar.getId(), DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), source.getId(), userId, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void mergeLog_preferSource_sourceValuesWin() {
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"),
                new BigDecimal("20.0"), new BigDecimal("80.0")));

        evLogService.mergeLog(target.getId(), source.getId(), userId, true);

        EvLog merged = evLogRepository.findById(target.getId()).orElseThrow();
        assertThat(merged.getKwhAtVehicle()).isEqualByComparingTo("21.0");
        assertThat(merged.getSocBeforeChargePercent()).isEqualByComparingTo("20.0");
        // kwhCharged from target because source has none
        assertThat(merged.getKwhCharged()).isEqualByComparingTo("22.5");
        assertThat(evLogRepository.findById(source.getId())).isEmpty();
    }

    @Test
    void mergeLog_preferSource_sourceOverridesTargetValues() {
        // Both logs have kwhCharged - source value should win when preferSource=true
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("19.0"), null, null, null));

        evLogService.mergeLog(target.getId(), source.getId(), userId, true);

        EvLog merged = evLogRepository.findById(target.getId()).orElseThrow();
        assertThat(merged.getKwhCharged()).isEqualByComparingTo("19.0");
    }

    @Test
    void mergeLog_preferTarget_targetValuesWin() {
        // Default behavior: existing test already covers this, but explicit call with false
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("19.0"), null, null, null));

        evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        EvLog merged = evLogRepository.findById(target.getId()).orElseThrow();
        assertThat(merged.getKwhCharged()).isEqualByComparingTo("22.5");
    }

    @Test
    void mergeLog_sameLog_throwsConflict() {
        EvLog log = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(log.getId(), log.getId(), userId, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void mergeLog_differentCars_throwsConflict() {
        Car secondCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(secondCar.getId(), DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), source.getId(), userId, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void mergeLog_returnsMergedLog() {
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));

        EvLog merged = evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        assertThat(merged.getId()).isEqualTo(target.getId());
        assertThat(merged.getKwhCharged()).isEqualByComparingTo("22.5");
        assertThat(merged.getKwhAtVehicle()).isEqualByComparingTo("21.0");
    }

    // ── Merge-Fenster ─────────────────────────────────────────────────────────
    // Zwei Logs duerfen nur zusammengefuehrt werden, wenn sie zeitlich nah genug
    // beieinander liegen - sonst waeren es zwei echte, getrennte Ladevorgaenge.

    @Test
    void mergeLog_withinWindow_succeeds() {
        // 14h Abstand: langsamer AC-Ladevorgang, muss zusammenfuehrbar bleiben
        EvLog target = evLogRepository.save(buildLogAt(carId, LocalDateTime.now().minusHours(20),
                DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null));
        EvLog source = evLogRepository.save(buildLogAt(carId, LocalDateTime.now().minusHours(6),
                DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0")));

        EvLog merged = evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        assertThat(merged.getKwhAtVehicle()).isEqualByComparingTo("21.0");
        assertThat(evLogRepository.findById(source.getId())).isEmpty();
    }

    @Test
    void mergeLog_outsideWindow_throwsConflict() {
        EvLog target = evLogRepository.save(buildLogAt(carId, LocalDateTime.now().minusHours(30),
                DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null));
        EvLog source = evLogRepository.save(buildLogAt(carId, LocalDateTime.now().minusHours(1),
                DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0")));

        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), source.getId(), userId, false))
                .isInstanceOf(ConflictException.class);
        assertThat(evLogRepository.findById(source.getId())).isPresent();
    }

    @Test
    void mergeLog_sequentialCurves_keepsBothConcatenated() throws Exception {
        // Zwei aufeinanderfolgende Ladungen: source deckt frueh (grosse Ladung), target spaet
        // (kleine Nachladung) ab. Nach dem Merge muss die ueberlebende Kurve BEIDE Bereiche
        // enthalten - die des geloeschten source darf nicht verloren gehen.
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.TESLA_LIVE, null, new BigDecimal("1.0"), null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.TESLA_LIVE, null, new BigDecimal("60.0"), null, null));
        evLogRepository.updatePowerCurvePoints(target.getId(), powerCurve(2_000_000L, 2_020_000L));
        evLogRepository.updatePowerCurvePoints(source.getId(), powerCurve(1_000_000L, 1_020_000L, 1_040_000L));

        evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        String merged = evLogRepository.findPowerCurvePointsJson(target.getId()).orElseThrow();
        assertThat(curvePointCount(merged)).isEqualTo(5);
        assertThat(firstTs(merged)).isEqualTo(1_000_000L);
        assertThat(lastTs(merged)).isEqualTo(2_020_000L);
    }

    @Test
    void mergeLog_overlappingCurves_collapsesToOneCurve() throws Exception {
        // Brutto/Netto: dieselbe physische Ladung, zwei Messungen mit denselben Zeitstempeln.
        // Der Merge darf keine doppelte (Zickzack-)Kurve erzeugen.
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.TESLA_LIVE, null, new BigDecimal("21.0"), null, null));
        evLogRepository.updatePowerCurvePoints(target.getId(), powerCurve(1_000_000L, 1_020_000L, 1_040_000L));
        evLogRepository.updatePowerCurvePoints(source.getId(), powerCurve(1_000_000L, 1_020_000L, 1_040_000L));

        evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        String merged = evLogRepository.findPowerCurvePointsJson(target.getId()).orElseThrow();
        assertThat(curvePointCount(merged)).isEqualTo(3);
    }

    @Test
    void mergeLog_onlySourceHasCurve_survivorInheritsIt() throws Exception {
        // Genau der gemeldete Fall: der ueberlebende target hat keine Kurve, die des
        // geloeschten source ist die einzige - sie muss auf den Survivor uebergehen.
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.TESLA_LIVE, null, new BigDecimal("60.0"), null, null));
        evLogRepository.updatePowerCurvePoints(source.getId(), powerCurve(1_000_000L, 1_020_000L, 1_040_000L));

        evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        String merged = evLogRepository.findPowerCurvePointsJson(target.getId()).orElseThrow();
        assertThat(curvePointCount(merged)).isEqualTo(3);
    }

    @Test
    void mergeLog_socCurves_mergedToo() throws Exception {
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("1.0"), null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("60.0"), null, null));
        evLogRepository.updateSocCurvePoints(target.getId(), socCurve(2_000_000L));
        evLogRepository.updateSocCurvePoints(source.getId(), socCurve(1_000_000L, 1_020_000L));

        evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        String merged = evLogRepository.findOwnerIdAndPowerCurveJson(target.getId()).orElseThrow().socCurvePointsJson();
        assertThat(curvePointCount(merged)).isEqualTo(3);
    }

    private static String powerCurve(long... tsValues) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tsValues.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"ts\":").append(tsValues[i]).append(",\"kw\":50.0,\"soc\":50.0}");
        }
        return sb.append("]").toString();
    }

    private static String socCurve(long... tsValues) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tsValues.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"ts\":").append(tsValues[i]).append(",\"soc\":50.0}");
        }
        return sb.append("]").toString();
    }

    private static int curvePointCount(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).size();
    }

    private static long firstTs(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get(0).get("ts").asLong();
    }

    private static long lastTs(String json) throws Exception {
        var arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        return arr.get(arr.size() - 1).get("ts").asLong();
    }

    private EvLog buildLogAt(UUID carId, LocalDateTime loggedAt, DataSource dataSource,
                             BigDecimal kwhCharged, BigDecimal kwhAtVehicle) {
        return buildLog(carId, dataSource, kwhCharged, kwhAtVehicle, null, null)
                .toBuilder().loggedAt(loggedAt).build();
    }

    private EvLog buildLog(UUID carId, DataSource dataSource, BigDecimal kwhCharged, BigDecimal kwhAtVehicle,
                            BigDecimal socStart, BigDecimal socEnd) {
        EnergyMeasurementType measurementType = dataSource.measurementType();
        // WALLBOX_GOE returns AT_CHARGER but kwhAtVehicle path needs AT_VEHICLE
        if (kwhAtVehicle != null && kwhCharged == null) {
            measurementType = EnergyMeasurementType.AT_VEHICLE;
        }
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhCharged)
                .kwhAtVehicle(kwhAtVehicle)
                .socBeforeChargePercent(socStart)
                .socAfterChargePercent(socEnd)
                .loggedAt(LocalDateTime.now().minusHours(1))
                .dataSource(dataSource)
                .measurementType(measurementType)
                .includeInStatistics(true)
                .build();
    }
}
