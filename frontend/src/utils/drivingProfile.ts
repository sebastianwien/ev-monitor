import type { PersonalRangeInputs } from '../api/personalRangeService'
import type { NeedsInput } from './needsCheck'

/** The five sliders of the driving profile; key order is page order */
export type ProfileKey = 'annualKm' | 'highwayKm' | 'commute' | 'commuteDays' | 'longTrip'
export interface SliderRange { min: number; max: number; step: number; start: number }

export const PROFILE_SLIDERS: Record<ProfileKey, SliderRange> = {
  annualKm: { min: 5_000, max: 75_000, step: 1_000, start: 15_000 },
  /** motorway km a year; the slider ends at the reader's yearly mileage once that is answered */
  highwayKm: { min: 0, max: 75_000, step: 500, start: 4_000 },
  commute: { min: 5, max: 150, step: 5, start: 40 },
  /** the same 0 to 6 days as the ranking's needs check */
  commuteDays: { min: 0, max: 6, step: 1, start: 5 },
  longTrip: { min: 250, max: 2_000, step: 50, start: 500 },
}
export const PROFILE_KEYS = Object.keys(PROFILE_SLIDERS) as ProfileKey[]

/** The motorway share in percent the calculation takes: motorway km over the yearly mileage, at most 100 */
export function highwayPercent(highwayKm: number, annualKm: number): number {
  return annualKm > 0 ? Math.min(100, Math.round(highwayKm / annualKm * 100)) : 0
}

/** Server bounds of the calculator inputs (PersonalRangeRequest) */
const LIMITS = { commuteKmPerDay: [0, 500], longTripKm: [50, 2_000], annualKm: [1_000, 100_000] } as const
const clamp = (v: number, [lo, hi]: readonly [number, number]) => Math.min(hi, Math.max(lo, Math.round(v)))

/**
 * The ranking's answers as calculator inputs, so the list rests on the model page's calculation.
 * An open question takes the slider's start value as on the model page; the list shows a figure
 * only for answered parts. Yearly km only when answered: until then the month's cost rests on the
 * commute. Home charging counts until the reader says otherwise; the community price applies.
 */
export function needsToInputs(n: NeedsInput): PersonalRangeInputs {
  const s = PROFILE_SLIDERS
  return {
    commuteKmPerDay: clamp(n.dailyKm ?? s.commute.start, LIMITS.commuteKmPerDay),
    commuteDaysPerWeek: n.commuteDays ?? s.commuteDays.start,
    highwaySharePercent: highwayPercent(n.highwayKm ?? s.highwayKm.start, n.annualKm ?? s.annualKm.start),
    homeCharging: n.homeCharging ?? true,
    longTripKm: clamp(n.longestTripKm ?? s.longTrip.start, LIMITS.longTripKm),
    annualKm: n.annualKm != null ? clamp(n.annualKm, LIMITS.annualKm) : null,
    pricePerKwh: null,
  }
}
