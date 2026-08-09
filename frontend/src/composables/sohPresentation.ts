import type { BatterySohSource, BatterySohStatus } from '../api/carService'

/** Default y-axis band. Wide enough that normal degradation reads as a gentle slope. */
const DEFAULT_FLOOR = 80
const CEILING = 100

/**
 * Fixed axis band instead of auto-scaling to the data.
 *
 * Auto-scaling is the default in most chart libraries and is wrong here: with values
 * between 91.9 and 92.1 it would stretch 0.2 percentage points of measurement noise
 * across the full plot height and make a healthy battery look like it is collapsing.
 */
export function sohAxisBounds(values: number[]): { min: number; max: number } {
  const usable = values.filter((v) => Number.isFinite(v))
  if (usable.length === 0) return { min: DEFAULT_FLOOR, max: CEILING }

  const lowest = Math.min(...usable)
  if (lowest >= DEFAULT_FLOOR) return { min: DEFAULT_FLOOR, max: CEILING }

  return { min: Math.floor(lowest / 5) * 5, max: CEILING }
}

export type SohEmptyStateKey = 'no_capacity' | 'no_charges' | 'hub_too_small' | 'pending'

/**
 * Picks what to tell the user when there is no SoH value yet. Ordered by what blocks
 * them first, so the message names the one thing that actually needs to change.
 */
export function sohEmptyStateKey(status: BatterySohStatus): SohEmptyStateKey {
  if (!status.capacityKnown) return 'no_capacity'
  if (status.largestSocHubPercent == null) return 'no_charges'
  if (status.largestSocHubPercent < status.requiredSocHubPercent) return 'hub_too_small'
  return 'pending'
}

/**
 * Nominal net capacity derived from the SoH-adjusted one.
 *
 * `car.customNetCapacityKwh` only carries a value when no vehicle specification is linked,
 * so it is null for most cars. The effective capacity is always present and is defined as
 * nominal x (1 - degradation/100) - reversing that is the reliable way to get the nominal
 * figure without fetching the specification.
 */
export function nominalCapacityKwh(
  effectiveKwh: number | null,
  degradationPercent: number | null,
): number | null {
  if (effectiveKwh == null) return null
  if (degradationPercent == null) return effectiveKwh
  const remaining = 1 - degradationPercent / 100
  if (remaining <= 0) return null
  return effectiveKwh / remaining
}

const TRUST_KEYS: Record<BatterySohSource, string> = {
  VEHICLE_BMS: 'trust_bms',
  CHARGE_LOG: 'trust_estimate',
  MANUAL: 'trust_manual',
  UNKNOWN: 'trust_unknown',
}

/** Translation key for the confidence badge - a BMS reading and an estimate are not equal. */
export function sohTrustKey(source: BatterySohSource): string {
  return TRUST_KEYS[source] ?? 'trust_unknown'
}
