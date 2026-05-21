package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for SpecChargingEfficiencyJob.
 *
 * The job queries ev_log data to compute the median implicit charging efficiency per
 * vehicle_specification and charging_type (DC/AC), then persists results back into
 * charging_efficiency_dc / charging_efficiency_ac.
 *
 * Efficiency formula per log:
 *   (soc_after - soc_start) / 100 * net_battery_capacity_kwh / kwh_charged
 *
 * Minimum 5 qualifying logs required; SoC delta >= 75%; only DC/AC (not UNKNOWN).
 */
class SpecChargingEfficiencyJobTest extends AbstractIntegrationTest {

    @Autowired
    private SpecChargingEfficiencyJob job;

    @Autowired
    private JpaVehicleSpecificationRepository jpaSpecRepo;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = createAndSaveUser("spec-eff-" + System.currentTimeMillis() + "@example.com").getId();
    }

    // ------------------------------------------------------------------
    // Helper: create a VehicleSpecificationEntity with net_battery_capacity_kwh
    // ------------------------------------------------------------------

    private VehicleSpecificationEntity createSpec(BigDecimal netBatteryKwh) {
        VehicleSpecificationEntity entity = new VehicleSpecificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setCarBrand("BMW");
        entity.setCarModel("TEST_MODEL_" + UUID.randomUUID().toString().substring(0, 8));
        entity.setBatteryCapacityKwh(netBatteryKwh);
        entity.setNetBatteryCapacityKwh(netBatteryKwh);
        entity.setOfficialRangeKm(new BigDecimal("400.0"));
        entity.setOfficialConsumptionKwhPer100km(new BigDecimal("17.0"));
        entity.setWltpType("COMBINED");
        entity.setRatingSource("WLTP");
        entity.setVariantName("");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return jpaSpecRepo.save(entity);
    }

    private Car createCarForSpec(UUID specId) {
        // Use a model whose spec we can control via vehicleSpecificationId
        // We create a Car directly via the domain builder so we can set vehicleSpecificationId
        Car car = Car.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .model(CarBrand.CarModel.MODEL_3)
                .year(2024)
                .licensePlate("TEST-" + UUID.randomUUID().toString().substring(0, 6))
                .trim("Standard")
                .customNetCapacityKwh(new BigDecimal("75.0"))
                .powerKw(new BigDecimal("150.0"))
                .status(CarStatus.ACTIVE)
                .vehicleSpecificationId(specId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return carRepository.save(car);
    }

    /**
     * Creates an EvLog with specific SoC start/after, kwhCharged, charging type.
     * includeInStatistics = true by default.
     */
    private EvLog createLog(UUID carId, int socStart, int socAfter, BigDecimal kwhCharged,
                             ChargingType chargingType) {
        return createLog(carId, socStart, socAfter, kwhCharged, chargingType, true);
    }

    private EvLog createLog(UUID carId, int socStart, int socAfter, BigDecimal kwhCharged,
                             ChargingType chargingType, boolean includeInStatistics) {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhCharged)
                .costEur(new BigDecimal("10.0"))
                .chargeDurationMinutes(45)
                .geohash("u33d1")
                .odometerKm(50000)
                .socAfterChargePercent(new BigDecimal(socAfter))
                .socBeforeChargePercent(new BigDecimal(socStart))
                .loggedAt(LocalDateTime.now())
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(includeInStatistics)
                .chargingType(chargingType)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return evLogRepository.save(log);
    }

    // ------------------------------------------------------------------
    // Test 1: 6 DC logs with SoC delta >= 60% → efficiency_dc is set
    // ------------------------------------------------------------------

    @Test
    void sixDcLogs_setsChargingEfficiencyDc() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        // delta = 80-5 = 75% (≥ threshold); battery_used = 75/100 * 75 = 56.25 kWh
        // efficiency = 56.25 / 60.0 = 0.9375
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 5, 80, new BigDecimal("60.0"), ChargingType.DC);
        }

        String summary = job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc())
                .isNotNull()
                .isEqualByComparingTo(new BigDecimal("0.9375"));
        assertThat(updated.getChargingEfficiencyAc()).isNull();
        assertThat(summary).contains("DC:");
    }

    // ------------------------------------------------------------------
    // Test 2: Only 4 logs → below minimum → efficiency stays null
    // ------------------------------------------------------------------

    @Test
    void fourDcLogs_belowMinimum_efficiencyRemainsNull() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        for (int i = 0; i < 4; i++) {
            createLog(car.getId(), 20, 90, new BigDecimal("52.5"), ChargingType.DC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNull();
    }

    // ------------------------------------------------------------------
    // Test 3: DC and AC separately computed and independent
    // ------------------------------------------------------------------

    @Test
    void dcAndAcComputedSeparatelyAndIndependently() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        // delta = 80-5 = 75% (≥ threshold); battery_used = 75/100 * 75 = 56.25 kWh
        // DC: efficiency = 56.25 / 60.0 = 0.9375
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 5, 80, new BigDecimal("60.0"), ChargingType.DC);
        }
        // AC: efficiency = 56.25 / 75.0 = 0.75
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 5, 80, new BigDecimal("75.0"), ChargingType.AC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc())
                .isNotNull()
                .isEqualByComparingTo(new BigDecimal("0.9375"));
        assertThat(updated.getChargingEfficiencyAc())
                .isNotNull()
                .isEqualByComparingTo(new BigDecimal("0.7500"));
    }

    // ------------------------------------------------------------------
    // Test 4: SoC delta < 75% → logs ignored
    // ------------------------------------------------------------------

    @Test
    void socDeltaBelow75_logsIgnored() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        // SoC delta = 74 (below threshold of 75)
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 20, 94, new BigDecimal("44.25"), ChargingType.DC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNull();
    }

    // ------------------------------------------------------------------
    // Test 5: UNKNOWN charging type → ignored, doesn't count toward minimum
    // ------------------------------------------------------------------

    @Test
    void unknownChargingType_ignored() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        // 6 UNKNOWN logs - chargeDurationMinutes = null ensures no inference of AC/DC
        for (int i = 0; i < 6; i++) {
            EvLog log = EvLog.builder()
                    .id(UUID.randomUUID())
                    .carId(car.getId())
                    .kwhCharged(new BigDecimal("52.5"))
                    .costEur(new BigDecimal("10.0"))
                    .chargeDurationMinutes(null) // prevent charging type inference
                    .geohash("u33d1")
                    .odometerKm(50000)
                    .socAfterChargePercent(new BigDecimal("90"))
                    .socBeforeChargePercent(new BigDecimal("20"))
                    .loggedAt(LocalDateTime.now())
                    .dataSource(DataSource.USER_LOGGED)
                    .includeInStatistics(true)
                    .chargingType(ChargingType.UNKNOWN)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            evLogRepository.save(log);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNull();
        assertThat(updated.getChargingEfficiencyAc()).isNull();
    }

    // ------------------------------------------------------------------
    // Test 6: include_in_statistics = false → ignored
    // ------------------------------------------------------------------

    @Test
    void excludedFromStatistics_ignored() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 20, 90, new BigDecimal("52.5"), ChargingType.DC, false);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNull();
    }

    // ------------------------------------------------------------------
    // Test 7: spec without net_battery_capacity_kwh → ignored
    // ------------------------------------------------------------------

    @Test
    void specWithoutNetBatteryCapacity_ignored() {
        VehicleSpecificationEntity entity = new VehicleSpecificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setCarBrand("BMW");
        entity.setCarModel("NO_NET_CAP_" + UUID.randomUUID().toString().substring(0, 8));
        entity.setBatteryCapacityKwh(new BigDecimal("75.0"));
        entity.setNetBatteryCapacityKwh(null); // explicitly null
        entity.setOfficialRangeKm(new BigDecimal("400.0"));
        entity.setOfficialConsumptionKwhPer100km(new BigDecimal("17.0"));
        entity.setWltpType("COMBINED");
        entity.setRatingSource("WLTP");
        entity.setVariantName("");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        VehicleSpecificationEntity spec = jpaSpecRepo.save(entity);

        Car car = createCarForSpec(spec.getId());
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 20, 90, new BigDecimal("52.5"), ChargingType.DC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNull();
    }

    // ------------------------------------------------------------------
    // Test 8: existing DC value preserved when new data only has AC
    // ------------------------------------------------------------------

    @Test
    void existingDcValuePreserved_whenOnlyAcDataAvailable() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        // Pre-set a DC efficiency value
        spec.setChargingEfficiencyDc(new BigDecimal("0.9500"));
        jpaSpecRepo.save(spec);

        Car car = createCarForSpec(spec.getId());
        // Only AC logs, enough to update AC (delta = 80-5 = 75%)
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 5, 80, new BigDecimal("60.0"), ChargingType.AC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        // DC must remain unchanged
        assertThat(updated.getChargingEfficiencyDc())
                .isEqualByComparingTo(new BigDecimal("0.9500"));
        // AC must be updated
        assertThat(updated.getChargingEfficiencyAc()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Test 9: verify median value with concrete numbers
    //
    // 6 DC logs with varying kwhCharged, netBattery = 75 kWh
    // soc_start = 10%, soc_after = 90%  → delta = 80% → battery_used = 60 kWh
    // efficiencies: 60/60=1.000, 60/65=0.9231, 60/70=0.8571, 60/75=0.8000, 60/80=0.7500, 60/85=0.7059
    // All in [0.5, 1.0] ✓
    // sorted: 0.7059, 0.7500, 0.8000, 0.8571, 0.9231, 1.0000
    // median (6 elements, PERCENTILE_CONT(0.5)): avg of 3rd and 4th = (0.8000 + 0.8571) / 2 = 0.8286
    // ------------------------------------------------------------------

    @Test
    void medianCalculatedCorrectlyWithConcreteValues() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        // delta = 90-10 = 80% (≥ threshold); battery_used = 80/100 * 75 = 60 kWh
        int socStart = 10;
        int socAfter = 90;
        BigDecimal[] kwhValues = {
            new BigDecimal("60.0"),
            new BigDecimal("65.0"),
            new BigDecimal("70.0"),
            new BigDecimal("75.0"),
            new BigDecimal("80.0"),
            new BigDecimal("85.0")
        };
        for (BigDecimal kwh : kwhValues) {
            createLog(car.getId(), socStart, socAfter, kwh, ChargingType.DC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNotNull();
        // 60/70 = 0.85714... and 60/75 = 0.80000 → avg = 0.82857
        assertThat(updated.getChargingEfficiencyDc().doubleValue())
                .isCloseTo(0.8286, within(0.0002));
    }

    // ------------------------------------------------------------------
    // Test 10: implausible efficiency > 1.0 → field stays null (not persisted)
    //
    // net_battery_capacity_kwh = 75 kWh, soc delta = 80% → battery_used = 60 kWh
    // kwh_charged = 40.0 → efficiency = 60 / 40.0 = 1.5 (> 1.0)
    // → value must be rejected, charging_efficiency_dc stays null
    // ------------------------------------------------------------------

    @Test
    void implausibleEfficiencyAboveOne_fieldRemainsNull() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        // delta = 90-10 = 80% (≥ threshold); battery_used = 60 kWh
        // efficiency = 60 / 40.0 = 1.5 → implausible, rejected
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 10, 90, new BigDecimal("40.0"), ChargingType.DC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNull();
    }

    // Test 11: efficiency exactly 1.0 → rejected (circular/zirkuläre Daten)
    //
    // net_battery_capacity_kwh = 75 kWh, soc delta = 80% → battery_used = 60 kWh
    // kwh_charged = 60.0 → efficiency = 60 / 60.0 = 1.0 → rejected
    // ------------------------------------------------------------------

    @Test
    void efficiencyExactlyOne_fieldRemainsNull() {
        VehicleSpecificationEntity spec = createSpec(new BigDecimal("75.0"));
        Car car = createCarForSpec(spec.getId());

        // delta = 90-10 = 80% → battery_used = 60 kWh; kwh_charged = 60 → efficiency = 1.0
        for (int i = 0; i < 6; i++) {
            createLog(car.getId(), 10, 90, new BigDecimal("60.0"), ChargingType.DC);
        }

        job.run();

        VehicleSpecificationEntity updated = jpaSpecRepo.findById(spec.getId()).orElseThrow();
        assertThat(updated.getChargingEfficiencyDc()).isNull();
    }
}
