package com.evmonitor.application.personalrange;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * The calculator inputs. Also stored as the user's range profile (app_user.range_profile)
 * so other model pages open pre-filled.
 *
 * @param pricePerKwh  the user's own electricity price; null means the community price of the model
 * @param netBatteryKwh the battery variant the page shows; null means the largest WLTP variant
 * @param annualKm      yearly mileage; when given, monthly cost and charges per week rest on it
 *                      instead of 22 commute days
 */
public record PersonalRangeRequest(
        @Min(0) @Max(500) int commuteKmPerDay,
        @Min(0) @Max(100) int highwaySharePercent,
        boolean homeCharging,
        @Min(50) @Max(2000) int longTripKm,
        @DecimalMin("0.05") @DecimalMax("1.50") BigDecimal pricePerKwh,
        @DecimalMin("10") @DecimalMax("250") BigDecimal netBatteryKwh,
        @Min(1000) @Max(100000) Integer annualKm
) {
    public PersonalRangeRequest(int commuteKmPerDay, int highwaySharePercent, boolean homeCharging, int longTripKm,
                                BigDecimal pricePerKwh, BigDecimal netBatteryKwh) {
        this(commuteKmPerDay, highwaySharePercent, homeCharging, longTripKm, pricePerKwh, netBatteryKwh, null);
    }

    public PersonalRangeRequest(int commuteKmPerDay, int highwaySharePercent, boolean homeCharging, int longTripKm,
                                BigDecimal pricePerKwh) {
        this(commuteKmPerDay, highwaySharePercent, homeCharging, longTripKm, pricePerKwh, null, null);
    }

    /** Without an own price: the community price applies. */
    public PersonalRangeRequest(int commuteKmPerDay, int highwaySharePercent, boolean homeCharging, int longTripKm) {
        this(commuteKmPerDay, highwaySharePercent, homeCharging, longTripKm, null, null);
    }
}
