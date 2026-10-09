package com.evmonitor.application.personalrange;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalRangeCalculatorTest {

    private static final BigDecimal NET = new BigDecimal("77");
    private static final BigDecimal WLTP = new BigDecimal("16.0");
    private static final BigDecimal REF_HOME = new BigDecimal("0.32");
    private static final BigDecimal REF_PUBLIC = new BigDecimal("0.55");

    /** All community values measured: summer 15, winter 20, highway 19, city 16, DC 100 kW, 0.30/0.60 EUR. */
    private static ModelRangeBasis measured() {
        return ModelRangeBasis.builder()
                .netBatteryKwh(NET).netBatteryMeasured(true).wltpKwhPer100km(WLTP)
                .summerKwhPer100km(new BigDecimal("15.0")).winterKwhPer100km(new BigDecimal("20.0"))
                .highwayKwhPer100km(new BigDecimal("19.0")).baseKwhPer100km(new BigDecimal("16.0"))
                .dcChargingPowerKw(new BigDecimal("100")).homePricePerKwh(new BigDecimal("0.30"))
                .publicPricePerKwh(new BigDecimal("0.60"))
                .referenceHomePricePerKwh(REF_HOME).referencePublicPricePerKwh(REF_PUBLIC)
                .build();
    }

    private static ModelRangeBasis wltpOnly() {
        return ModelRangeBasis.builder()
                .netBatteryKwh(NET).netBatteryMeasured(true).wltpKwhPer100km(WLTP)
                .referenceHomePricePerKwh(REF_HOME).referencePublicPricePerKwh(REF_PUBLIC)
                .build();
    }

    @Test
    void measuredValuesDriveEveryResult() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(40, 50, true, 600), measured());

        // 77 × 0.9 × 100 / 15 = 462 → 460; / 20 = 346.5 → 350
        assertThat(r.summerRangeKm()).isEqualTo(460);
        assertThat(r.winterRangeKm()).isEqualTo(350);
        // first leg 90→10 %: 77 × 0.8 / 19 = 324 km; then 10→80 % legs of 284 km → 1 stop
        assertThat(r.longTripStops()).isEqualTo(1);
        // 53.9 kWh / 100 kW = 32 min → nearest 5 min
        assertThat(r.longTripChargingMinutes()).isEqualTo(30);
        // mixed 17.5 kWh/100km × 200 km/week = 35 kWh; home charge 20→80 % = 46.2 kWh → 0.76 → 1.0
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("1.0");
        // 40 km × 22 days × 0.175 kWh/km × 0.30 EUR = 46.2 → 46
        assertThat(r.monthlyCostEur()).isEqualTo(46);
        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.MEASURED);
        assertThat(r.assumptions()).allMatch(PersonalRangeResponse.Assumption::measured);
    }

    @Test
    void withoutCommunityDataEverythingFallsBackToWltpFactors() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(40, 50, false, 600), wltpOnly());

        // summer 16 × 1.15 = 18.4 → 376.6 → 380; winter 16 × 1.35 = 21.6 → 320.8 → 320
        assertThat(r.summerRangeKm()).isEqualTo(380);
        assertThat(r.winterRangeKm()).isEqualTo(320);
        // highway 16 × 1.25 = 20: first leg 308 km, legs 269.5 km → 2 stops; 50 kW assumed: 2 × 64.7 = 129 → 130
        assertThat(r.longTripStops()).isEqualTo(2);
        assertThat(r.longTripChargingMinutes()).isEqualTo(130);
        // base 18.4, highway 20 → mixed 19.2; 880 km × 0.192 × 0.55 public reference = 92.9 → 93
        assertThat(r.monthlyCostEur()).isEqualTo(93);
        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.WLTP_FACTOR);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("CHARGING_POWER") && !a.measured());
    }

    @Test
    void mixedSourcesArePartial() {
        ModelRangeBasis basis = wltpOnly().toBuilder()
                .summerKwhPer100km(new BigDecimal("15.0")).winterKwhPer100km(new BigDecimal("20.0"))
                .build();

        PersonalRangeResponse r = PersonalRangeCalculator.calculate(new PersonalRangeRequest(20, 0, true, 300), basis);

        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.PARTIAL);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("WINTER_CONSUMPTION") && a.measured());
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("HIGHWAY_CONSUMPTION") && !a.measured());
    }

    @Test
    void shortTripNeedsNoStopAndNoCommuteCostsNothing() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(new PersonalRangeRequest(0, 0, true, 50), measured());

        assertThat(r.longTripStops()).isZero();
        assertThat(r.longTripChargingMinutes()).isZero();
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("0");
        assertThat(r.monthlyCostEur()).isZero();
    }

    @Test
    void extremeInputsStayFinite() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(new PersonalRangeRequest(500, 100, false, 2000), measured());

        // 2000 km: (2000 - 324) / 284 = 5.9 → 6 stops
        assertThat(r.longTripStops()).isEqualTo(6);
        // 2500 km/week × 0.19 = 475 kWh; DC charge 10→80 % = 53.9 kWh → 8.8 → 9.0
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("9.0");
    }
}
