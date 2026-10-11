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
                .highwayKwhPer100km(new BigDecimal("19.0")).highwayCarCount(20).baseKwhPer100km(new BigDecimal("16.0"))
                .dcChargingPowerKw(new BigDecimal("100")).homePricePerKwh(new BigDecimal("0.30"))
                .publicPricePerKwh(new BigDecimal("0.60"))
                .referenceHomePricePerKwh(REF_HOME).referencePublicPricePerKwh(REF_PUBLIC)
                .homeChargingEfficiency(new BigDecimal("0.90")).publicChargingEfficiency(new BigDecimal("0.95"))
                .build();
    }

    private static ModelRangeBasis wltpOnly() {
        return ModelRangeBasis.builder()
                .netBatteryKwh(NET).netBatteryMeasured(true).wltpKwhPer100km(WLTP)
                .referenceHomePricePerKwh(REF_HOME).referencePublicPricePerKwh(REF_PUBLIC)
                .homeChargingEfficiency(new BigDecimal("0.90")).publicChargingEfficiency(new BigDecimal("0.95"))
                .build();
    }

    @Test
    void measuredValuesDriveEveryResult() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(40, 50, true, 600), measured());

        // highway 19 against base 16 = +18.75 %, half of the driving: summer 15 × 1.094 = 16.4 → 77 × 0.9 × 100 / 16.4 = 422 → 420;
        // winter 20 × 1.094 = 21.9 → 316 → 320
        assertThat(r.summerRangeKm()).isEqualTo(420);
        assertThat(r.winterRangeKm()).isEqualTo(320);
        // first leg 90→10 %: 77 × 0.8 / 19 = 324 km; then 10→80 % legs of 284 km → 1 stop
        assertThat(r.longTripStops()).isEqualTo(1);
        // 53.9 kWh / 100 kW = 32 min → nearest 5 min
        assertThat(r.longTripChargingMinutes()).isEqualTo(30);
        // mixed 17.5 kWh/100km × 200 km/week = 35 kWh; home charge 20→80 % = 46.2 kWh → 0.76 → 1.0
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("1.0");
        // costs are paid at the plug: 40 km × 22 days × 0.175 kWh/km / 0.90 AC efficiency × 0.30 EUR = 51.3 → 51
        assertThat(r.monthlyCostEur()).isEqualTo(51);
        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.MEASURED);
        assertThat(r.assumptions()).allMatch(PersonalRangeResponse.Assumption::measured);
    }

    @Test
    void withoutCommunityDataEverythingFallsBackToWltpFactors() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(40, 50, false, 600), wltpOnly());

        // no fleet factors: fixed fallbacks. summer 16 × 1.15 = 18.4, winter 16 × 1.49 = 23.8; highway 20 against base 19.4
        // = +3.1 %, half of it: summer 18.7 → 370.6 → 370; winter 24.2 → 286.4 → 290
        assertThat(r.summerRangeKm()).isEqualTo(370);
        assertThat(r.winterRangeKm()).isEqualTo(290);
        // highway 16 × 1.25 = 20: first leg 308 km, legs 269.5 km → 2 stops; 50 kW assumed: 2 × 64.7 = 129 → 130
        assertThat(r.longTripStops()).isEqualTo(2);
        assertThat(r.longTripChargingMinutes()).isEqualTo(130);
        // base 16 × 1.21 = 19.4, highway 20 → mixed 19.7; 880 km × 0.197 / 0.95 DC × 0.55 public reference = 100.4 → 100
        assertThat(r.monthlyCostEur()).isEqualTo(100);
        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.WLTP_FACTOR);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("CHARGING_POWER") && !a.measured());
    }

    @Test
    void fleetFactorsReplaceTheFixedWltpFactors() {
        ModelRangeBasis basis = wltpOnly().toBuilder()
                .fleetFactors(new WltpFleetFactors(new BigDecimal("1.25"), 17, new BigDecimal("1.10"), 17, new BigDecimal("1.60"), 15))
                .build();

        PersonalRangeResponse r = PersonalRangeCalculator.calculate(new PersonalRangeRequest(40, 0, true, 300), basis);

        // summer 16 × 1.10 = 17.6 → 393.75 → 390; winter 16 × 1.60 = 25.6 → 270.7 → 270
        assertThat(r.summerRangeKm()).isEqualTo(390);
        assertThat(r.winterRangeKm()).isEqualTo(270);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("BASE_CONSUMPTION") && a.value().compareTo(new BigDecimal("20.0")) == 0);
    }

    @Test
    void highwayShareMovesTheSeasonRangesLikeTheBand() {
        // 0 % motorway: the model's season values as the page shows them
        PersonalRangeResponse none = PersonalRangeCalculator.calculate(new PersonalRangeRequest(40, 0, true, 600), measured());
        assertThat(none.summerRangeKm()).isEqualTo(460);
        assertThat(none.winterRangeKm()).isEqualTo(350);

        // 100 % motorway: the full highway surcharge (19 against 16) on both seasons
        PersonalRangeResponse all = PersonalRangeCalculator.calculate(new PersonalRangeRequest(40, 100, true, 600), measured());
        // summer 15 × 1.1875 = 17.8 → 389 → 390; winter 20 × 1.1875 = 23.8 → 291 → 290
        assertThat(all.summerRangeKm()).isEqualTo(390);
        assertThat(all.winterRangeKm()).isEqualTo(290);

        // the band before the gate shows the same two ends
        PersonalRangePreviewResponse band = PersonalRangeCalculator.preview(new PersonalRangeRequest(40, 100, true, 600), measured());
        assertThat(band.minKm()).isEqualTo(all.winterRangeKm());
        assertThat(band.maxKm()).isEqualTo(all.summerRangeKm());
    }

    @Test
    void annualMileageDrivesCostsAndChargesWhenGiven() {
        PersonalRangeRequest in = new PersonalRangeRequest(40, 50, true, 600, null, null, 30_000);

        PersonalRangeResponse r = PersonalRangeCalculator.calculate(in, measured());

        // 30,000 km / 12 = 2,500 km a month × 0.175 kWh/km / 0.90 × 0.30 EUR = 145.8 → 146 (not 51 from 22 commute days)
        assertThat(r.monthlyCostEur()).isEqualTo(146);
        // 30,000 / 52 = 577 km a week × 0.175 = 101 kWh; home charge 46.2 kWh → 2.19 → 2.5
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("2.5");
        // the ranges do not depend on mileage
        assertThat(r.summerRangeKm()).isEqualTo(420);
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

    /** Highway measured 19.0 next to the WLTP estimate 16 × 1.25 = 20.0, behind the given number of cars. */
    private static PersonalRangeResponse.Assumption highwayWith(int cars) {
        ModelRangeBasis basis = measured().toBuilder().highwayCarCount(cars).build();
        return PersonalRangeCalculator.calculate(new PersonalRangeRequest(20, 0, true, 300), basis)
                .assumptions().stream().filter(a -> a.key().equals("HIGHWAY_CONSUMPTION")).findFirst().orElseThrow();
    }

    @Test
    void highwayBelowFiveCarsIsTheWltpEstimate() {
        PersonalRangeResponse.Assumption a = highwayWith(4);

        assertThat(a.value()).isEqualByComparingTo("20.0");
        assertThat(a.measuredPercent()).isZero();
        assertThat(a.measured()).isFalse();
    }

    @Test
    void highwayBlendsTowardsTheMeasurementAsCarsGrow() {
        // 5 cars: half measured → 19.5; 10 cars: three quarters → 19.25 → 19.3
        assertThat(highwayWith(5).value()).isEqualByComparingTo("19.5");
        assertThat(highwayWith(5).measuredPercent()).isEqualTo(50);
        assertThat(highwayWith(10).value()).isEqualByComparingTo("19.3");
        assertThat(highwayWith(10).measuredPercent()).isEqualTo(75);
        assertThat(highwayWith(10).measured()).isFalse();
    }

    @Test
    void highwayFromFifteenCarsIsFullyMeasured() {
        PersonalRangeResponse.Assumption a = highwayWith(15);

        assertThat(a.value()).isEqualByComparingTo("19.0");
        assertThat(a.measuredPercent()).isEqualTo(100);
        assertThat(a.measured()).isTrue();
    }

    @Test
    void blendedHighwayMakesTheResultPartial() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(new PersonalRangeRequest(20, 0, true, 300),
                measured().toBuilder().highwayCarCount(8).build());

        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.PARTIAL);
    }

    @Test
    void monthlyCostUsesGivenPriceOverCommunityPrice() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(40, 50, true, 600, new BigDecimal("0.45")), measured());

        // 40 km × 22 days × 0.175 kWh/km / 0.90 × 0.45 EUR = 77
        assertThat(r.monthlyCostEur()).isEqualTo(77);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("OWN_PRICE") && a.measured()
                && a.value().compareTo(new BigDecimal("0.45")) == 0);
        assertThat(r.assumptions()).noneMatch(a -> a.key().equals("PRICE"));
        // the user's own price does not touch the data level, which rates the consumption values only
        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.MEASURED);
    }

    @Test
    void previewCarriesOnlyTheTwoSeasonRangesAndTheirLevel() {
        PersonalRangePreviewResponse p = PersonalRangeCalculator.preview(
                new PersonalRangeRequest(40, 50, true, 600), measured().toBuilder().highwayCarCount(2).build());

        // 50 % motorway on the WLTP highway estimate (2 cars): 20 against 16 = +12.5 %; winter 22.5 → 308 → 310, summer 16.9 → 410
        assertThat(p.minKm()).isEqualTo(310);
        assertThat(p.maxKm()).isEqualTo(410);
        // highway is estimated here, but the band only rests on summer and winter
        assertThat(p.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.MEASURED);

        PersonalRangePreviewResponse partial = PersonalRangeCalculator.preview(
                new PersonalRangeRequest(40, 50, true, 600),
                wltpOnly().toBuilder().winterKwhPer100km(new BigDecimal("20.0")).build());
        assertThat(partial.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.PARTIAL);
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
