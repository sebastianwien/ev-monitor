/**
 * Pure helpers for the "letzter Ladevorgang / letzte Fahrt" dashboard block.
 *
 * The dashboard reuses the already merged + descending-sorted `mergedLogFeed`
 * (charges, Ladegruppen and trips interleaved). These helpers pick the newest
 * charge / trip and flatten the two charge shapes (single log vs Ladegruppe
 * aggregate) into one display model, so the component stays presentational.
 *
 * Kept free of Vue imports so the branching logic is unit-testable in isolation.
 */

export interface FeedEntryLike {
  _isTrip?: boolean
  _isLadegruppe?: boolean
}

/** Coerce API decimals (which may arrive as strings) to a finite number or null. */
function num(v: unknown): number | null {
  if (v == null) return null
  const n = typeof v === 'string' ? parseFloat(v) : (v as number)
  return typeof n === 'number' && Number.isFinite(n) ? n : null
}

/** First charge / Ladegruppe entry in a descending-sorted feed. */
export function latestChargeEntry<T extends FeedEntryLike>(feed: T[] | null | undefined): T | null {
  if (!Array.isArray(feed)) return null
  return feed.find((e) => !e._isTrip) ?? null
}

/** First trip entry in a descending-sorted feed. */
export function latestTripEntry<T extends FeedEntryLike>(feed: T[] | null | undefined): T | null {
  if (!Array.isArray(feed)) return null
  return feed.find((e) => !!e._isTrip) ?? null
}

export interface NormalizedCharge {
  id: string | number
  loggedAt: string | null
  /** Energy that reached the car (netto-first), matching the log-feed "+X kWh" display. */
  kwh: number | null
  /** Energy drawn from the grid (brutto), when measured. Null when only netto exists. */
  kwhGross: number | null
  costEur: number | null
  /**
   * Price per kWh, divided by the SAME basis the cost was billed on (brutto-first,
   * mirroring EvLog.costBasisKwh on the backend). Dividing by netto instead would
   * report a ct/kWh above the tariff the user actually configured.
   */
  costPerKwh: number | null
  socBefore: number | null
  socAfter: number | null
  maxPowerKw: number | null
  chargingType: string | null
  dataSource: string | null
  durationMinutes: number | null
  /** Coarse charging location (6 chars ~600 m private, 7 ~150 m public). Null if unknown. */
  geohash: string | null
  isGroup: boolean
}

/**
 * Flatten a charge feed entry into a single display model. A Ladegruppe carries
 * pre-aggregated `_total*` / `_max*` fields; a single log carries the raw
 * per-charge fields. Mirrors the aggregation the log feed already performs.
 */
export function normalizeCharge(e: any): NormalizedCharge | null {
  if (!e) return null
  const isGroup = !!e._isLadegruppe
  const kwh = isGroup ? num(e._totalKwh) : (num(e.kwhAtVehicle) ?? num(e.kwhCharged))
  const kwhGross = isGroup ? num(e._totalKwhGross) : num(e.kwhCharged)
  const costEur = isGroup ? num(e._totalCostEur) : num(e.costEur)
  const costBasisKwh = kwhGross != null && kwhGross > 0 ? kwhGross : kwh
  // Eine Ladegruppe bringt ihre eigene Abrechnungsbasis mit (_costBasisKwh summiert nur
  // die Teilvorgaenge, die auch Kosten tragen) - die Netto/Brutto-Summen der Kachel waeren
  // hier der falsche Divisor.
  const basis = isGroup ? num(e._costBasisKwh) : costBasisKwh
  const costPerKwh =
    costEur != null && basis != null && basis > 0 ? costEur / basis : null
  return {
    id: e.id,
    loggedAt: e.loggedAt ?? null,
    kwh,
    kwhGross,
    costEur,
    costPerKwh,
    socBefore: num(e.socBeforeChargePercent),
    socAfter: isGroup ? num(e._maxSoc) : num(e.socAfterChargePercent),
    maxPowerKw: isGroup ? num(e._maxPower) : num(e.maxChargingPowerKw),
    chargingType: e.chargingType ?? null,
    dataSource: isGroup ? (e._commonDataSource ?? e.dataSource ?? null) : (e.dataSource ?? null),
    durationMinutes: isGroup ? null : num(e.chargeDurationMinutes),
    // Bei einer Ladegruppe tragen nicht alle Einzelvorgaenge einen Ort - der erste mit
    // Ort steht fuer die Gruppe, sie fand ohnehin an derselben Saeule statt.
    geohash: isGroup
      ? ((e._entries ?? []).find((entry: any) => entry?.geohash)?.geohash ?? null)
      : (e.geohash ?? null),
    isGroup,
  }
}

/**
 * Parts for `Intl.RelativeTimeFormat` describing how long ago `fromMs` was.
 * Negative `value` = past. Returns the coarsest fitting unit (second → year),
 * so the component can render a native, localized "vor 3 Stunden" / "gestern".
 */
export function relativeTimeParts(
  fromMs: number,
  nowMs: number,
): { value: number; unit: Intl.RelativeTimeFormatUnit } | null {
  if (!Number.isFinite(fromMs) || !Number.isFinite(nowMs)) return null
  const diffSec = Math.round((fromMs - nowMs) / 1000)
  const abs = Math.abs(diffSec)
  const MIN = 60
  const HOUR = 3600
  const DAY = 86400
  const WEEK = 604800
  const MONTH = 2592000 // 30 days
  const YEAR = 31536000 // 365 days
  if (abs < MIN) return { value: diffSec, unit: 'second' }
  if (abs < HOUR) return { value: Math.round(diffSec / MIN), unit: 'minute' }
  if (abs < DAY) return { value: Math.round(diffSec / HOUR), unit: 'hour' }
  if (abs < WEEK) return { value: Math.round(diffSec / DAY), unit: 'day' }
  if (abs < MONTH) return { value: Math.round(diffSec / WEEK), unit: 'week' }
  if (abs < YEAR) return { value: Math.round(diffSec / MONTH), unit: 'month' }
  return { value: Math.round(diffSec / YEAR), unit: 'year' }
}

/**
 * The timestamp that answers "when did this trip happen": its end. A drive is placed in time by
 * when it finished - a long trip started hours ago is not "vor 3 Stunden" news. Falls back to the
 * start while the trip is still running and has no end yet.
 */
export function tripTimestamp(
  trip: { tripStartedAt?: string | null; tripEndedAt?: string | null } | null | undefined,
): string | null {
  return trip?.tripEndedAt ?? trip?.tripStartedAt ?? null
}

/**
 * i18n key + args for a trip's speed line. Each value is only mentioned when it exists - a trip
 * without VehicleSpeed samples has no maximum, and rendering "max 0 km/h" would state a wrong
 * measurement rather than an absent one.
 */
export function tripSpeedKeyAndArgs(
  avgSpeedKmh: number | null | undefined,
  maxSpeedKmh: number | null | undefined,
): { key: string; args: Record<string, number> } | null {
  const avg = avgSpeedKmh != null ? Math.round(Number(avgSpeedKmh)) : null
  const max = maxSpeedKmh != null ? Math.round(Number(maxSpeedKmh)) : null
  if (avg != null && max != null) return { key: 'dashboard.trip_speed_summary', args: { avg, max } }
  if (avg != null) return { key: 'dashboard.trip_speed_avg_only', args: { avg } }
  if (max != null) return { key: 'dashboard.trip_speed_max_only', args: { max } }
  return null
}
