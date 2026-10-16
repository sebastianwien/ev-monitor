import type { PersonalRangeRankingEntry } from '../api/personalRangeService'

/**
 * The list's free answers: spans over every model of the ranking reply for the reader's profile,
 * no figure of a single model. Plus the top-5 order by what matters most to the reader. Pure
 * functions; every figure per model comes from the server (same calculation as the model page).
 */

export interface Span { lo: number; hi: number }

/** "Was ist dir am wichtigsten?": far, cheap, and short breaks once enough models have a travel time */
export type RankingPriority = 'range' | 'cost' | 'breaks'

/** "Kurze Ladepausen" shows itself from this share of models with a travel time on */
export const BREAKS_MIN_SHARE = 0.8
/** The petrol car the month is compared with, as the reference prices assume it */
export const PETROL_LITERS_PER_100KM = 7.0

/** 10th to 90th percentile (nearest rank): the range most models lie in, without the outliers */
export function percentileSpan(values: number[]): Span | null {
  if (values.length === 0) return null
  const sorted = [...values].sort((a, b) => a - b)
  const at = (p: number) => sorted[Math.max(0, Math.ceil(p * sorted.length) - 1)]
  return { lo: at(0.1), hi: at(0.9) }
}

export interface RankingAnswers {
  total: number
  winterKm: Span
  summerKm: Span
  /** models whose winter range covers the daily commute; null without a commute */
  commuteOk: number | null
  /** days between two charges for the commute in winter; null without commute charging */
  chargeEveryDays: Span | null
  /** stops on the longest trip; null without a trip */
  stops: Span | null
  /** the fewest stops any model needs, and how many models manage with them */
  fewestStops: number | null
  fewestStopsCount: number
  costEur: Span
  /** the same month's km in a petrol car; null without a fuel price */
  petrolEur: number | null
  /** what the electric cars save on it; null when none is cheaper */
  savingEur: Span | null
}

export interface AnswerProfile {
  dailyKm: number | null
  longestTripKm: number | null
  petrolPricePerLiter: number | null
}

export function rankingAnswers(entries: PersonalRangeRankingEntry[], p: AnswerProfile): RankingAnswers | null {
  if (entries.length === 0) return null
  const span = (f: (x: PersonalRangeRankingEntry) => number) => percentileSpan(entries.map(f))!
  const intervals = entries.filter(x => x.chargesPerWeekWinter > 0).map(x => Math.round(7 / x.chargesPerWeekWinter))
  const fewestStops = p.longestTripKm != null ? Math.min(...entries.map(x => x.longTripStops)) : null
  const costEur = span(x => x.monthlyCostEur)
  const petrolEur = p.petrolPricePerLiter != null && p.petrolPricePerLiter > 0
    ? Math.round(entries[0].monthlyKm * PETROL_LITERS_PER_100KM / 100 * p.petrolPricePerLiter)
    : null
  const savingHi = petrolEur != null ? petrolEur - costEur.lo : 0
  return {
    total: entries.length,
    winterKm: span(x => x.winterRangeKm),
    summerKm: span(x => x.summerRangeKm),
    commuteOk: p.dailyKm != null ? entries.filter(x => x.winterRangeKm >= p.dailyKm!).length : null,
    chargeEveryDays: p.dailyKm != null && p.dailyKm > 0 ? percentileSpan(intervals) : null,
    stops: p.longestTripKm != null ? span(x => x.longTripStops) : null,
    fewestStops,
    fewestStopsCount: fewestStops != null ? entries.filter(x => x.longTripStops === fewestStops).length : 0,
    costEur,
    petrolEur,
    savingEur: petrolEur != null && savingHi > 0 ? { lo: Math.max(0, petrolEur - costEur.hi), hi: savingHi } : null,
  }
}

/**
 * The order of the top 5. "Weit kommen": fewest stops on the longest trip, then the larger winter range.
 * "Niedrige Kosten": cheapest month, no floor on the stops. "Kurze Ladepausen": shortest travel time,
 * only models that have one. A new array, the input stays.
 */
export function sortByPriority(entries: PersonalRangeRankingEntry[], priority: RankingPriority): PersonalRangeRankingEntry[] {
  switch (priority) {
    case 'cost':
      return [...entries].sort((a, b) => a.monthlyCostEur - b.monthlyCostEur)
    case 'breaks':
      return entries.filter(x => x.longTripTotalMinutes != null)
        .sort((a, b) => a.longTripTotalMinutes! - b.longTripTotalMinutes!)
    default:
      return [...entries].sort((a, b) => a.longTripStops - b.longTripStops || b.winterRangeKm - a.winterRangeKm)
  }
}

/** The answers of "Was ist dir am wichtigsten?"; short breaks only once enough models have a travel time */
export function availablePriorities(entries: PersonalRangeRankingEntry[]): RankingPriority[] {
  const timed = entries.filter(x => x.longTripTotalMinutes != null).length
  return entries.length > 0 && timed / entries.length >= BREAKS_MIN_SHARE ? ['range', 'cost', 'breaks'] : ['range', 'cost']
}
