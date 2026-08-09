package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Explains why a car has no auto-detected SoH yet. Without this the UI can only show an
 * empty chart, which reads like a broken feature rather than a missing precondition.
 *
 * @param requiredSocHubPercent  SoC percentage points a single charge must add to qualify
 * @param largestSocHubPercent   largest hub the car ever recorded, null if it has no usable log
 * @param qualifyingChargeCount  charges currently meeting the requirement, capped at the
 *                               median window size - beyond that the exact number carries
 *                               no meaning, since only the window feeds the estimate
 * @param capacityKnown          false if the car has no nominal net capacity, in which case
 *                               no amount of charging can produce a SoH value
 */
public record BatterySohStatusResponse(
        int requiredSocHubPercent,
        BigDecimal largestSocHubPercent,
        int qualifyingChargeCount,
        boolean capacityKnown
) {}
