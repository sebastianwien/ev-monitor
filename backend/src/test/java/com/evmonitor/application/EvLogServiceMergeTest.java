package com.evmonitor.application;

import com.evmonitor.domain.*;
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
    void mergeLog_targetNotFound_throwsIllegalArgument() {
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(UUID.randomUUID(), source.getId(), userId, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeLog_sourceNotFound_throwsIllegalArgument() {
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), UUID.randomUUID(), userId, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeLog_targetBelongsToDifferentUser_throwsIllegalArgument() {
        User other = createAndSaveUser("other-" + System.currentTimeMillis() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(buildLog(otherCar.getId(), DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(carId, DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), source.getId(), userId, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeLog_sourceBelongsToDifferentUser_throwsIllegalArgument() {
        User other = createAndSaveUser("other2-" + System.currentTimeMillis() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(otherCar.getId(), DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), source.getId(), userId, false))
                .isInstanceOf(IllegalArgumentException.class);
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
    void mergeLog_sameLog_throwsIllegalArgument() {
        EvLog log = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(log.getId(), log.getId(), userId, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeLog_differentCars_throwsIllegalArgument() {
        Car secondCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(buildLog(carId, DataSource.WALLBOX_GOE, new BigDecimal("22.5"), null, null, null));
        EvLog source = evLogRepository.save(buildLog(secondCar.getId(), DataSource.SMARTCAR_LIVE, null, new BigDecimal("21.0"), null, null));
        assertThatThrownBy(() -> evLogService.mergeLog(target.getId(), source.getId(), userId, false))
                .isInstanceOf(IllegalArgumentException.class);
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
