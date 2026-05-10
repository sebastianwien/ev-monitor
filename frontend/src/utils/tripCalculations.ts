/**
 * Pure helpers for derived trip / trip-group metrics shown in the LogFeed.
 *
 * Kept free of Vue-specific imports so the logic is testable in isolation
 * and so the caller controls when to recompute (typically inside a `computed`).
 */

export interface TripForCalc {
  id: string
  tripStartedAt: string | Date | null
  tripEndedAt: string | Date | null
  distanceKm: number | null
  socStart: number | null
  socEnd: number | null
  estimatedConsumedKwh?: number | null
}

export interface ChargeLogForCalc {
  loggedAt: string | Date | null
  costEur: number | null
  kwhCharged: number | null
  kwhAtVehicle: number | null
}

export interface ConsumptionResult {
  kwhPer100km: number
  estimated: boolean
}

const toMillis = (d: string | Date | null | undefined): number | null => {
  if (d == null) return null
  if (d instanceof Date) return d.getTime()
  const ms = new Date(d).getTime()
  return Number.isFinite(ms) ? ms : null
}

/**
 * Per-trip consumption in kWh/100km. Mirrors the previous inline helper:
 *   - prefer estimatedConsumedKwh / distance
 *   - fall back to (socStart - socEnd) * capacity / distance
 *   - return null if the trip lacks data
 *
 * `estimated` is true when the SoC delta source had integer-percent precision
 * (so the result has a wider error band).
 */
export function tripConsumption(
  trip: TripForCalc,
  effectiveBatteryCapacityKwh: number | null | undefined,
): ConsumptionResult | null {
  if (!trip.distanceKm || trip.distanceKm <= 0) return null
  if (trip.estimatedConsumedKwh != null) {
    return { kwhPer100km: (trip.estimatedConsumedKwh / trip.distanceKm) * 100, estimated: false }
  }
  if (
    trip.socStart != null
    && trip.socEnd != null
    && trip.socStart > trip.socEnd
    && effectiveBatteryCapacityKwh
  ) {
    const isDecimalSoc = trip.socStart % 1 !== 0 || trip.socEnd % 1 !== 0
    return {
      kwhPer100km: ((trip.socStart - trip.socEnd) * effectiveBatteryCapacityKwh) / trip.distanceKm,
      estimated: !isDecimalSoc,
    }
  }
  return null
}

/**
 * Pre-process charge logs once per render: filter to those with a usable cost,
 * sort ascending by timestamp. Subsequent per-trip lookups can then walk this
 * list and stop early.
 */
export function prepareCostLookup(logs: ChargeLogForCalc[]): { ts: number; cpk: number }[] {
  const out: { ts: number; cpk: number }[] = []
  for (const log of logs) {
    const baseKwh = log.kwhCharged ?? log.kwhAtVehicle
    if (log.costEur == null || !baseKwh || baseKwh <= 0) continue
    const ts = toMillis(log.loggedAt)
    if (ts == null) continue
    out.push({ ts, cpk: log.costEur / baseKwh })
  }
  out.sort((a, b) => a.ts - b.ts)
  return out
}

/**
 * Look up the cost-per-kWh of the most recent charge that ended before the trip
 * started. `sortedLogs` must come from prepareCostLookup() (sorted ascending).
 * Falls back to fallbackCpk when no preceding charge exists.
 */
export function lookupCostPerKwhForTrip(
  trip: Pick<TripForCalc, 'tripStartedAt'>,
  sortedLogs: { ts: number; cpk: number }[],
  fallbackCpk: number | null,
): number | null {
  const tripStart = toMillis(trip.tripStartedAt)
  if (tripStart == null || sortedLogs.length === 0) return fallbackCpk
  // Binary search for largest ts < tripStart
  let lo = 0
  let hi = sortedLogs.length - 1
  let best = -1
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1
    if (sortedLogs[mid].ts < tripStart) {
      best = mid
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  return best >= 0 ? sortedLogs[best].cpk : fallbackCpk
}

/**
 * Build a Map<tripId, cost-per-kWh> in one pass for O(N + M log M) overall
 * instead of O(N × M) when the template would naively re-scan logs per trip.
 */
export function buildTripCostPerKwhMap(
  trips: Pick<TripForCalc, 'id' | 'tripStartedAt'>[],
  logs: ChargeLogForCalc[],
  fallbackCpk: number | null,
): Map<string, number | null> {
  const sorted = prepareCostLookup(logs)
  const map = new Map<string, number | null>()
  for (const trip of trips) {
    map.set(trip.id, lookupCostPerKwhForTrip(trip, sorted, fallbackCpk))
  }
  return map
}

/**
 * Sum of consumed energy (kWh) across all trips in a group.
 * Returns null if no trip has both consumption and distance.
 */
export function tripGroupConsumedKwh(
  trips: TripForCalc[],
  effectiveBatteryCapacityKwh: number | null | undefined,
): number | null {
  let total = 0
  let any = false
  for (const trip of trips) {
    const c = tripConsumption(trip, effectiveBatteryCapacityKwh)
    if (c && trip.distanceKm) {
      total += (c.kwhPer100km * trip.distanceKm) / 100
      any = true
    }
  }
  return any ? total : null
}

/**
 * SoC boundaries of a trip cluster: socStart of the oldest trip → socEnd of the
 * newest trip. Clusters span a contiguous driving period between charges, so
 * this is the net battery use across the cluster.
 */
export function tripGroupSocBoundaries(trips: TripForCalc[]): { start: number; end: number } | null {
  if (trips.length === 0) return null
  let oldest = trips[0]
  let newest = trips[0]
  for (const trip of trips) {
    const tripStart = toMillis(trip.tripStartedAt)
    const oldestStart = toMillis(oldest.tripStartedAt)
    if (tripStart != null && oldestStart != null && tripStart < oldestStart) oldest = trip
    const tripEnd = toMillis(trip.tripEndedAt)
    const newestEnd = toMillis(newest.tripEndedAt)
    if (tripEnd != null && newestEnd != null && tripEnd > newestEnd) newest = trip
  }
  if (oldest.socStart == null || newest.socEnd == null) return null
  return { start: oldest.socStart, end: newest.socEnd }
}

/**
 * Aggregate €/100km for a trip group, weighted by per-trip distance.
 * Uses the precomputed cpkMap so it doesn't rescan logs.
 */
export function tripGroupCostPer100km(
  trips: TripForCalc[],
  cpkMap: Map<string, number | null>,
  effectiveBatteryCapacityKwh: number | null | undefined,
): number | null {
  let totalCost = 0
  let totalDist = 0
  for (const trip of trips) {
    const c = tripConsumption(trip, effectiveBatteryCapacityKwh)
    const cpk = cpkMap.get(trip.id)
    if (c && cpk != null && trip.distanceKm) {
      totalCost += (c.kwhPer100km * cpk * trip.distanceKm) / 100
      totalDist += trip.distanceKm
    }
  }
  if (totalDist === 0) return null
  return (totalCost * 100) / totalDist
}
