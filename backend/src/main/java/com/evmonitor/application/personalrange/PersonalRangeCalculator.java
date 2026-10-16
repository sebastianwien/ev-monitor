package com.evmonitor.application.personalrange;

import com.evmonitor.application.charging.FastChargePower;
import com.evmonitor.application.charging.FastChargeStop;
import com.evmonitor.application.charging.LongTripPlan;
import com.evmonitor.application.range.RangeWindow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure calculation of the personal range result. No consumption formula of its own: all
 * consumption values come from EvLogService results (via ModelRangeBasis) or, where the
 * community has too little data, from WLTP times a fleet factor (highway: base times highway ratio).
 */
final class PersonalRangeCalculator {

    /**
     * Fallbacks when the fleet has too few models for a median (WltpFleetFactors). Summer, winter and
     * overall match the fleet medians measured on 11.10.2026 (1.13, 1.49, 1.21, rounded up for summer).
     * Highway has no WLTP factor: its estimate is the base consumption times the highway ratio.
     */
    static final BigDecimal SUMMER_FACTOR = new BigDecimal("1.15");
    static final BigDecimal WINTER_FACTOR = new BigDecimal("1.49");
    static final BigDecimal OVERALL_FACTOR = new BigDecimal("1.21");
    /**
     * Highway against the route-typed mean where a model has too few highway cars: median over the
     * models with enough data, measured on 11.10.2026 (ID.3 +7 %, Model 3 +2 %, Model Y +3 %, EV6 +3 %,
     * ID.4 +9 %, Ioniq 5 -3 %).
     */
    static final BigDecimal HIGHWAY_RATIO_FALLBACK = new BigDecimal("1.03");
    /** Same as the model page and the model list: range = 100 to 10 % of the net battery. */
    private static final BigDecimal RANGE_SHARE = RangeWindow.SHARE;
    /** Charging away from home without a home charger: a fast-charging stop's share. */
    private static final BigDecimal STOP_SHARE = FastChargeStop.SHARE;
    /** Home charging from 20 to 80 %. */
    private static final BigDecimal HOME_CHARGE_SHARE = new BigDecimal("0.6");
    /** Highway measurement counts from this many cars (half weight) and fully from FULL_HIGHWAY_CARS. */
    static final int MIN_HIGHWAY_CARS = 5;
    static final int FULL_HIGHWAY_CARS = 15;
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
        BigDecimal base = pick("BASE_CONSUMPTION", b.baseKwhPer100km(), b.wltpKwhPer100km(),
                factor(fleet != null ? fleet.overall() : null, OVERALL_FACTOR), assumptions);
        BigDecimal highway = highway(b, base, assumptions);

        FastChargePower power = b.dcChargingPower() != null ? b.dcChargingPower() : FastChargePower.fallback();
        if (power.isReliable()) {
            assumptions.add(new PersonalRangeResponse.Assumption("CHARGING_POWER", power.isMeasured(), power.kw()));
        }

        BigDecimal price = price(in, b, assumptions);

        LongTripPlan trip = LongTripPlan.of(net, highway, power.kw(), in.longTripKm());
        // Charging and travel time only on a measured or datasheet power: the fleet median made slow chargers
        // look fast. The stops rest on battery and consumption alone; without a stop the time is the drive.
        boolean timed = power.isReliable() || trip.stops() == 0;
        if (timed) {
            // A fixed setting, listed so the page and the mail can name it next to the travel time
            assumptions.add(new PersonalRangeResponse.Assumption("TRAVEL_SPEED", false, BigDecimal.valueOf(LongTripPlan.TRAVEL_SPEED_KMH)));
        }

        // Commute: base and highway consumption blended by the highway share
        double h = in.highwaySharePercent() / 100.0;
        double mixedKwhPerKm = (base.doubleValue() * (1 - h) + highway.doubleValue() * h) / 100;
        // Season ranges for the profile: the highway effect on the share of motorway driving, applied to
        // the season value. 0 % motorway gives the model's own season ranges, the figures the page shows;
        // the band before the gate rests on the same two values.
        double highwayRatio = 1 + h * (highwayRatio(b).doubleValue() - 1);
        BigDecimal summerProfile = summer.multiply(BigDecimal.valueOf(highwayRatio)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal winterProfile = winter.multiply(BigDecimal.valueOf(highwayRatio)).setScale(1, RoundingMode.HALF_UP);
        // Mileage: the yearly figure when the reader gave one, else the commute on the reader's commute days
        double commuteKmPerWeek = in.commuteKmPerDay() * in.commuteDays();
        double weeklyKm = in.annualKm() != null ? in.annualKm() / 52.0 : commuteKmPerWeek;
        double monthlyKm = monthlyKm(in);
        double weeklyKwh = weeklyKm * mixedKwhPerKm;
        double kwhPerCharge = net.multiply(in.homeCharging() ? HOME_CHARGE_SHARE : STOP_SHARE).doubleValue();
        BigDecimal chargesPerWeek = halfCharges(weeklyKwh / kwhPerCharge);
        // The commute on the reader's commute days, per season: how often they plug in in summer and in
        // winter. Not rounded, the page turns small values into "every 10 days".
        BigDecimal chargesSummer = charges(commuteKmPerWeek * summerProfile.doubleValue() / 100 / kwhPerCharge);
        BigDecimal chargesWinter = charges(commuteKmPerWeek * winterProfile.doubleValue() / 100 / kwhPerCharge);
        // Consumption is battery side (charging losses removed), the price is per kWh at the plug
        BigDecimal efficiency = in.homeCharging() ? b.homeChargingEfficiency() : b.publicChargingEfficiency();
        double plugKwhPerKm = mixedKwhPerKm / efficiency.doubleValue();
        int monthlyCost = (int) Math.round(monthlyKm * plugKwhPerKm * price.doubleValue());

        return new PersonalRangeResponse(
                round10(km(net, RANGE_SHARE, winterProfile)),
                round10(km(net, RANGE_SHARE, summerProfile)),
                trip.stops(),
                timed ? trip.chargingMinutes() : null,
                timed ? trip.totalMinutes() : null,
                chargesPerWeek,
                chargesSummer,
                chargesWinter,
                monthlyCost,
                dataLevel(assumptions),
                List.copyOf(assumptions));
    }

    /** The ranking row of a model: the model page result plus the stop ladder on the same highway consumption. */
    static PersonalRangeRankingEntry rankingEntry(String brand, String model, PersonalRangeRequest in, ModelRangeBasis b) {
        PersonalRangeResponse r = calculate(in, b);
        BigDecimal highway = r.assumptions().stream().filter(a -> a.key().equals("HIGHWAY_CONSUMPTION"))
                .findFirst().orElseThrow().value();
        return new PersonalRangeRankingEntry(brand, model, r.summerRangeKm(), r.winterRangeKm(),
                r.longTripStops(), r.longTripTotalMinutes(), r.chargesPerWeekSummer(), r.chargesPerWeekWinter(),
                r.monthlyCostEur(),
                (int) Math.round(monthlyKm(in)),
                round10(LongTripPlan.reachKm(b.netBatteryKwh(), highway, 0)),
                round10(LongTripPlan.reachKm(b.netBatteryKwh(), highway, 1)),
                round10(LongTripPlan.reachKm(b.netBatteryKwh(), highway, 2)));
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

    /**
     * Measured highway against the mean of the route-typed trips, both from the same period. It can be
     * below 1: a relaxed motorway against brisk city driving. Under MIN_HIGHWAY_CARS the fleet median,
     * never a WLTP estimate against a measured value.
     */
    static BigDecimal highwayRatio(ModelRangeBasis b) {
        if (b.highwayKwhPer100km() == null || b.highwayCarCount() < MIN_HIGHWAY_CARS
                || b.routeMeanKwhPer100km() == null || b.routeMeanKwhPer100km().signum() <= 0) {
            return HIGHWAY_RATIO_FALLBACK;
        }
        return b.highwayKwhPer100km().divide(b.routeMeanKwhPer100km(), 4, RoundingMode.HALF_UP);
    }

    /** The month's distance: the yearly figure when given, else the commute (22 workdays a month on 5 days, so 4.4 weeks). */
    private static double monthlyKm(PersonalRangeRequest in) {
        return in.annualKm() != null ? in.annualKm() / 12.0
                : (double) in.commuteKmPerDay() * in.commuteDays() * WORKDAYS_PER_MONTH / WORKDAYS_PER_WEEK;
    }

    private static BigDecimal charges(double charges) {
        return BigDecimal.valueOf(charges).setScale(2, RoundingMode.HALF_UP);
    }

    /** Rounded up to half charges: 1.2 charges a week means plugging in 1.5 times */
    private static BigDecimal halfCharges(double charges) {
        return BigDecimal.valueOf(Math.ceil(charges * 2) / 2).setScale(1, RoundingMode.HALF_UP);
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
     * MIN_HIGHWAY_CARS the estimate only, from there half measured, rising linearly to fully
     * measured at FULL_HIGHWAY_CARS. The value moves towards the community as more people log.
     * The estimate is the base consumption (drivers' mean, else WLTP times the fleet factor) times the
     * highway ratio: a model driven far below its WLTP figure stays below it on the motorway too.
     */
    private static BigDecimal highway(ModelRangeBasis b, BigDecimal base, List<PersonalRangeResponse.Assumption> assumptions) {
        BigDecimal estimate = base.multiply(highwayRatio(b)).setScale(1, RoundingMode.HALF_UP);
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
