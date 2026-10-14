import type { PersonalRangeRankingEntry } from '../api/personalRangeService'

/**
 * Needs check for the model overview: the reader's answers, shared with the model page, and the
 * per-model figures for them. The figures come from the server (/api/public/personal-range/ranking),
 * the same PersonalRangeCalculator as the model page on the largest WLTP battery. No range,
 * charging or stop formula in the browser.
 */

/** Every answer starts empty: an unanswered question is not rated, never filled with a default. */
export interface NeedsInput {
  /** km a year and the motorway km of it, the same sliders as on the model page */
  annualKm: number | null
  highwayKm: number | null
  dailyKm: number | null
  /** commute days a week, MIN_COMMUTE_DAYS to MAX_COMMUTE_DAYS */
  commuteDays: number | null
  longestTripKm: number | null
  homeCharging: boolean | null
  /** fast-charge stops the reader accepts on the longest trip, MIN_STOPS to MAX_STOPS */
  maxStops: number
}

export const MIN_STOPS = 0
/** Upper bound of the stop chips; a 2.000 km trip with a small battery needs this many */
export const MAX_STOPS = 10
export const MIN_COMMUTE_DAYS = 0
export const MAX_COMMUTE_DAYS = 6
export const NEEDS_EMPTY: NeedsInput = { annualKm: null, highwayKm: null, dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null, maxStops: 0 }
/** Commute weeks a year: 52 minus holidays and public holidays */
export const COMMUTE_WEEKS_PER_YEAR = 46

/** One model's figures for the reader's answers; each part only once its questions are answered. */
export interface NeedsAssessment {
  /** charges a week for the commute (home) or fast-charge stops a week (on the road), not rounded */
  commute: { homeCharging: boolean; summer: number; winter: number } | null
  /** stops and the whole travel time on the longest trip */
  trip: { stops: number; totalMinutes: number } | null
  summerRangeKm: number
  winterRangeKm: number
  /** charging cost a month at the yearly km, else at the commute; null until one of them is answered */
  monthlyCostEur: number | null
}

/** Commute km a week, null until km a day and commute days are answered */
export function commuteKmPerWeek(input: Pick<NeedsInput, 'dailyKm' | 'commuteDays'>): number | null {
  return input.dailyKm != null && input.commuteDays != null ? input.dailyKm * input.commuteDays : null
}

/** The server figures of one model, kept to the parts the reader answered. Home charging counts until answered, as on the model page. */
export function assessEntry(e: PersonalRangeRankingEntry, input: NeedsInput): NeedsAssessment {
  const commuteAnswered = commuteKmPerWeek(input) != null
  return {
    commute: commuteAnswered
      ? { homeCharging: input.homeCharging ?? true, summer: e.chargesPerWeekSummer, winter: e.chargesPerWeekWinter }
      : null,
    trip: input.longestTripKm != null ? { stops: e.longTripStops, totalMinutes: e.longTripTotalMinutes } : null,
    summerRangeKm: e.summerRangeKm,
    winterRangeKm: e.winterRangeKm,
    monthlyCostEur: input.annualKm != null || commuteAnswered ? e.monthlyCostEur : null,
  }
}

/** The trip the reader entered works within the allowed stops. The commute is a figure only, never judged. No trip, no verdict. */
export function meetsNeeds(a: NeedsAssessment | null, maxStops: number): boolean {
  return a != null && a.trip != null && a.trip.stops <= maxStops
}

export interface NeedsSummary {
  /** models with figures */
  total: number
  /** models that make the longest trip without a stop; 0 without a trip */
  tripOk: number
  /** index = stops allowed (0 to MAX_STOPS): models that make the trip with at most that many */
  tripOkWithin: number[]
}

export function summarizeNeeds(assessments: (NeedsAssessment | null)[]): NeedsSummary {
  let total = 0
  let tripOk = 0
  const tripOkWithin = Array.from({ length: MAX_STOPS + 1 }, () => 0)
  for (const a of assessments) {
    if (!a) continue
    total++
    if (!a.trip) continue
    if (a.trip.stops === 0) tripOk++
    for (let n = a.trip.stops; n <= MAX_STOPS; n++) tripOkWithin[n]++
  }
  return { total, tripOk, tripOkWithin }
}
