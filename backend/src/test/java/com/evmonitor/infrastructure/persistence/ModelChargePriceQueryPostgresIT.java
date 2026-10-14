package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Home and fast-charging price of a model, as the model page, the calculator and the partner tile
 * show them. Home: AC not marked public. Fast: DC from 50 kW, the peak power or, without it, the
 * average power of the session. Both weighted by kWh, so a small top-up does not count as much as
 * a full charge. Each case on its own model: all Postgres tests share one database.
 */
class ModelChargePriceQueryPostgresIT extends AbstractPostgresIT {

    @Autowired JpaEvLogRepository jpaEvLogRepository;
    @Autowired CarRepository carRepository;
    @Autowired UserRepository userRepository;

    private static final LocalDateTime AT = LocalDateTime.of(2024, 5, 10, 20, 0);
    /** One car may log once per moment: every session gets its own hour */
    private int hour;

    @Test
    void home_isPrivateAcWeightedByKwh() {
        Car car = car(CarBrand.CarModel.ARIYA);
        // four short top-ups at 50 ct, one full charge at 10 ct: 26 € for 100 kWh, not the 42 ct per session
        for (int i = 0; i < 4; i++) log(car, ChargingType.AC, false, "10", "5.00", null, 120);
        log(car, ChargingType.AC, false, "60", "6.00", null, 480);
        // public AC is not home charging
        for (int i = 0; i < 5; i++) log(car, ChargingType.AC, true, "20", "12.00", null, 120);

        assertThat(price(CarBrand.CarModel.ARIYA, 0)).isEqualByComparingTo("0.260");
    }

    @Test
    void fast_isDcFromFiftyKwPeak() {
        Car car = car(CarBrand.CarModel.EV_9);
        for (int i = 0; i < 5; i++) log(car, ChargingType.DC, true, "40", "16.00", "150", 30);
        // DC at 22 kW peak is a wallbox misread as DC or a slow column, not the motorway charger
        for (int i = 0; i < 5; i++) log(car, ChargingType.DC, true, "10", "2.00", "22", 60);

        assertThat(price(CarBrand.CarModel.EV_9, 2)).isEqualByComparingTo("0.400");
    }

    @Test
    void fastWithoutPeak_countsWhenTheAveragePowerReachesFiftyKw() {
        Car car = car(CarBrand.CarModel.FIAT_500E);
        // 40 kWh in 30 min: about 76 kW on average, counts
        for (int i = 0; i < 5; i++) log(car, ChargingType.DC, true, "40", "20.00", null, 30);
        // 20 kWh in two hours: 10 kW, does not count; without a duration it cannot be judged
        for (int i = 0; i < 5; i++) log(car, ChargingType.DC, true, "20", "2.00", null, 120);
        log(car, ChargingType.DC, true, "30", "3.00", null, null);

        assertThat(price(CarBrand.CarModel.FIAT_500E, 2)).isEqualByComparingTo("0.500");
    }

    @Test
    void underFiveSessions_noPrice() {
        Car car = car(CarBrand.CarModel.SMART_1);
        for (int i = 0; i < 4; i++) log(car, ChargingType.AC, false, "10", "3.00", null, 120);

        assertThat(stats(CarBrand.CarModel.SMART_1)[0]).isNull();
    }

    private BigDecimal price(CarBrand.CarModel model, int column) {
        Object value = stats(model)[column];
        assertThat(value).as("price column %d", column).isNotNull();
        return new BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP);
    }

    private Object[] stats(CarBrand.CarModel model) {
        Object[] row = jpaEvLogRepository.findHomeFastCostStatsByModel(model.name(), false);
        return row.length > 0 && row[0] instanceof Object[] nested ? nested : row;
    }

    private Car car(CarBrand.CarModel model) {
        User user = userRepository.save(TestDataBuilder.createTestUser("price-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com"));
        return carRepository.save(TestDataBuilder.createTestCar(user.getId(), model, BigDecimal.valueOf(60)));
    }

    private void log(Car car, ChargingType type, boolean publicCharging, String kwh, String cost, String peakKw, Integer minutes) {
        EvLog base = TestDataBuilder.createTestEvLogWithTimestamp(car.getId(), new BigDecimal(kwh), new BigDecimal(cost), AT.plusHours(hour++));
        evLogRepository.save(base.toBuilder()
                .chargingType(type)
                .publicCharging(publicCharging)
                .maxChargingPowerKw(peakKw == null ? null : new BigDecimal(peakKw))
                .chargeDurationMinutes(minutes)
                .kwhAtVehicle(null)
                .build());
    }
}
