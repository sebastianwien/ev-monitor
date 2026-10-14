package com.evmonitor.application.personalrange;

import com.evmonitor.application.charging.FastChargePower;

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
                .routeMeanKwhPer100km(new BigDecimal("16.0"))
                .dcChargingPower(FastChargePower.measured(new BigDecimal("100"), 40)).homePricePerKwh(new BigDecimal("0.30"))
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
                PersonalRangeRequestFixtures.answers(40, 50, true, 600), measured());

        // highway 19 against base 16 = +18.75 %, half of the driving: summer 15 × 1.094 = 16.4 → 77 × 0.9 × 100 / 16.4 = 422 → 420;
        // winter 20 × 1.094 = 21.9 → 316 → 320
        assertThat(r.summerRangeKm()).isEqualTo(420);
        assertThat(r.winterRangeKm()).isEqualTo(320);
        // first leg 90→10 %: 77 × 0.8 / 19 = 324 km; then 10→80 % legs of 284 km → 1 stop
        assertThat(r.longTripStops()).isEqualTo(1);
        // 53.9 kWh / 100 kW = 32 min → nearest 5 min
        assertThat(r.longTripChargingMinutes()).isEqualTo(30);
        // 600 km at 110 km/h = 327 min + 32.3 min charging (unrounded) = 359.3 → nearest 5 min
        assertThat(r.longTripTotalMinutes()).isEqualTo(360);
        // mixed 17.5 kWh/100km × 200 km/week = 35 kWh; home charge 20→80 % = 46.2 kWh → 0.76 → 1.0
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("1.0");
        // costs are paid at the plug: 40 km × 22 days × 0.175 kWh/km / 0.90 AC efficiency × 0.30 EUR = 51.3 → 51
        assertThat(r.monthlyCostEur()).isEqualTo(51);
        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.MEASURED);
        // the travel speed is a fixed setting, not a measurement; everything else is measured
        assertThat(r.assumptions()).filteredOn(a -> !a.key().equals("TRAVEL_SPEED")).allMatch(PersonalRangeResponse.Assumption::measured);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("TRAVEL_SPEED") && a.value().intValue() == 110 && !a.measured());
    }

    @Test
    void withoutCommunityDataEverythingFallsBackToWltpFactors() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                PersonalRangeRequestFixtures.answers(40, 50, false, 600), wltpOnly());

        // no fleet factors: fixed fallbacks. summer 16 × 1.15 = 18.4, winter 16 × 1.49 = 23.8; highway 20 against base 19.4
        // = +3.1 %, half of it: summer 18.7 → 370.6 → 370; winter 24.2 → 286.4 → 290
        assertThat(r.summerRangeKm()).isEqualTo(370);
        assertThat(r.winterRangeKm()).isEqualTo(290);
        // highway 16 × 1.25 = 20: first leg 308 km, legs 269.5 km → 2 stops; no power at all: fixed default
        // 104 kW: 2 × 31.1 = 62 → 60
        assertThat(r.longTripStops()).isEqualTo(2);
        assertThat(r.longTripChargingMinutes()).isEqualTo(60);
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

        PersonalRangeResponse r = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 0, true, 300), basis);

        // summer 16 × 1.10 = 17.6 → 393.75 → 390; winter 16 × 1.60 = 25.6 → 270.7 → 270
        assertThat(r.summerRangeKm()).isEqualTo(390);
        assertThat(r.winterRangeKm()).isEqualTo(270);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("BASE_CONSUMPTION") && a.value().compareTo(new BigDecimal("20.0")) == 0);
    }

    @Test
    void highwayEffectComparesWithLogsOfTheSameRouteTypedPeriodNotTheOverallMean() {
        // overall mean 20 (all years, winters included) would make highway 19 look cheaper; the
        // route-typed logs average 16, so the highway costs 19/16
        ModelRangeBasis basis = measured().toBuilder().baseKwhPer100km(new BigDecimal("20.0")).build();

        PersonalRangeResponse none = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 0, true, 600), basis);
        PersonalRangeResponse all = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 100, true, 600), basis);

        assertThat(none.summerRangeKm()).isEqualTo(460);
        assertThat(all.summerRangeKm()).isEqualTo(390);
    }

    @Test
    void measuredCheaperHighwayIsShownAsSuch() {
        // a relaxed 100 km/h behind a van against brisk city driving: measured, so it counts
        ModelRangeBasis basis = measured().toBuilder().highwayKwhPer100km(new BigDecimal("15.0")).build();

        PersonalRangeResponse none = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 0, true, 600), basis);
        PersonalRangeResponse all = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 100, true, 600), basis);

        assertThat(all.summerRangeKm()).isGreaterThan(none.summerRangeKm());
    }

    @Test
    void fewHighwayCarsUseTheFleetSurchargeNotWltpAgainstMeasured() {
        // 3 cars: no own highway ratio; WLTP × 1.25 = 20 against the measured 16 would be +25 %, the fleet says +3 %
        ModelRangeBasis basis = measured().toBuilder().highwayCarCount(3).build();

        PersonalRangeResponse all = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 100, true, 600), basis);

        // summer 15 × 1.03 = 15.5 → 77 × 0.9 × 100 / 15.5 = 447 → 450
        assertThat(all.summerRangeKm()).isEqualTo(450);
    }

    @Test
    void highwayShareMovesTheSeasonRangesLikeTheBand() {
        // 0 % motorway: the model's season values as the page shows them
        PersonalRangeResponse none = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 0, true, 600), measured());
        assertThat(none.summerRangeKm()).isEqualTo(460);
        assertThat(none.winterRangeKm()).isEqualTo(350);

        // 100 % motorway: the full highway surcharge (19 against 16) on both seasons
        PersonalRangeResponse all = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(40, 100, true, 600), measured());
        // summer 15 × 1.1875 = 17.8 → 389 → 390; winter 20 × 1.1875 = 23.8 → 291 → 290
        assertThat(all.summerRangeKm()).isEqualTo(390);
        assertThat(all.winterRangeKm()).isEqualTo(290);

        // the band before the gate shows the same two ends
        PersonalRangePreviewResponse band = PersonalRangeCalculator.preview(PersonalRangeRequestFixtures.answers(40, 100, true, 600), measured());
        assertThat(band.minKm()).isEqualTo(all.winterRangeKm());
        assertThat(band.maxKm()).isEqualTo(all.summerRangeKm());
    }

    @Test
    void annualMileageDrivesCostsAndChargesWhenGiven() {
        PersonalRangeRequest in = new PersonalRangeRequest(40, 50, true, 600, null, null, 30_000, null);

        PersonalRangeResponse r = PersonalRangeCalculator.calculate(in, measured());

        // 30,000 km / 12 = 2,500 km a month × 0.175 kWh/km / 0.90 × 0.30 EUR = 145.8 → 146 (not 51 from 22 commute days)
        assertThat(r.monthlyCostEur()).isEqualTo(146);
        // 30,000 / 52 = 577 km a week × 0.175 = 101 kWh; home charge 46.2 kWh → 2.19 → 2.5
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("2.5");
        // the ranges do not depend on mileage
        assertThat(r.summerRangeKm()).isEqualTo(420);
    }

    @Test
    void commuteOnTheGivenDaysDrivesWeeklyChargesPerSeasonWithoutRounding() {
        PersonalRangeResponse home = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(100, 50, true, 600, null, null, 30_000, 3), measured());

        // 100 km × 3 days = 300 km a week; summer 16.4 kWh/100km = 49.2 kWh / home charge 20→80 % 46.2 kWh = 1.06,
        // winter 21.9 = 65.7 kWh → 1.42; no rounding to half charges, the yearly mileage does not enter
        assertThat(home.chargesPerWeekSummer()).isEqualByComparingTo("1.06");
        assertThat(home.chargesPerWeekWinter()).isEqualByComparingTo("1.42");

        PersonalRangeResponse fastCharging = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(100, 50, false, 600, null, null, null, 3), measured());
        // public charging 10 → 80 % = 53.9 kWh: 0.91 and 1.22
        assertThat(fastCharging.chargesPerWeekSummer()).isEqualByComparingTo("0.91");
        assertThat(fastCharging.chargesPerWeekWinter()).isEqualByComparingTo("1.22");
    }

    @Test
    void rankingEntryAddsTheStopLadderOnTheHighwayConsumptionOfTheStops() {
        PersonalRangeRankingEntry e = PersonalRangeCalculator.rankingEntry("VOLKSWAGEN", "ID_3",
                PersonalRangeRequestFixtures.answers(40, 50, true, 600), measured());

        // first leg 77 × 0.8 / 19 = 324 km, each stop 77 × 0.7 / 19 = 284 km: 608 → 610 and 892 → 890
        assertThat(e.oneStopReachKm()).isEqualTo(610);
        assertThat(e.twoStopsReachKm()).isEqualTo(890);
        assertThat(e.longTripStops()).isEqualTo(1);
        assertThat(e.winterRangeKm()).isEqualTo(320);
        // the distance the monthly cost rests on: 40 km × 5 days × 4.4 weeks
        assertThat(e.monthlyKm()).isEqualTo(880);
    }

    @Test
    void withoutYearlyMileageMonthlyCostAndChargesFollowTheCommuteDays() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(40, 50, true, 600, null, null, null, 3), measured());

        // 40 km × 3 days × 4.4 weeks (22 workdays on 5 days) = 528 km × 0.175 kWh/km / 0.90 × 0.30 EUR = 30.8 → 31
        assertThat(r.monthlyCostEur()).isEqualTo(31);
        // 120 km a week × 0.175 = 21 kWh / 46.2 kWh = 0.45 → 0.5
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("0.5");
    }

    @Test
    void storedProfilesWithoutCommuteDaysCountFiveDays() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                PersonalRangeRequestFixtures.answers(100, 50, true, 600), measured());

        // profiles stored before the commute-days question: 100 km × 5 days = 500 km → 82 / 46.2 = 1.77, 109.5 / 46.2 = 2.37
        assertThat(r.chargesPerWeekSummer()).isEqualByComparingTo("1.77");
        assertThat(r.chargesPerWeekWinter()).isEqualByComparingTo("2.37");
    }

    @Test
    void mixedSourcesArePartial() {
        ModelRangeBasis basis = wltpOnly().toBuilder()
                .summerKwhPer100km(new BigDecimal("15.0")).winterKwhPer100km(new BigDecimal("20.0"))
                .build();

        PersonalRangeResponse r = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(20, 0, true, 300), basis);

        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.PARTIAL);
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("WINTER_CONSUMPTION") && a.measured());
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("HIGHWAY_CONSUMPTION") && !a.measured());
    }

    /** Highway measured 19.0 next to the WLTP estimate 16 × 1.25 = 20.0, behind the given number of cars. */
    private static PersonalRangeResponse.Assumption highwayWith(int cars) {
        ModelRangeBasis basis = measured().toBuilder().highwayCarCount(cars).build();
        return PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(20, 0, true, 300), basis)
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
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(20, 0, true, 300),
                measured().toBuilder().highwayCarCount(8).build());

        assertThat(r.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.PARTIAL);
    }

    @Test
    void monthlyCostUsesGivenPriceOverCommunityPrice() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(
                new PersonalRangeRequest(40, 50, true, 600, new BigDecimal("0.45"), null, null, null), measured());

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
                PersonalRangeRequestFixtures.answers(40, 50, true, 600), measured().toBuilder().highwayCarCount(2).build());

        // 50 % motorway with 2 highway cars: half the fleet surcharge of 3 %; winter 20.3 → 341 → 340, summer 15.2 → 456 → 460
        assertThat(p.minKm()).isEqualTo(340);
        assertThat(p.maxKm()).isEqualTo(460);
        // highway is estimated here, but the band only rests on summer and winter
        assertThat(p.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.MEASURED);

        PersonalRangePreviewResponse partial = PersonalRangeCalculator.preview(
                PersonalRangeRequestFixtures.answers(40, 50, true, 600),
                wltpOnly().toBuilder().winterKwhPer100km(new BigDecimal("20.0")).build());
        assertThat(partial.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.PARTIAL);
    }

    @Test
    void shortTripNeedsNoStopAndNoCommuteCostsNothing() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(0, 0, true, 50), measured());

        assertThat(r.longTripStops()).isZero();
        assertThat(r.longTripChargingMinutes()).isZero();
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("0");
        assertThat(r.monthlyCostEur()).isZero();
    }

    @Test
    void extremeInputsStayFinite() {
        PersonalRangeResponse r = PersonalRangeCalculator.calculate(PersonalRangeRequestFixtures.answers(500, 100, false, 2000), measured());

        // 2000 km: (2000 - 324) / 284 = 5.9 → 6 stops
        assertThat(r.longTripStops()).isEqualTo(6);
        // 2500 km/week × 0.19 = 475 kWh; DC charge 10→80 % = 53.9 kWh → 8.8 → 9.0
        assertThat(r.chargesPerWeek()).isEqualByComparingTo("9.0");
    }
}
