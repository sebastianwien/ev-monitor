import { describe, it, expect } from 'vitest'
import type { PersonalRangeRankingEntry } from '../../api/personalRangeService'
import { NEEDS_EMPTY, COMMUTE_WEEKS_PER_YEAR, commuteKmPerWeek, assessEntry, meetsNeeds, summarizeNeeds, type NeedsInput } from '../needsCheck'

/** Anna: 40 km on 3 days, charging at home, 400 km longest trip */
const ANNA: NeedsInput = { annualKm: null, highwayKm: null, dailyKm: 40, commuteDays: 3, longestTripKm: 400, homeCharging: true, maxStops: 0 }

const entry = (over: Partial<PersonalRangeRankingEntry> = {}): PersonalRangeRankingEntry => ({
  brand: 'TESLA', model: 'MODEL_3', summerRangeKm: 420, winterRangeKm: 320, longTripStops: 1, longTripTotalMinutes: 245,
  chargesPerWeekSummer: 0.4, chargesPerWeekWinter: 0.55, monthlyCostEur: 31, monthlyKm: 530, oneStopReachKm: 610, twoStopsReachKm: 890, ...over,
})

describe('needsCheck', () => {
  it('starts empty: no answer is a default', () => {
    expect(NEEDS_EMPTY).toEqual({ annualKm: null, highwayKm: null, dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null, maxStops: 0 })
    expect(COMMUTE_WEEKS_PER_YEAR).toBe(46)
  })

  describe('commuteKmPerWeek', () => {
    it('is km a day times commute days, null until both are answered', () => {
      expect(commuteKmPerWeek(ANNA)).toBe(120)
      expect(commuteKmPerWeek({ ...ANNA, commuteDays: null })).toBeNull()
      expect(commuteKmPerWeek({ ...ANNA, dailyKm: null })).toBeNull()
    })
  })

  describe('assessEntry', () => {
    it('takes the server figures, each part only once answered', () => {
      expect(assessEntry(entry(), ANNA)).toEqual({
        commute: { homeCharging: true, summer: 0.4, winter: 0.55 },
        trip: { stops: 1, totalMinutes: 245 },
        summerRangeKm: 420,
        winterRangeKm: 320,
        monthlyCostEur: 31,
      })
    })

    it('no commute without km and days, no trip without the longest trip, no cost without a mileage', () => {
      const a = assessEntry(entry(), { ...NEEDS_EMPTY, longestTripKm: 400 })
      expect(a.commute).toBeNull()
      expect(a.trip).toEqual({ stops: 1, totalMinutes: 245 })
      expect(a.monthlyCostEur).toBeNull()
      expect(assessEntry(entry(), { ...NEEDS_EMPTY, annualKm: 12_000 }).monthlyCostEur).toBe(31)
    })

    it('counts charging at home until the reader says otherwise, as on the model page', () => {
      expect(assessEntry(entry(), { ...ANNA, homeCharging: null }).commute?.homeCharging).toBe(true)
      expect(assessEntry(entry(), { ...ANNA, homeCharging: false }).commute?.homeCharging).toBe(false)
    })
  })

  describe('meetsNeeds', () => {
    it('the trip works within the allowed stops; no trip answered or no figures, no verdict', () => {
      const a = assessEntry(entry({ longTripStops: 1 }), ANNA)
      expect(meetsNeeds(a, 1)).toBe(true)
      expect(meetsNeeds(a, 0)).toBe(false)
      expect(meetsNeeds(assessEntry(entry(), { ...ANNA, longestTripKm: null }), 4)).toBe(false)
      expect(meetsNeeds(null, 4)).toBe(false)
    })
  })

  describe('summarizeNeeds', () => {
    it('counts models with figures and trips within the allowed stops', () => {
      const list = [assessEntry(entry({ longTripStops: 0 }), ANNA), assessEntry(entry({ longTripStops: 2 }), ANNA), null]
      expect(summarizeNeeds(list)).toEqual({ total: 2, tripOk: 1, tripOkWithin: [1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2] })
    })

    it('without the longest trip there is nothing to count', () => {
      const list = [assessEntry(entry(), { ...ANNA, longestTripKm: null })]
      expect(summarizeNeeds(list)).toEqual({ total: 1, tripOk: 0, tripOkWithin: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0] })
    })
  })
})
