package com.evmonitor.application.recap;

import com.evmonitor.application.EvLogStatisticsResponse;
import com.evmonitor.application.EvLogStatisticsResponse.LocationSplit;
import com.evmonitor.application.EvLogStatisticsService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.external.FuelPriceService;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository.RecapCandidate;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyRecapServiceTest {

    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    private final EvLogStatisticsService statisticsService = mock(EvLogStatisticsService.class);
    private final EvLogRepository evLogRepository = mock(EvLogRepository.class);
    private final CarRepository carRepository = mock(CarRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FuelPriceService fuelPriceService = mock(FuelPriceService.class);

    private final MonthlyRecapService service = new MonthlyRecapService(
            statisticsService, evLogRepository, carRepository, userRepository, fuelPriceService);

    private User user;
    private Car car;
    private final List<EvLog> logs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        user = TestDataBuilder.createTestUser("ihle@example.com").toBuilder().id(UUID.randomUUID()).build();
        car = TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75))
                .toBuilder().id(UUID.randomUUID()).build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
        when(evLogRepository.findAllByCarId(car.getId())).thenReturn(logs);
        when(fuelPriceService.getAvgFuelPrice()).thenReturn(1.75);
    }

    @Test
    void fullMonth_carriesCostDistanceComparisonAndChargeSplit() {
        addLog(ChargingType.AC, "8.00", 1);
        addLog(ChargingType.DC, "20.00", 2);
        addLog(ChargingType.DC, "21.74", 3);
        stats("147.3", "49.74", "863", "17.1", null);

        MonthlyRecap recap = service.build(candidate(), AUGUST).orElseThrow();

        assertThat(recap.charges()).isEqualTo(3);
        assertThat(recap.acCharges()).isEqualTo(1);
        assertThat(recap.dcCharges()).isEqualTo(2);
        assertThat(recap.kwh()).isEqualByComparingTo("147.3");
        assertThat(recap.costEur()).isEqualByComparingTo("49.74");
        assertThat(recap.distanceKm()).isEqualByComparingTo("863");
        assertThat(recap.consumptionKwhPer100km()).isEqualByComparingTo("17.1");
        assertThat(recap.costPer100Km()).isEqualByComparingTo("5.76");
        // 863 km * 7 l/100 km * 1,75 €/l
        assertThat(recap.fuelCostEur()).isEqualByComparingTo("105.72");
        assertThat(recap.carName()).isEqualTo("Model 3");
        assertThat(recap.carId()).isEqualTo(car.getId());
        assertThat(recap.pricelessHint()).isNull();
    }

    @Test
    void previousMonthDistance_comesFromTheSameStatistics() {
        addLog(ChargingType.AC, "8.00", 1);
        addLog(ChargingType.AC, "8.00", 2);
        addLog(ChargingType.AC, "8.00", 3);
        stats("90", "24", "1766", null, null);
        statsFor(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "863");

        MonthlyRecap recap = service.build(candidate(), AUGUST).orElseThrow();

        assertThat(recap.previousDistanceKm()).isEqualByComparingTo("863");
        assertThat(recap.savingsEur()).isEqualByComparingTo(recap.fuelCostEur().subtract(new BigDecimal("24")));
    }

    @Test
    void savings_onlyWhenElectricityWasCheaper() {
        addLog(ChargingType.DC, "60.00", 1);
        addLog(ChargingType.DC, "60.00", 2);
        addLog(ChargingType.DC, "60.00", 3);
        // 100 km * 7 l * 1,75 € = 12,25 € Benzin gegen 180 € Strom
        stats("90", "180", "100", null, null);

        MonthlyRecap recap = service.build(candidate(), AUGUST).orElseThrow();

        assertThat(recap.savingsEur()).isNull();
        assertThat(recap.previousDistanceKm()).isNull();
    }

    @Test
    void underThreeChargesInStatistics_isEmpty() {
        addLog(ChargingType.AC, "8.00", 1);
        addLog(ChargingType.AC, "8.00", 2);
        logs.add(logAt(ChargingType.AC, "8.00", 3).toBuilder().includeInStatistics(false).build());
        stats("30", "16", "200", null, null);

        assertThat(service.build(candidate(), AUGUST)).isEmpty();
    }

    @Test
    void logsOutsideTheMonth_areIgnored() {
        addLog(ChargingType.AC, "8.00", 1);
        addLog(ChargingType.AC, "8.00", 2);
        logs.add(logAt(ChargingType.AC, "8.00", 3).toBuilder().loggedAt(LocalDateTime.of(2026, 9, 1, 0, 0)).build());
        stats("30", "16", "200", null, null);

        assertThat(service.build(candidate(), AUGUST)).isEmpty();
    }

    @Test
    void noDistance_dropsComparisonAndCostPer100Km() {
        addLog(ChargingType.AC, "8.00", 1);
        addLog(ChargingType.AC, "8.00", 2);
        addLog(ChargingType.AC, "8.00", 3);
        stats("90", "24", null, null, null);

        MonthlyRecap recap = service.build(candidate(), AUGUST).orElseThrow();

        assertThat(recap.distanceKm()).isNull();
        assertThat(recap.fuelCostEur()).isNull();
        assertThat(recap.costPer100Km()).isNull();
    }

    @Test
    void onePricelessCharge_namesItsTimeAndKwh() {
        addLog(ChargingType.DC, "20.00", 1);
        addLog(ChargingType.DC, "20.00", 2);
        logs.add(logAt(ChargingType.DC, null, 16).toBuilder()
                .loggedAt(LocalDateTime.of(2026, 8, 16, 11, 9))
                .kwhCharged(null).kwhAtVehicle(new BigDecimal("6.1")).build());
        stats("100", "40", "500", null, null);

        MonthlyRecap.PricelessHint hint = service.build(candidate(), AUGUST).orElseThrow().pricelessHint();

        assertThat(hint.count()).isEqualTo(1);
        assertThat(hint.firstAt()).isEqualTo(LocalDateTime.of(2026, 8, 16, 11, 9));
        assertThat(hint.firstKwh()).isEqualByComparingTo("6.1");
    }

    @Test
    void noPriceAtAll_hasNoCostButHintForAllCharges() {
        addLog(ChargingType.AC, null, 1);
        addLog(ChargingType.AC, null, 2);
        addLog(ChargingType.AC, null, 3);
        stats("90", "0", "500", null, null);

        MonthlyRecap recap = service.build(candidate(), AUGUST).orElseThrow();

        assertThat(recap.costEur()).isNull();
        assertThat(recap.costPer100Km()).isNull();
        assertThat(recap.pricelessHint().count()).isEqualTo(3);
    }

    @Test
    void homeShare_onlyWhenSomethingWasChargedPrivately() {
        addLog(ChargingType.AC, "8.00", 1);
        addLog(ChargingType.AC, "8.00", 2);
        addLog(ChargingType.DC, "8.00", 3);
        stats("100", "24", "500", null, new LocationSplit(new BigDecimal("25"), new BigDecimal("75"), BigDecimal.ZERO));

        assertThat(service.build(candidate(), AUGUST).orElseThrow().homeSharePercent()).isEqualTo(75);

        stats("100", "24", "500", null, new LocationSplit(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(service.build(candidate(), AUGUST).orElseThrow().homeSharePercent()).isNull();

        stats("1000", "24", "500", null, new LocationSplit(new BigDecimal("996"), new BigDecimal("4"), BigDecimal.ZERO));

        assertThat(service.build(candidate(), AUGUST).orElseThrow().homeSharePercent()).isNull();
    }

    // --- helpers ---

    private RecapCandidate candidate() {
        return new RecapCandidate(user.getId(), car.getId());
    }

    private void addLog(ChargingType type, String cost, int day) {
        logs.add(logAt(type, cost, day));
    }

    private EvLog logAt(ChargingType type, String cost, int day) {
        return TestDataBuilder.createTestEvLogWithTimestamp(car.getId(), new BigDecimal("30"),
                        cost == null ? null : new BigDecimal(cost), LocalDateTime.of(2026, 8, day, 12, 0))
                .toBuilder().chargingType(type).build();
    }

    private void statsFor(LocalDate start, LocalDate end, String distance) {
        EvLogStatisticsResponse response = new EvLogStatisticsResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, new BigDecimal(distance), null,
                null, null, null, List.of(), null, null, null, null);
        when(statisticsService.getStatistics(eq(car.getId()), eq(user.getId()), eq(start), eq(end), any())).thenReturn(response);
    }

    private void stats(String kwh, String cost, String distance, String consumption, LocationSplit location) {
        EvLogStatisticsResponse response = new EvLogStatisticsResponse(
                new BigDecimal(kwh), new BigDecimal(cost), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(cost),
                null, null, null, null, null,
                distance == null ? null : new BigDecimal(distance),
                consumption == null ? null : new BigDecimal(consumption),
                null, null, null, List.of(), null, location, null, null);
        when(statisticsService.getStatistics(eq(car.getId()), eq(user.getId()),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)), any())).thenReturn(response);
    }
}
