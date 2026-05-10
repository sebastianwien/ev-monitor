/**
 * Mirrors the backend's plausibility.minTripDistanceKm threshold (10 km).
 * Below this, SoC granularity (±1%) and capacity assumptions distort the
 * calculated kWh/100km value far beyond useful precision, so the backend
 * filters such logs out of `consumptionKwhPer100km`.
 */
export const SHORT_TRIP_THRESHOLD_KM = 10

/**
 * True when an EV log entry has a measured but too-short distance for a
 * reliable per-log consumption calculation. The frontend uses this to render
 * an explanatory hint instead of an empty consumption value.
 *
 * Returns false when distance is missing, zero, negative, or at/above the
 * threshold.
 */
export function isShortTrip(entry: { distanceSinceLastChargeKm?: number | null }): boolean {
  const dist = entry.distanceSinceLastChargeKm
  if (dist == null) return false
  return dist > 0 && dist < SHORT_TRIP_THRESHOLD_KM
}
