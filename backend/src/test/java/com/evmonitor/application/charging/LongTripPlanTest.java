package com.evmonitor.application.charging;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LongTripPlanTest {

    private static final BigDecimal NET = new BigDecimal("75");
    private static final BigDecimal HIGHWAY = new BigDecimal("20");
    private static final BigDecimal POWER = new BigDecimal("130");

    @Test
    void stopsChargingAndTravelTimeForAHolidayTrip() {
        // Erste Etappe 100 → 10 %: 75 × 0,9 × 100 / 20 = 337,5 km; jede weitere 10 → 80 %: 262,5 km
        // 800 km: ceil(462,5 / 262,5) = 2 Stopps; je 75 × 0,7 / 130 kW = 24,2 min → 48,5 → 50 min
        // Fahrt 800 / 110 km/h = 436 min; gesamt 486 → auf 5 gerundet 485 min (8 h 5 min)
        LongTripPlan plan = LongTripPlan.of(NET, HIGHWAY, POWER, 800);

        assertThat(plan.stops()).isEqualTo(2);
        assertThat(plan.chargingMinutes()).isEqualTo(50);
        assertThat(plan.drivingMinutes()).isEqualTo(436);
        assertThat(plan.totalMinutes()).isEqualTo(485);
    }

    @Test
    void aTripWithinTheFirstLegNeedsNoStop() {
        LongTripPlan plan = LongTripPlan.of(NET, HIGHWAY, POWER, 300);

        assertThat(plan.stops()).isZero();
        assertThat(plan.chargingMinutes()).isZero();
        // 300 / 110 × 60 = 163,6 → 164; gesamt auf 5 gerundet 165
        assertThat(plan.drivingMinutes()).isEqualTo(164);
        assertThat(plan.totalMinutes()).isEqualTo(165);
    }

    @Test
    void theFirstLegStartsWithAFullBatteryLikeTheRangeOfTheList() {
        // 330 km: mit 90 → 10 % (300 km) wäre es ein Stopp, ab 100 % (337,5 km) keiner
        assertThat(LongTripPlan.of(NET, HIGHWAY, POWER, 330).stops()).isZero();
        assertThat(LongTripPlan.of(NET, HIGHWAY, POWER, 340).stops()).isEqualTo(1);
    }

    @Test
    void slowerChargingMakesTheSameTripLonger() {
        // 50 kW: je Stopp 63 min, 2 Stopps 126 → 125 min
        LongTripPlan slow = LongTripPlan.of(NET, HIGHWAY, new BigDecimal("50"), 800);

        assertThat(slow.stops()).isEqualTo(2);
        assertThat(slow.chargingMinutes()).isEqualTo(125);
        assertThat(slow.totalMinutes()).isGreaterThan(LongTripPlan.of(NET, HIGHWAY, POWER, 800).totalMinutes());
    }

    @Test
    void reachWithStopsIsTheFirstLegPlusOneLegPerStop() {
        // 337,5 km erste Etappe, je Stopp 262,5 km dazu
        assertThat(LongTripPlan.reachKm(NET, HIGHWAY, 0)).isEqualTo(337.5);
        assertThat(LongTripPlan.reachKm(NET, HIGHWAY, 1)).isEqualTo(600.0);
        assertThat(LongTripPlan.reachKm(NET, HIGHWAY, 2)).isEqualTo(862.5);
    }
}
