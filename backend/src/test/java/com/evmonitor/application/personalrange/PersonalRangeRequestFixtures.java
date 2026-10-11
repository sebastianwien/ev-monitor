package com.evmonitor.application.personalrange;

/** The four core answers without own price, battery and yearly mileage, for tests only. */
public final class PersonalRangeRequestFixtures {

    private PersonalRangeRequestFixtures() {}

    public static PersonalRangeRequest answers(int commuteKmPerDay, int highwaySharePercent, boolean homeCharging, int longTripKm) {
        return new PersonalRangeRequest(commuteKmPerDay, highwaySharePercent, homeCharging, longTripKm, null, null, null);
    }
}
