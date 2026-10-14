/**
 * Needs check for the model overview: how often do I charge for my commute, and how many
 * stops does my longest trip need? Pure arithmetic on the range spans the backend derives
 * (net capacity × 100 / community consumption). No consumption formula of its own.
 *
 * Commute rule, the same on the model page (backend PersonalRangeCalculator): km a day times
 * commute days a week, divided by what one charge adds. Weekends and holidays stay out, the
 * UI says "fürs Pendeln".
 *
 * Assumptions (named constants, explained in the UI):
 * - HOME_CHARGE_SHARE: a charge at home fills 20 to 80 %
 * - FAST_CHARGE_SHARE: a fast-charge stop adds 70 % (10 to 80 %)
 * - USABLE_BATTERY_SHARE: the first leg of a long trip uses 80 % (90 to 10 %)
 */

/** Every answer starts empty: an unanswered question is not rated, never filled with a default. */
export interface NeedsInput {
  /** km a year and the motorway share in percent: asked on both pages, used by the model page */
  annualKm: number | null
  highwayShare: number | null
  dailyKm: number | null
  /** commute days a week, MIN_COMMUTE_DAYS to MAX_COMMUTE_DAYS */
  commuteDays: number | null
  longestTripKm: number | null
  homeCharging: boolean | null
  /** fast-charge stops the reader accepts on the longest trip, MIN_STOPS to MAX_STOPS */
  maxStops: number
}

/** Every stop on the road is counted as this long */
export const ONE_STOP_MINUTES = 20
export const MIN_STOPS = 0
export const MAX_STOPS = 4
export const MIN_COMMUTE_DAYS = 1
export const MAX_COMMUTE_DAYS = 5
export const NEEDS_EMPTY: NeedsInput = { annualKm: null, highwayShare: null, dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null, maxStops: 0 }
export const USABLE_BATTERY_SHARE = 0.8
export const FAST_CHARGE_SHARE = 0.7
export const HOME_CHARGE_SHARE = 0.6
/** Commute weeks a year: 52 minus holidays and public holidays */
export const COMMUTE_WEEKS_PER_YEAR = 46

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

export interface CommuteAssessment {
  homeCharging: boolean
  /** charges a week (home) or fast-charge stops a week (on the road), not rounded:
   *  low from the largest battery, high from the smallest */
  perWeek: { low: number; high: number }
}

interface PartsAssessment {
  /** null until km a day, commute days and home charging are answered */
  commute: CommuteAssessment | null
  /** stops on the longest trip (largest to smallest battery), null until the trip is answered */
  tripStops: Span | null
}

export type NeedsAssessment =
  | { assessable: false }
  | (PartsAssessment & {
      assessable: true
      /** true when the winter range was used, false for the typical-range fallback */
      winter: boolean
      /** the same figures on the typical range, next to the winter ones; null when the main figures are typical already */
      typical: PartsAssessment | null
    })

/** Commute km a week, null until km a day and commute days are answered */
export function commuteKmPerWeek(input: Pick<NeedsInput, 'dailyKm' | 'commuteDays'>): number | null {
  return input.dailyKm != null && input.commuteDays != null ? input.dailyKm * input.commuteDays : null
}

/** Charges a week at home (20 to 80 %) or fast-charge stops on the road (10 to 80 %), not rounded. Null without range. */
export function chargesPerWeek(rangeKm: number, weeklyKm: number, homeCharging: boolean): number | null {
  if (!(rangeKm > 0)) return null
  if (!(weeklyKm > 0)) return 0
  return weeklyKm / (rangeKm * (homeCharging ? HOME_CHARGE_SHARE : FAST_CHARGE_SHARE))
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

/** Commute charges and trip stops on one range span (smallest to largest battery); each part only when answered.
    Home charging counts until the reader says otherwise, as on the model page: the question comes after the gate. */
function assessOn(m: RangeFields, input: NeedsInput, basis: { min: number; max: number }): PartsAssessment {
  const weeklyKm = commuteKmPerWeek(input)
  const homeCharging = input.homeCharging ?? true
  let commute: CommuteAssessment | null = null
  if (weeklyKm != null) {
    const high = chargesPerWeek(basis.min, weeklyKm, homeCharging)
    const low = chargesPerWeek(basis.max, weeklyKm, homeCharging)
    if (high != null && low != null) commute = { homeCharging, perWeek: { low, high } }
  }
  let trip: Span | null = null
  if (input.longestTripKm != null) {
    const tripKm = input.longestTripKm
    const stops = (r: number) => tripStops(r, tripKm, stopAddedKm(r, m.avgConsumptionKwhPer100km, m.fastChargePowerKw, ONE_STOP_MINUTES))
    const a = stops(basis.min)
    const b = stops(basis.max)
    trip = a != null && b != null ? formatSpan(a, b) : null
  }
  return { commute, tripStops: trip }
}

/** Assessable whenever the model has a range span; which parts carry figures depends on the answers. */
export function assessModel(m: RangeFields, input: NeedsInput): NeedsAssessment {
  const basis = rangeBasis(m)
  if (!basis || !(basis.min > 0) || !(basis.max > 0)) return { assessable: false }
  const main = assessOn(m, input, basis)
  const typical = basis.winter && m.typicalRangeMinKm != null && m.typicalRangeMaxKm != null
    ? assessOn(m, input, { min: m.typicalRangeMinKm, max: m.typicalRangeMaxKm })
    : null
  return { assessable: true, winter: basis.winter, ...main, typical }
}

/** The trip the reader entered works for every battery variant within the allowed stops. The commute
 *  is shown as a figure only: nobody asked for a weekly rhythm, so it is not judged. No trip, no verdict. */
export function meetsNeeds(a: NeedsAssessment, maxStops: number): boolean {
  return a.assessable && a.tripStops != null && a.tripStops.max <= maxStops
}

export interface NeedsSummary {
  /** models with a range span */
  total: number
  /** at least one battery variant makes the longest trip without a stop; 0 without a trip */
  tripOk: number
  /** index = stops allowed (0 to MAX_STOPS): models whose best battery makes the trip with at most that many */
  tripOkWithin: number[]
}

export function summarizeNeeds(models: RangeFields[], input: NeedsInput): NeedsSummary {
  let total = 0
  let tripOk = 0
  const tripOkWithin = Array.from({ length: MAX_STOPS + 1 }, () => 0)
  for (const m of models) {
    const a = assessModel(m, input)
    if (!a.assessable) continue
    total++
    if (!a.tripStops) continue
    if (a.tripStops.min === 0) tripOk++
    for (let n = a.tripStops.min; n <= MAX_STOPS; n++) tripOkWithin[n]++
  }
  return { total, tripOk, tripOkWithin }
}

/**
 * Range with `stops` fast-charge stops of ONE_STOP_MINUTES each: the usable share of the
 * battery plus what a stop adds (the model's community DC power, else the flat share, see
 * stopAddedKm). Null without range.
 */
export function stopsRangeKm(
  rangeKm: number | null | undefined,
  consumptionKwhPer100km: number | null | undefined,
  dcPowerKw: number | null | undefined,
  stops: number,
): number | null {
  if (rangeKm == null || rangeKm <= 0) return null
  return Math.round(rangeKm * USABLE_BATTERY_SHARE + stops * stopAddedKm(rangeKm, consumptionKwhPer100km, dcPowerKw, ONE_STOP_MINUTES))
}
