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

/**
 * Tageswechsel zwischen zwei aufeinanderfolgenden Trips einer Fahrtgruppe.
 * Trips sind absteigend sortiert (neuester zuerst). Verglichen werden die Startdaten, denn
 * der Trenner beschriftet den Trip darunter mit dessen tripStartedAt. Wuerde stattdessen das
 * Ende des Vorgaengers zaehlen, loeste ein Trip ueber Mitternacht denselben Trenner zweimal
 * aus. Ohne Startzeitstempel gibt es keinen Trenner.
 */
export function isDayBoundary(
  trips: { tripStartedAt?: string | null }[],
  idx: number,
): boolean {
  if (idx <= 0) return false
  const prev = trips[idx - 1]?.tripStartedAt
  const current = trips[idx]?.tripStartedAt
  if (!prev || !current) return false
  return new Date(prev).toDateString() !== new Date(current).toDateString()
}

/** Fahrten, die dichter aufeinander folgen, lesen sich als ein Vorgang statt als zwei. */
const CHAIN_PAUSE_MINUTES = 45

type TripTimes = { tripStartedAt?: string | null; tripEndedAt?: string | null }

/**
 * Ruhe vor dieser Fahrt, in Minuten - die Zeit, die das Auto stand.
 *
 * Trips sind absteigend sortiert (neuester zuerst), die frueher gefahrene Fahrt steht also
 * beim hoeheren Index. Fehlt deren Ankunft, zaehlt ersatzweise ihre Abfahrt: sie liegt
 * immer davor, die Pause faellt damit hoechstens zu gross aus, nie zu klein.
 *
 * @returns null fuer die aelteste Fahrt der Gruppe und bei fehlenden Zeitstempeln
 */
export function pauseBeforeTripMinutes(trips: TripTimes[], idx: number): number | null {
  const current = trips[idx]?.tripStartedAt
  const earlier = trips[idx + 1]
  if (!current || !earlier) return null
  const earlierEnd = earlier.tripEndedAt ?? earlier.tripStartedAt
  if (!earlierEnd) return null
  const minutes = (new Date(current).getTime() - new Date(earlierEnd).getTime()) / 60000
  return Number.isFinite(minutes) ? Math.round(minutes) : null
}

/**
 * Ob unter dieser Fahrt eine echte Standzeit liegt - mindestens {@link CHAIN_PAUSE_MINUTES}
 * Minuten, in denen das Auto stand.
 *
 * Bewusst ohne den Tageswechsel: der bekommt im Feed sein eigenes Datumsband. Beides auf
 * dieselbe graue Zeile zu legen hiesse, dass Mitternacht und ein Einkaufsstopp gleich
 * aussehen - dann sagt die Zeile nichts mehr.
 *
 * Innerhalb eines Tages gilt: ist die Pause nicht bestimmbar, gibt es auch keine Angabe.
 */
export function isRestBreak(trips: TripTimes[], idx: number): boolean {
  if (idx >= trips.length - 1) return false
  const pause = pauseBeforeTripMinutes(trips, idx)
  return pause !== null && pause >= CHAIN_PAUSE_MINUTES
}

/** Ein Tag innerhalb einer Fahrtgruppe - die zweite Ebene des Log-Feeds. */
export interface TripDay<T = any> {
  /** Stabil ueber Rendervorgaenge hinweg, dient als Schluessel fuer Liste und Zustand. */
  dateKey: string
  trips: T[]
  tripCount: number
  km: number
}

/**
 * Zerlegt die Fahrten einer Gruppe in Tage, ohne ihre Reihenfolge anzutasten - die Liste ist
 * bereits absteigend sortiert, die Tage folgen ihr.
 *
 * Massgeblich ist die Abfahrt: eine Fahrt ueber Mitternacht gehoert zu dem Tag, an dem sie
 * begonnen hat. Sonst eroeffnete ihre Ankunft einen Tag, an dem niemand losgefahren ist.
 * Fahrten ohne Startzeit landen in einem eigenen Abschnitt, statt zu verschwinden.
 */
export function groupTripsByDay<T extends { tripStartedAt?: string | null; distanceKm?: number | null }>(
  trips: T[],
): TripDay<T>[] {
  const days: TripDay<T>[] = []
  for (const trip of trips) {
    const dateKey = trip.tripStartedAt ? trip.tripStartedAt.slice(0, 10) : 'unknown'
    const current = days[days.length - 1]
    if (current && current.dateKey === dateKey) current.trips.push(trip)
    else days.push({ dateKey, trips: [trip], tripCount: 0, km: 0 })
  }
  for (const day of days) {
    day.tripCount = day.trips.length
    day.km = day.trips.reduce((sum, t) => sum + (t.distanceKm ?? 0), 0)
  }
  return days
}
