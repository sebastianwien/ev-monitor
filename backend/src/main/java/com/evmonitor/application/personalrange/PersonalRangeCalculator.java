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

    static final BigDecimal SUMMER_FACTOR = new BigDecimal("1.15");
    static final BigDecimal WINTER_FACTOR = new BigDecimal("1.35");
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

        BigDecimal summer = pick("SUMMER_CONSUMPTION", b.summerKwhPer100km(), b.wltpKwhPer100km(), SUMMER_FACTOR, assumptions);
        BigDecimal winter = pick("WINTER_CONSUMPTION", b.winterKwhPer100km(), b.wltpKwhPer100km(), WINTER_FACTOR, assumptions);
        BigDecimal highway = highway(b, assumptions);
        BigDecimal base = pick("BASE_CONSUMPTION", b.baseKwhPer100km(), b.wltpKwhPer100km(), SUMMER_FACTOR, assumptions);

        BigDecimal power = b.dcChargingPowerKw() != null ? b.dcChargingPowerKw() : ASSUMED_DC_POWER_KW;
        assumptions.add(new PersonalRangeResponse.Assumption("CHARGING_POWER", b.dcChargingPowerKw() != null, power));

        BigDecimal communityPrice = in.homeCharging() ? b.homePricePerKwh() : b.publicPricePerKwh();
        BigDecimal price = communityPrice != null ? communityPrice
                : in.homeCharging() ? b.referenceHomePricePerKwh() : b.referencePublicPricePerKwh();
        assumptions.add(new PersonalRangeResponse.Assumption("PRICE", communityPrice != null, price));

        // Long trip on the highway value: first leg 90 → 10 %, every further leg 10 → 80 %
        double firstLegKm = km(net, FIRST_LEG_SHARE, highway);
        double legKm = km(net, STOP_SHARE, highway);
        int stops = in.longTripKm() <= firstLegKm ? 0 : (int) Math.ceil((in.longTripKm() - firstLegKm) / legKm);
        double minutesPerStop = net.multiply(STOP_SHARE).doubleValue() / power.doubleValue() * 60;
        int chargingMinutes = (int) (Math.round(stops * minutesPerStop / 5.0) * 5);

        // Commute: base and highway consumption blended by the highway share
        double h = in.highwaySharePercent() / 100.0;
        double mixedKwhPerKm = (base.doubleValue() * (1 - h) + highway.doubleValue() * h) / 100;
        double weeklyKwh = in.commuteKmPerDay() * WORKDAYS_PER_WEEK * mixedKwhPerKm;
        double kwhPerCharge = net.multiply(in.homeCharging() ? HOME_CHARGE_SHARE : STOP_SHARE).doubleValue();
        BigDecimal chargesPerWeek = BigDecimal.valueOf(Math.ceil(weeklyKwh / kwhPerCharge * 2) / 2)
                .setScale(1, RoundingMode.HALF_UP);
        int monthlyCost = (int) Math.round(in.commuteKmPerDay() * WORKDAYS_PER_MONTH * mixedKwhPerKm * price.doubleValue());

        return new PersonalRangeResponse(
                round10(km(net, RANGE_SHARE, winter)),
                round10(km(net, RANGE_SHARE, summer)),
                stops,
                chargingMinutes,
                chargesPerWeek,
                monthlyCost,
                dataLevel(assumptions),
                List.copyOf(assumptions));
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
        long measured = consumption.stream().filter(PersonalRangeResponse.Assumption::measured).count();
        if (measured == consumption.size()) return PersonalRangeResponse.DataLevel.MEASURED;
        if (measured == 0) return PersonalRangeResponse.DataLevel.WLTP_FACTOR;
        return PersonalRangeResponse.DataLevel.PARTIAL;
    }
}
