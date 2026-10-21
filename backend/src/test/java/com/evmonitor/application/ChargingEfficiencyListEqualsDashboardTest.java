package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression: Ladeliste und Dashboard rechnen denselben Ladewirkungsgrad. Früher griff ein
 * Spec-Faktor (vehicle_specification.charging_efficiency_ac) nur in der Ladeliste, nicht im
 * Dashboard, und dieselbe Ladung hatte zwei Verbräuche. Der Spec-Wert wird nicht mehr gelesen.
 */
class ChargingEfficiencyListEqualsDashboardTest extends AbstractIntegrationTest {

    @Autowired private EvLogService evLogService;
    @Autowired private EvLogStatisticsService evLogStatisticsService;

    @Test
    void perLogConsumption_inList_equalsDashboard_evenWhenSpecCarriesAnEfficiency() {
        User user = createAndSaveUser("list-eq-dash-" + System.nanoTime() + "@example.com");
        VehicleSpecification spec = createAndSaveSpec(CarBrand.CarModel.MODEL_3);
        spec = vehicleSpecificationRepository.save(spec.toBuilder()
                .chargingEfficiencyAc(new BigDecimal("0.50"))
                .chargingEfficiencyDc(new BigDecimal("0.50"))
                .build());
        Car car = carRepository.save(createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3)
                .toBuilder().vehicleSpecificationId(spec.getId()).build());

        LocalDateTime now = LocalDateTime.now();
        saveLog(car.getId(), 10000, now.minusDays(2), "20");
        UUID logB = saveLog(car.getId(), 10200, now.minusDays(1), "30");

        BigDecimal listValue = evLogService.getLogsForCar(car.getId(), user.getId(), null, null, null).stream()
                .filter(r -> r.id().equals(logB))
                .findFirst().orElseThrow()
                .consumptionKwhPer100km();
        BigDecimal dashboardValue = evLogStatisticsService
                .getStatistics(car.getId(), user.getId(), null, null, null)
                .avgConsumptionKwhPer100km();

        assertNotNull(listValue);
        assertNotNull(dashboardValue);
        assertEquals(0, listValue.compareTo(dashboardValue),
                "Ladeliste " + listValue + " gegen Dashboard " + dashboardValue);
    }

    private UUID saveLog(UUID carId, int odometerKm, LocalDateTime loggedAt, String kwh) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        evLogRepository.save(EvLog.builder()
                .id(id).carId(carId)
                .kwhCharged(new BigDecimal(kwh)).costEur(BigDecimal.TEN)
                .chargeDurationMinutes(180).maxChargingPowerKw(new BigDecimal("11"))
                .geohash("u33dc").odometerKm(odometerKm)
                .socAfterChargePercent(BigDecimal.valueOf(80))
                .loggedAt(loggedAt).dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true).chargingType(ChargingType.AC)
                .createdAt(now).updatedAt(now)
                .build());
        return id;
    }
}
