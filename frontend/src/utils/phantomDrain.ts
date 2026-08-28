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
  /** Reine Standzeit: Start des neueren Eintrags minus Ende des aelteren. */
  pauseMs?: number
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

/**
 * Anzeigefertige Standverlust-Summe eines Zeitraums oder Ladezyklus: auf zwei Stellen
 * gerundet, null unterhalb der Anzeige-Schwelle - dieselbe Schwelle wie beim einzelnen Drain.
 */
export function totalPhantomKwh(entries: readonly DrainEntry[] | null | undefined): number | null {
  const sum = sumPhantomKwh(entries)
  return sum > DRAIN_DISPLAY_THRESHOLD_KWH ? Math.round(sum * 100) / 100 : null
}

/** Unterhalb dieser Energiemenge ist ein Drain Messrauschen und wird nicht angezeigt. */
const DRAIN_DISPLAY_THRESHOLD_KWH = 0.05

/** Backend stores LocalDateTime without timezone - treat as UTC for consistent comparison. */
function toEpochMs(isoString: string | null | undefined): number {
  if (!isoString) return 0
  const s = isoString.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(isoString) ? isoString : isoString + 'Z'
  return new Date(s).getTime()
}

/** Ein Feed-Eintrag, wie ihn useLogList baut: Fahrt, Ladelog oder Ladegruppe. */
interface FeedEntry {
  _isTrip?: boolean
  _isLadegruppe?: boolean
  _topUps?: Array<{ loggedAt?: string | null }>
  _phantomDrain?: PhantomDrain | null
  tripStartedAt?: string | null
  tripEndedAt?: string | null
  loggedAt?: string | null
  socStart?: number | string | null
  socEnd?: number | string | null
  socBeforeChargePercent?: number | string | null
  socAfterChargePercent?: number | string | null
  _maxSoc?: number | string | null
  odometerStartKm?: number | string | null
  odometerEndKm?: number | string | null
  odometerKm?: number | string | null
  energyRemainingStartKwh?: number | string | null
  energyRemainingEndKwh?: number | string | null
}

/**
 * Annotiert jeden Feed-Eintrag mit dem Standverlust der Parklücke DAVOR (`_phantomDrain`).
 *
 * `entries` ist absteigend sortiert: entries[i] = neuer, entries[i+1] = aelter. Der Drain
 * ist die Energie, die zwischen Ende des aelteren und Start des neueren Eintrags verloren
 * ging. Tesla-Fahrten liefern das kWh-Delta direkt (EnergyRemaining), sonst SoC-Delta mal
 * SoH-adjustierter Kapazitaet. Nur mit Odometer-Bestätigung, dass das Auto stand -
 * sonst waere ein Fahrverbrauch faelschlich Standverlust.
 */
export function annotatePhantomDrains(entries: FeedEntry[], capacityKwh: number | null): void {
  const endTs = (e: FeedEntry): number =>
    e._isTrip
      ? toEpochMs(e.tripEndedAt)
      : toEpochMs(e._isLadegruppe ? e._topUps?.[0]?.loggedAt : e.loggedAt)
  const startTs = (e: FeedEntry): number =>
    e._isTrip
      ? toEpochMs(e.tripStartedAt)
      : toEpochMs(e._isLadegruppe ? e._topUps?.[e._topUps.length - 1]?.loggedAt : e.loggedAt)

  const exitSoc = (e: FeedEntry): number | null => {
    if (e._isTrip) return e.socEnd != null ? Number(e.socEnd) : null
    const soc = e._isLadegruppe ? e._maxSoc : e.socAfterChargePercent
    return soc != null ? Number(soc) : null
  }
  const entrySoc = (e: FeedEntry): number | null => {
    if (e._isTrip) return e.socStart != null ? Number(e.socStart) : null
    return e.socBeforeChargePercent != null ? Number(e.socBeforeChargePercent) : null
  }
  const exitOdometer = (e: FeedEntry): number | null => {
    if (e._isTrip) return e.odometerEndKm != null ? Number(e.odometerEndKm) : null
    return e.odometerKm != null ? Number(e.odometerKm) : null
  }
  const entryOdometer = (e: FeedEntry): number | null => {
    if (e._isTrip) return e.odometerStartKm != null ? Number(e.odometerStartKm) : null
    return e.odometerKm != null ? Number(e.odometerKm) : null
  }

  for (let i = 0; i < entries.length; i++) {
    entries[i]._phantomDrain = null
    if (i >= entries.length - 1) continue

    const newer = entries[i]
    const older = entries[i + 1]

    // Tesla: direct kWh delta via EnergyRemaining (most precise)
    const olderEnergyEnd = older._isTrip && older.energyRemainingEndKwh != null
      ? Number(older.energyRemainingEndKwh) : null
    const newerEnergyStart = newer._isTrip && newer.energyRemainingStartKwh != null
      ? Number(newer.energyRemainingStartKwh) : null

    // Odometer confidence: car didn't move during the gap
    const odomOlder = exitOdometer(older)
    const odomNewer = entryOdometer(newer)
    const highConfidence = odomOlder != null && odomNewer != null && Math.abs(odomOlder - odomNewer) < 0.5

    let drainKwh: number | null = null

    if (olderEnergyEnd != null && newerEnergyStart != null) {
      drainKwh = olderEnergyEnd - newerEnergyStart
    } else {
      const socOlder = exitSoc(older)
      const socNewer = entrySoc(newer)
      if (socOlder != null && socNewer != null && capacityKwh != null) {
        drainKwh = (socOlder - socNewer) / 100 * capacityKwh
      }
    }

    if (drainKwh != null && drainKwh > DRAIN_DISPLAY_THRESHOLD_KWH && highConfidence) {
      newer._phantomDrain = {
        kwh: Math.round(drainKwh * 100) / 100,
        durationMs: endTs(newer) - endTs(older),
        pauseMs: startTs(newer) - endTs(older),
        // Value the lost energy at the price of the most recent charge before the gap.
        pricePerKwh: precedingChargePricePerKwh(entries as ChargeLike[], i),
      }
    }
  }
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
