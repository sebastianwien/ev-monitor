import { computed, reactive } from 'vue'
import type { LocationQuery } from 'vue-router'
import type { PersonalRangeInputs } from '../api/personalRangeService'
import { analytics } from '../services/analytics'
import { readTilePrefill } from '../utils/tilePrefill'
import { readStoredNeeds, writeStoredNeeds } from './useModelRanking'

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

/** The field of the answers behind each slider */
const FIELD = {
  annualKm: 'annualKm',
  highwayKm: 'highwayKm',
  commute: 'commuteKmPerDay',
  commuteDays: 'commuteDays',
  longTrip: 'longTripKm',
} as const satisfies Record<ProfileKey, string>

/** Where each slider's answer sits in the needs the ranking shares with this page */
export const NEEDS_FIELD = {
  annualKm: 'annualKm',
  highwayKm: 'highwayKm',
  commute: 'dailyKm',
  commuteDays: 'commuteDays',
  longTrip: 'longestTripKm',
} as const satisfies Record<ProfileKey, string>

/** The motorway share in percent the calculation takes: motorway km over the yearly mileage, at most 100 */
export function highwayPercent(highwayKm: number, annualKm: number): number {
  return annualKm > 0 ? Math.min(100, Math.round(highwayKm / annualKm * 100)) : 0
}

/** Onto the slider: within its range and on its step */
function snap(key: ProfileKey, value: number): number {
  const s = PROFILE_SLIDERS[key]
  return Math.min(s.max, Math.max(s.min, Math.round(value / s.step) * s.step))
}

/**
 * The reader's driving profile on the model page. Unanswered sliders hold their start value but
 * stay open: the result never rests on a value nobody chose. Answers given elsewhere count as
 * answered: the ranking's needs check (same browser), a chip on the partner tile (`?pendeln=`,
 * `?urlaub=`, wins over the stored needs) and a stored server profile (`applyProfile`, wins over
 * both). Only the reader's own answers go back to the ranking, never a start value or a snapped profile.
 */
export function useDrivingProfile(query: LocationQuery, modelName: () => string) {
  const answers = reactive({
    annualKm: PROFILE_SLIDERS.annualKm.start,
    highwayKm: PROFILE_SLIDERS.highwayKm.start,
    commuteKmPerDay: PROFILE_SLIDERS.commute.start,
    commuteDays: PROFILE_SLIDERS.commuteDays.start,
    longTripKm: PROFILE_SLIDERS.longTrip.start,
    /** before the result the backend counts home charging; the wallbox question comes with the answers */
    homeCharging: true,
    pricePerKwh: null as number | null,
  })
  const answered = reactive(new Set<ProfileKey>())
  /** Sliders still open, in page order; the result waits until there are none */
  const openQuestions = computed(() => PROFILE_KEYS.filter(k => !answered.has(k)))

  function valueOf(key: ProfileKey): number {
    return answers[FIELD[key]]
  }

  function set(key: ProfileKey, value: number) {
    answers[FIELD[key]] = value
    answered.add(key)
  }

  /** The reader's own answer on a slider */
  function answer(key: ProfileKey, value: number) {
    if (answered.size === 0) analytics.track('personal_range_started', { model: modelName() })
    set(key, value)
    analytics.track('story_chip_answered', { model: modelName(), question: key })
    writeStoredNeeds({ [NEEDS_FIELD[key]]: value })
  }

  /** The wallbox answer after the result; only an active answer goes back to the ranking */
  function answerHome(value: boolean) {
    answers.homeCharging = value
    writeStoredNeeds({ homeCharging: value })
  }

  /** A stored profile is the reader's own answers: sliders move to the nearest step and count as answered */
  function applyProfile(p: PersonalRangeInputs) {
    if (p.annualKm != null) set('annualKm', snap('annualKm', p.annualKm))
    // the profile keeps the share; back to motorway km at its yearly mileage
    set('highwayKm', snap('highwayKm', p.highwaySharePercent * (p.annualKm ?? PROFILE_SLIDERS.annualKm.start) / 100))
    set('commute', snap('commute', p.commuteKmPerDay))
    set('longTrip', snap('longTrip', p.longTripKm))
    // A profile from before the commute-days question leaves that one slider open: the reader answers it once
    if (p.commuteDaysPerWeek != null) set('commuteDays', p.commuteDaysPerWeek)
    answers.homeCharging = p.homeCharging
    answers.pricePerKwh = p.pricePerKwh ?? null
  }

  const needs = readStoredNeeds()
  if (needs) {
    if (needs.annualKm != null) set('annualKm', snap('annualKm', needs.annualKm))
    if (needs.highwayKm != null) set('highwayKm', snap('highwayKm', needs.highwayKm))
    if (needs.dailyKm != null) set('commute', snap('commute', needs.dailyKm))
    if (needs.commuteDays != null) set('commuteDays', needs.commuteDays)
    if (needs.longestTripKm != null) set('longTrip', snap('longTrip', needs.longestTripKm))
    if (needs.homeCharging != null) answers.homeCharging = needs.homeCharging
  }
  const tile = readTilePrefill(query)
  if (tile.commuteKmPerDay != null) set('commute', snap('commute', tile.commuteKmPerDay))
  if (tile.longTripKm != null) set('longTrip', snap('longTrip', tile.longTripKm))

  return { answers, answered, openQuestions, valueOf, answer, answerHome, applyProfile }
}
