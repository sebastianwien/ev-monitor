/**
 * Needs check for the model overview: does an EV fit my daily distance, my longest trip
 * and my charging situation? Pure arithmetic on the range spans the backend derives
 * (net capacity × 100 / community consumption). No consumption formula of its own.
 *
 * Assumptions (named constants, explained in the UI):
 * - USABLE_BATTERY_SHARE: 80 % of the battery is used day to day (10 to 90 %)
 * - FAST_CHARGE_SHARE: a fast-charge stop adds 70 % (10 to 80 %)
 * The first leg of a long trip uses the same 80 % as everyday driving.
 */

export interface NeedsInput {
  dailyKm: number
  longestTripKm: number
  homeCharging: boolean
  /** length of a fast-charge stop on the road */
  stopMinutes: number
}

export const ONE_STOP_MINUTES = 20
export const STOP_MINUTES_MIN = 5
export const STOP_MINUTES_MAX = 120
export const NEEDS_DEFAULTS: NeedsInput = { dailyKm: 40, longestTripKm: 400, homeCharging: true, stopMinutes: ONE_STOP_MINUTES }
export const USABLE_BATTERY_SHARE = 0.8
export const FAST_CHARGE_SHARE = 0.7
const DAYS_PER_WEEK = 7

/** The fields of TopModelPreview the check needs: range spans, and consumption plus DC power for the stops. */
export interface RangeFields {
  typicalRangeMinKm?: number | null
  typicalRangeMaxKm?: number | null
  winterRangeMinKm?: number | null
  winterRangeMaxKm?: number | null
  avgConsumptionKwhPer100km?: number | null
  fastChargePowerKw?: number | null
}

export interface Span {
  min: number
  max: number
  /** min and max are the same value */
  single: boolean
}

export type NeedsAssessment =
  | { assessable: false }
  | {
      assessable: true
      /** true when the winter range was used, false for the typical-range fallback */
      winter: boolean
      /** days between charges at home (smallest to largest battery), null without home charging */
      interval: Span | null
      /** fast-charge stops per week, null with home charging */
      stopsPerWeek: Span | null
      /** stops on the longest trip (largest to smallest battery) */
      tripStops: Span
    }

/** Whole days the usable range covers; at least one. Null without a daily distance. */
export function chargeIntervalDays(rangeKm: number, dailyKm: number): number | null {
  if (!(dailyKm > 0) || !(rangeKm > 0)) return null
  return Math.max(1, Math.floor((rangeKm * USABLE_BATTERY_SHARE) / dailyKm))
}

/** Fast-charge stops a week of daily driving needs. Null without range. */
export function stopsPerWeek(rangeKm: number, dailyKm: number): number | null {
  if (!(rangeKm > 0)) return null
  if (!(dailyKm > 0)) return 0
  return Math.ceil((dailyKm * DAYS_PER_WEEK) / (rangeKm * FAST_CHARGE_SHARE))
}

/**
 * Kilometres one fast-charge stop adds: what the model's community DC power puts in during
 * the stop, never more than FAST_CHARGE_SHARE of the battery. Without DC data or consumption
 * the flat FAST_CHARGE_SHARE applies.
 */
export function stopAddedKm(
  rangeKm: number,
  consumptionKwhPer100km: number | null | undefined,
  dcPowerKw: number | null | undefined,
  stopMinutes: number,
): number {
  const flat = rangeKm * FAST_CHARGE_SHARE
  if (consumptionKwhPer100km == null || consumptionKwhPer100km <= 0 || dcPowerKw == null || dcPowerKw <= 0 || !(stopMinutes > 0)) return flat
  const addedKwh = dcPowerKw * (stopMinutes / 60)
  return Math.min(flat, (addedKwh * 100) / consumptionKwhPer100km)
}

/** Stops on a single trip: first leg on the usable share, then one stop per addedKmPerStop (default FAST_CHARGE_SHARE). */
export function tripStops(rangeKm: number, tripKm: number, addedKmPerStop: number = rangeKm * FAST_CHARGE_SHARE): number | null {
  if (!(rangeKm > 0) || !(addedKmPerStop > 0)) return null
  const firstLeg = rangeKm * USABLE_BATTERY_SHARE
  if (!(tripKm > 0) || tripKm <= firstLeg) return 0
  return Math.ceil((tripKm - firstLeg) / addedKmPerStop)
}

/** Winter span when complete, else the typical span, else null. */
export function rangeBasis(m: RangeFields): { min: number; max: number; winter: boolean } | null {
  if (m.winterRangeMinKm != null && m.winterRangeMaxKm != null) {
    return { min: m.winterRangeMinKm, max: m.winterRangeMaxKm, winter: true }
  }
  if (m.typicalRangeMinKm != null && m.typicalRangeMaxKm != null) {
    return { min: m.typicalRangeMinKm, max: m.typicalRangeMaxKm, winter: false }
  }
  return null
}

export function formatSpan(a: number, b: number): Span {
  const min = Math.min(a, b)
  const max = Math.max(a, b)
  return { min, max, single: min === max }
}

export function assessModel(m: RangeFields, input: NeedsInput): NeedsAssessment {
  const basis = rangeBasis(m)
  if (!basis) return { assessable: false }
  const span = (fn: (rangeKm: number) => number | null): Span | null => {
    const a = fn(basis.min)
    const b = fn(basis.max)
    return a != null && b != null ? formatSpan(a, b) : null
  }
  const trip = span(r => tripStops(r, input.longestTripKm, stopAddedKm(r, m.avgConsumptionKwhPer100km, m.fastChargePowerKw, input.stopMinutes)))
  if (!trip) return { assessable: false }
  return {
    assessable: true,
    winter: basis.winter,
    interval: input.homeCharging ? span(r => chargeIntervalDays(r, input.dailyKm)) : null,
    stopsPerWeek: input.homeCharging ? null : span(r => stopsPerWeek(r, input.dailyKm)),
    tripStops: trip,
  }
}

export interface NeedsSummary {
  /** models with a range span */
  total: number
  /** home charging: at most once a week even with the smallest battery; otherwise at most one stop a week */
  weeklyOk: number
  /** at least one battery variant makes the longest trip without a stop */
  tripOk: number
  /** at least one battery variant makes the longest trip with at most one stop */
  tripOneStopOk: number
}

export function summarizeNeeds(models: RangeFields[], input: NeedsInput): NeedsSummary {
  let total = 0
  let weeklyOk = 0
  let tripOk = 0
  let tripOneStopOk = 0
  for (const m of models) {
    const a = assessModel(m, input)
    if (!a.assessable) continue
    total++
    if (a.interval ? a.interval.min >= DAYS_PER_WEEK : (a.stopsPerWeek?.max ?? Infinity) <= 1) weeklyOk++
    if (a.tripStops.min === 0) tripOk++
    if (a.tripStops.min <= 1) tripOneStopOk++
  }
  return { total, weeklyOk, tripOk, tripOneStopOk }
}

/**
 * Range with one fast-charge stop of stopMinutes: the usable share of the battery plus what
 * the model's community DC power adds in that time. Null without range, consumption or DC data
 * (then the flat share would say nothing about the model).
 */
export function oneStopRangeKm(
  rangeKm: number | null | undefined,
  consumptionKwhPer100km: number | null | undefined,
  dcPowerKw: number | null | undefined,
  stopMinutes: number = ONE_STOP_MINUTES,
): number | null {
  if (rangeKm == null || rangeKm <= 0 || consumptionKwhPer100km == null || consumptionKwhPer100km <= 0 || dcPowerKw == null || dcPowerKw <= 0) return null
  return Math.round(rangeKm * USABLE_BATTERY_SHARE + stopAddedKm(rangeKm, consumptionKwhPer100km, dcPowerKw, stopMinutes))
}
