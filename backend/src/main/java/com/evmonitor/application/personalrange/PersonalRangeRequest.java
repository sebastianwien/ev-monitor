package com.evmonitor.application.personalrange;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The four calculator inputs. Also stored as the user's range profile (app_user.range_profile)
 * so other model pages open pre-filled.
 */
public record PersonalRangeRequest(
        @Min(0) @Max(500) int commuteKmPerDay,
        @Min(0) @Max(100) int highwaySharePercent,
        boolean homeCharging,
        @Min(50) @Max(2000) int longTripKm
) {}
