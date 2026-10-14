package com.evmonitor.application.charging;

import java.math.BigDecimal;

/**
 * A long trip on the highway: how many fast-charging stops, how long they take and how long the
 * whole journey takes. First leg from 90 to 10 %, every further leg from 10 to 80 % (FastChargeStop),
 * travel at a fixed average speed. Minutes of charging and of the whole journey are rounded to 5.
 */
public record LongTripPlan(int stops, int chargingMinutes, int drivingMinutes, int totalMinutes) {

    /** Average travel speed of an electric car on a long trip, traffic included. */
    public static final int TRAVEL_SPEED_KMH = 110;
    /** Start at 90 %, arrive at the first charger with 10 %. */
    static final BigDecimal FIRST_LEG_SHARE = new BigDecimal("0.8");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static LongTripPlan of(BigDecimal netKwh, BigDecimal highwayKwhPer100km, BigDecimal powerKw, int km) {
        double firstLegKm = legKm(netKwh, FIRST_LEG_SHARE, highwayKwhPer100km);
        double legKm = legKm(netKwh, FastChargeStop.SHARE, highwayKwhPer100km);
        int stops = km <= firstLegKm ? 0 : (int) Math.ceil((km - firstLegKm) / legKm);
        double minutesPerStop = netKwh.multiply(FastChargeStop.SHARE).doubleValue() / powerKw.doubleValue() * 60;
        int chargingMinutes = roundTo5(stops * minutesPerStop);
        int drivingMinutes = (int) Math.round(km * 60.0 / TRAVEL_SPEED_KMH);
        return new LongTripPlan(stops, chargingMinutes, drivingMinutes, roundTo5(drivingMinutes + stops * minutesPerStop));
    }

    /** How far the car gets on the highway with the given number of stops: first leg plus one leg per stop. */
    public static double reachKm(BigDecimal netKwh, BigDecimal highwayKwhPer100km, int stops) {
        return legKm(netKwh, FIRST_LEG_SHARE, highwayKwhPer100km) + stops * legKm(netKwh, FastChargeStop.SHARE, highwayKwhPer100km);
    }

    private static double legKm(BigDecimal netKwh, BigDecimal share, BigDecimal kwhPer100km) {
        return netKwh.multiply(share).multiply(HUNDRED).doubleValue() / kwhPer100km.doubleValue();
    }

    private static int roundTo5(double minutes) {
        return (int) (Math.round(minutes / 5.0) * 5);
    }
}
