/**
 * Assumed average electricity price used to express phantom (idle) drain as a cost.
 * This is a flat assumption, not a per-user rate. Only used for illustrative/sample
 * contexts (e.g. the marketing teaser). Real user-facing values derive the price from
 * the user's own charging costs via {@link phantomEurFor}.
 */
export const PHANTOM_EUR_PER_KWH = 0.29

/** An idle-drain annotation as produced by the log feed (`_phantomDrain`). */
export interface PhantomDrain {
  kwh: number
  durationMs?: number
  /** €/kWh of the most recent charge before the idle gap; null if unknown. */
  pricePerKwh?: number | null
}

interface DrainEntry {
  _phantomDrain?: { kwh?: number | null } | null
}

/** A charge-log-like entry carrying cost and energy fields. */
export interface ChargeLike {
  _isTrip?: boolean
  // Single charge log
  costEur?: number | null
  kwhCharged?: number | null
  kwhAtVehicle?: number | null
  // Merged charge group (Ladegruppe)
  _isLadegruppe?: boolean
  _totalCostEur?: number | null
  _totalKwh?: number | null
}

/** Total idle-drain energy (kWh) across the given feed entries. */
export function sumPhantomKwh(entries: readonly DrainEntry[] | null | undefined): number {
  return (entries ?? []).reduce((sum, e) => sum + (e?._phantomDrain?.kwh ?? 0), 0)
}

/** Express an idle-drain energy amount (kWh) as an assumed (flat-rate) cost in EUR. */
export function phantomEur(kwh: number): number {
  return kwh * PHANTOM_EUR_PER_KWH
}

/**
 * Effective €/kWh of a single charge log (gross kWh preferred over net), or null when
 * the log is a trip or lacks usable cost/energy. Mirrors the per-log price shown in the
 * log feed (`costEur / (kwhCharged ?? kwhAtVehicle)`).
 */
export function chargeCostPerKwh(entry: ChargeLike | null | undefined): number | null {
  if (!entry || entry._isTrip) return null
  const cost = entry._isLadegruppe ? entry._totalCostEur : entry.costEur
  const kwh = entry._isLadegruppe ? entry._totalKwh : (entry.kwhCharged ?? entry.kwhAtVehicle)
  if (cost == null || kwh == null || kwh <= 0) return null
  return cost / kwh
}

/**
 * €/kWh of the charge that most recently preceded an idle gap. The feed is newest-first,
 * so the drain attached at `drainIndex` sits between `drainIndex+1` (older) and `drainIndex`
 * (newer); we scan toward older entries for the first charge with a usable price.
 */
export function precedingChargePricePerKwh(
  entries: readonly ChargeLike[],
  drainIndex: number,
): number | null {
  for (let i = drainIndex + 1; i < entries.length; i++) {
    const price = chargeCostPerKwh(entries[i])
    if (price != null) return price
  }
  return null
}

/**
 * Value an idle drain in EUR using the user's actual costs:
 * 1. the €/kWh of the charge preceding the idle gap (resolved upstream into `pricePerKwh`),
 * 2. else the user's blended average €/kWh,
 * 3. else null - we have no real price, so the cost is hidden rather than fabricated.
 */
export function phantomEurFor(
  drain: PhantomDrain | null | undefined,
  avgCostPerKwh: number | null | undefined,
): number | null {
  if (!drain) return null
  const price = drain.pricePerKwh
    ?? (avgCostPerKwh != null && avgCostPerKwh > 0 ? avgCostPerKwh : null)
  if (price == null) return null
  return drain.kwh * price
}
