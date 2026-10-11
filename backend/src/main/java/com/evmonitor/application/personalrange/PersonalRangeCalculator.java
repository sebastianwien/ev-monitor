package com.evmonitor.application.personalrange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure calculation of the personal range result. No consumption formula of its own: all
 * consumption values come from EvLogService results (via ModelRangeBasis) or, where the
 * community has too little data, from WLTP times a fixed factor.
 */
final class PersonalRangeCalculator {

    /**
     * Fallbacks when the fleet has too few models for a median (WltpFleetFactors). Summer, winter and
     * overall match the fleet medians measured on 11.10.2026 (1.13, 1.49, 1.21, rounded up for summer).
     * Highway has no fleet value (route types are too sparse), its estimate stays a fixed factor.
     */
    static final BigDecimal SUMMER_FACTOR = new BigDecimal("1.15");
    static final BigDecimal WINTER_FACTOR = new BigDecimal("1.49");
    static final BigDecimal OVERALL_FACTOR = new BigDecimal("1.21");
    static final BigDecimal HIGHWAY_FACTOR = new BigDecimal("1.25");
    /** Same as the model page: range = 90 % of the net battery. */
    private static final BigDecimal RANGE_SHARE = new BigDecimal("0.9");
    /** Long trip: start at 90 %, arrive at the first charger with 10 %. */
    private static final BigDecimal FIRST_LEG_SHARE = new BigDecimal("0.8");
    /** Fast-charging stop from 10 to 80 %. */
    private static final BigDecimal STOP_SHARE = new BigDecimal("0.7");
    /** Home charging from 20 to 80 %. */
    private static final BigDecimal HOME_CHARGE_SHARE = new BigDecimal("0.6");
    /** Highway measurement counts from this many cars (half weight) and fully from FULL_HIGHWAY_CARS. */
    static final int MIN_HIGHWAY_CARS = 5;
    static final int FULL_HIGHWAY_CARS = 15;
    static final BigDecimal ASSUMED_DC_POWER_KW = new BigDecimal("50");
    private static final int WORKDAYS_PER_WEEK = 5;
    private static final int WORKDAYS_PER_MONTH = 22;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PersonalRangeCalculator() {}

    static PersonalRangeResponse calculate(PersonalRangeRequest in, ModelRangeBasis b) {
        List<PersonalRangeResponse.Assumption> assumptions = new ArrayList<>();
        BigDecimal net = b.netBatteryKwh();
        assumptions.add(new PersonalRangeResponse.Assumption("BATTERY", b.netBatteryMeasured(), net));

        WltpFleetFactors fleet = b.fleetFactors();
        BigDecimal summer = pick("SUMMER_CONSUMPTION", b.summerKwhPer100km(), b.wltpKwhPer100km(),
                factor(fleet != null ? fleet.summer() : null, SUMMER_FACTOR), assumptions);
        BigDecimal winter = pick("WINTER_CONSUMPTION", b.winterKwhPer100km(), b.wltpKwhPer100km(),
                factor(fleet != null ? fleet.winter() : null, WINTER_FACTOR), assumptions);
        BigDecimal highway = highway(b, assumptions);
        BigDecimal base = pick("BASE_CONSUMPTION", b.baseKwhPer100km(), b.wltpKwhPer100km(),
                factor(fleet != null ? fleet.overall() : null, OVERALL_FACTOR), assumptions);

        BigDecimal power = b.dcChargingPowerKw() != null ? b.dcChargingPowerKw() : ASSUMED_DC_POWER_KW;
        assumptions.add(new PersonalRangeResponse.Assumption("CHARGING_POWER", b.dcChargingPowerKw() != null, power));

        BigDecimal price = price(in, b, assumptions);

        // Long trip on the highway value: first leg 90 → 10 %, every further leg 10 → 80 %
        double firstLegKm = km(net, FIRST_LEG_SHARE, highway);
        double legKm = km(net, STOP_SHARE, highway);
        int stops = in.longTripKm() <= firstLegKm ? 0 : (int) Math.ceil((in.longTripKm() - firstLegKm) / legKm);
        double minutesPerStop = net.multiply(STOP_SHARE).doubleValue() / power.doubleValue() * 60;
        int chargingMinutes = (int) (Math.round(stops * minutesPerStop / 5.0) * 5);

        // Commute: base and highway consumption blended by the highway share
        double h = in.highwaySharePercent() / 100.0;
        double mixedKwhPerKm = (base.doubleValue() * (1 - h) + highway.doubleValue() * h) / 100;
        // Season ranges for the profile: the highway surcharge (highway against everyday) on the share
        // of motorway driving, applied to the season value. 0 % motorway gives the model's own season
        // ranges, the figures the page shows; the band before the gate rests on the same two values.
        double highwayRatio = 1 + h * (highway.doubleValue() / base.doubleValue() - 1);
        BigDecimal summerProfile = summer.multiply(BigDecimal.valueOf(highwayRatio)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal winterProfile = winter.multiply(BigDecimal.valueOf(highwayRatio)).setScale(1, RoundingMode.HALF_UP);
        // Mileage: the yearly figure when the reader gave one, else the commute on workdays
        double weeklyKm = in.annualKm() != null ? in.annualKm() / 52.0 : in.commuteKmPerDay() * WORKDAYS_PER_WEEK;
        double monthlyKm = in.annualKm() != null ? in.annualKm() / 12.0 : in.commuteKmPerDay() * WORKDAYS_PER_MONTH;
        double weeklyKwh = weeklyKm * mixedKwhPerKm;
        double kwhPerCharge = net.multiply(in.homeCharging() ? HOME_CHARGE_SHARE : STOP_SHARE).doubleValue();
        BigDecimal chargesPerWeek = BigDecimal.valueOf(Math.ceil(weeklyKwh / kwhPerCharge * 2) / 2)
                .setScale(1, RoundingMode.HALF_UP);
        // Consumption is battery side (charging losses removed), the price is per kWh at the plug
        BigDecimal efficiency = in.homeCharging() ? b.homeChargingEfficiency() : b.publicChargingEfficiency();
        double plugKwhPerKm = mixedKwhPerKm / efficiency.doubleValue();
        int monthlyCost = (int) Math.round(monthlyKm * plugKwhPerKm * price.doubleValue());

        return new PersonalRangeResponse(
                round10(km(net, RANGE_SHARE, winterProfile)),
                round10(km(net, RANGE_SHARE, summerProfile)),
                stops,
                chargingMinutes,
                chargesPerWeek,
                monthlyCost,
                dataLevel(assumptions),
                List.copyOf(assumptions));
    }

    /**
     * The visitor's range band before the gate: the two season ranges, rated on the season values alone.
     * Highway and base consumption do not enter the band, so they do not lower its level either.
     */
    static PersonalRangePreviewResponse preview(PersonalRangeRequest in, ModelRangeBasis b) {
        PersonalRangeResponse full = calculate(in, b);
        List<PersonalRangeResponse.Assumption> seasons = full.assumptions().stream()
                .filter(a -> a.key().equals("SUMMER_CONSUMPTION") || a.key().equals("WINTER_CONSUMPTION")).toList();
        return new PersonalRangePreviewResponse(full.winterRangeKm(), full.summerRangeKm(), dataLevel(seasons));
    }

    /**
     * The user's own price wins (OWN_PRICE, a fact of the user, not an estimate); otherwise the
     * community price of the model (PRICE measured) or the reference price (PRICE estimated).
     */
    private static BigDecimal price(PersonalRangeRequest in, ModelRangeBasis b,
                                    List<PersonalRangeResponse.Assumption> assumptions) {
        if (in.pricePerKwh() != null) {
            assumptions.add(new PersonalRangeResponse.Assumption("OWN_PRICE", true, in.pricePerKwh()));
            return in.pricePerKwh();
        }
        BigDecimal communityPrice = in.homeCharging() ? b.homePricePerKwh() : b.publicPricePerKwh();
        BigDecimal price = communityPrice != null ? communityPrice
                : in.homeCharging() ? b.referenceHomePricePerKwh() : b.referencePublicPricePerKwh();
        assumptions.add(new PersonalRangeResponse.Assumption("PRICE", communityPrice != null, price));
        return price;
    }

    private static BigDecimal factor(BigDecimal fleetFactor, BigDecimal fallback) {
        return fleetFactor != null ? fleetFactor : fallback;
    }

    private static BigDecimal pick(String key, BigDecimal measured, BigDecimal wltp, BigDecimal factor,
                                   List<PersonalRangeResponse.Assumption> assumptions) {
        BigDecimal value = measured != null ? measured : wltp.multiply(factor).setScale(1, RoundingMode.HALF_UP);
        assumptions.add(new PersonalRangeResponse.Assumption(key, measured != null, value));
        return value;
    }

    /**
     * Highway consumption blended by the number of cars behind the measurement: below
     * MIN_HIGHWAY_CARS the WLTP estimate only, from there half measured, rising linearly to fully
     * measured at FULL_HIGHWAY_CARS. The value moves towards the community as more people log.
     */
    private static BigDecimal highway(ModelRangeBasis b, List<PersonalRangeResponse.Assumption> assumptions) {
        BigDecimal estimate = b.wltpKwhPer100km().multiply(HIGHWAY_FACTOR).setScale(1, RoundingMode.HALF_UP);
        int cars = b.highwayCarCount();
        if (b.highwayKwhPer100km() == null || cars < MIN_HIGHWAY_CARS) {
            assumptions.add(new PersonalRangeResponse.Assumption("HIGHWAY_CONSUMPTION", false, estimate));
            return estimate;
        }
        int percent = Math.min(100, 50 + 50 * (cars - MIN_HIGHWAY_CARS) / (FULL_HIGHWAY_CARS - MIN_HIGHWAY_CARS));
        BigDecimal share = BigDecimal.valueOf(percent, 2);
        BigDecimal value = b.highwayKwhPer100km().multiply(share)
                .add(estimate.multiply(BigDecimal.ONE.subtract(share)))
                .setScale(1, RoundingMode.HALF_UP);
        assumptions.add(new PersonalRangeResponse.Assumption("HIGHWAY_CONSUMPTION", percent == 100, value, percent));
        return value;
    }

    /** Distance on a share of the net battery at a consumption in kWh/100 km. */
    private static double km(BigDecimal netKwh, BigDecimal share, BigDecimal kwhPer100km) {
        return netKwh.multiply(share).multiply(HUNDRED).doubleValue() / kwhPer100km.doubleValue();
    }

    private static int round10(double km) {
        return (int) Math.round(km / 10) * 10;
    }

    /** Only the four consumption values decide the level; battery, power and price are listed separately. */
    private static PersonalRangeResponse.DataLevel dataLevel(List<PersonalRangeResponse.Assumption> assumptions) {
        List<PersonalRangeResponse.Assumption> consumption = assumptions.stream()
                .filter(a -> a.key().endsWith("_CONSUMPTION")).toList();
        return levelOf(consumption);
    }

    private static PersonalRangeResponse.DataLevel levelOf(List<PersonalRangeResponse.Assumption> consumption) {
        long measured = consumption.stream().filter(PersonalRangeResponse.Assumption::measured).count();
        if (measured == consumption.size()) return PersonalRangeResponse.DataLevel.MEASURED;
        if (measured == 0) return PersonalRangeResponse.DataLevel.WLTP_FACTOR;
        return PersonalRangeResponse.DataLevel.PARTIAL;
    }
}
