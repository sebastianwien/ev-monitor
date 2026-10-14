import { describe, it, expect } from 'vitest'
import {
  stopsRangeKm, stopAddedKm,
  NEEDS_EMPTY, USABLE_BATTERY_SHARE, FAST_CHARGE_SHARE, HOME_CHARGE_SHARE, COMMUTE_WEEKS_PER_YEAR,
  chargesPerWeek, commuteKmPerWeek, tripStops, rangeBasis, assessModel, summarizeNeeds, formatSpan,
  type NeedsInput, type RangeFields, meetsNeeds } from '../needsCheck'

const ranges = (over: Partial<RangeFields>): RangeFields => ({
  typicalRangeMinKm: null, typicalRangeMaxKm: null, winterRangeMinKm: null, winterRangeMaxKm: null, ...over,
})
/** Anna: 40 km on 3 days, charging at home, 400 km longest trip */
const ANNA: NeedsInput = { annualKm: null, highwayKm: null, dailyKm: 40, commuteDays: 3, longestTripKm: 400, homeCharging: true, maxStops: 0 }

describe('needsCheck', () => {
  it('starts empty: no answer is a default', () => {
    expect(NEEDS_EMPTY).toEqual({ annualKm: null, highwayKm: null, dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null, maxStops: 0 })
    expect(USABLE_BATTERY_SHARE).toBe(0.8)
    expect(FAST_CHARGE_SHARE).toBe(0.7)
    expect(HOME_CHARGE_SHARE).toBe(0.6)
    expect(COMMUTE_WEEKS_PER_YEAR).toBe(46)
  })

  describe('commuteKmPerWeek', () => {
    it('is km a day times commute days, null until both are answered', () => {
      expect(commuteKmPerWeek(ANNA)).toBe(120)
      expect(commuteKmPerWeek({ ...ANNA, commuteDays: null })).toBeNull()
      expect(commuteKmPerWeek({ ...ANNA, dailyKm: null })).toBeNull()
    })
  })

  describe('chargesPerWeek', () => {
    it('home charging fills 20 to 80 %, no rounding', () => {
      // Anna: 120 km a week, winter range 300 km → 0.6 × 300 = 180 km a charge → 0.67
      expect(chargesPerWeek(300, 120, true)).toBeCloseTo(0.667, 3)
      expect(chargesPerWeek(300, 400, true)).toBeCloseTo(2.222, 3)
    })

    it('a fast-charge stop on the road adds 10 to 80 %', () => {
      // 280 km a week, 300 × 0.7 = 210 → 1.33
      expect(chargesPerWeek(300, 280, false)).toBeCloseTo(1.333, 3)
    })

    it('is zero without driving and null without range', () => {
      expect(chargesPerWeek(300, 0, true)).toBe(0)
      expect(chargesPerWeek(0, 120, true)).toBeNull()
    })
  })

  describe('tripStops', () => {
    it('is zero when the usable range covers the trip', () => {
      // 500 × 0.8 = 400 ≥ 400
      expect(tripStops(500, 400)).toBe(0)
      expect(tripStops(499, 400)).toBe(1)
    })

    it('adds one stop per 70 % of range beyond the first leg', () => {
      // first leg 240, each stop 210: 400 → 1, 450 → 1, 451 → 2
      expect(tripStops(300, 400)).toBe(1)
      expect(tripStops(300, 450)).toBe(1)
      expect(tripStops(300, 451)).toBe(2)
    })

    it('handles a zero trip and very long trips', () => {
      expect(tripStops(300, 0)).toBe(0)
      expect(tripStops(300, 5000)).toBe(23)
      expect(tripStops(0, 400)).toBeNull()
    })

    it('counts with the kilometres a stop really adds when given', () => {
      // first leg 240, 211 km left, 100 km per stop → 3 instead of 2
      expect(tripStops(300, 451, 100)).toBe(3)
      expect(tripStops(300, 451, 0)).toBeNull()
    })
  })

  describe('stopAddedKm', () => {
    it('is what the DC power adds in the stop, capped at 70 % of the range, flat 70 % without data', () => {
      // 100 kW × 20/60 h = 33.3 kWh → 208 km at 16 kWh/100 km, cap 280
      expect(stopAddedKm(400, 16, 100, 20)).toBeCloseTo(208.33, 1)
      expect(stopAddedKm(400, 16, 100, 30)).toBe(280)
      expect(stopAddedKm(400, 16, null, 20)).toBe(280)
      expect(stopAddedKm(400, null, 100, 20)).toBe(280)
    })
  })

  describe('rangeBasis', () => {
    it('prefers the winter span and falls back to the typical span', () => {
      expect(rangeBasis(ranges({ typicalRangeMinKm: 400, typicalRangeMaxKm: 500, winterRangeMinKm: 300, winterRangeMaxKm: 380 })))
        .toEqual({ min: 300, max: 380, winter: true })
      expect(rangeBasis(ranges({ typicalRangeMinKm: 400, typicalRangeMaxKm: 500 })))
        .toEqual({ min: 400, max: 500, winter: false })
    })

    it('is null without any range', () => {
      expect(rangeBasis(ranges({}))).toBeNull()
      expect(rangeBasis(ranges({ typicalRangeMinKm: 400 }))).toBeNull()
    })
  })

  describe('formatSpan', () => {
    it('collapses equal values and keeps the smaller one first', () => {
      expect(formatSpan(5, 5)).toEqual({ min: 5, max: 5, single: true })
      expect(formatSpan(7, 5)).toEqual({ min: 5, max: 7, single: false })
    })
  })

  describe('meetsNeeds', () => {
    const base = { assessable: true as const, winter: true, commute: null, tripStops: { min: 0, max: 1, single: false }, typical: null }

    it('passt, wenn jede Batterie die Fahrt mit den erlaubten Stopps schafft', () => {
      expect(meetsNeeds(base, 1)).toBe(true)
      expect(meetsNeeds(base, 0)).toBe(false)
      expect(meetsNeeds({ ...base, tripStops: { min: 0, max: 0, single: true } }, 0)).toBe(true)
    })

    it('ohne Antwort zur längsten Fahrt gibt es kein Urteil, das Pendeln zählt nicht', () => {
      const commute = { homeCharging: true, perWeek: { low: 2, high: 3 } }
      expect(meetsNeeds({ ...base, tripStops: null, commute }, 4)).toBe(false)
    })

    it('nicht bewertbar passt nie', () => {
      expect(meetsNeeds({ assessable: false }, 4)).toBe(false)
    })
  })

  describe('assessModel', () => {
    const twoBatteries = ranges({ typicalRangeMinKm: 400, typicalRangeMaxKm: 500, winterRangeMinKm: 300, winterRangeMaxKm: 400 })

    it('gives charges per week for the commute and the trip stops, winter and typical', () => {
      const a = assessModel(twoBatteries, ANNA)
      if (!a.assessable) throw new Error('assessable expected')
      expect(a.winter).toBe(true)
      // 120 km a week: 0.6 × 400 = 240 → 0.5 (largest battery), 0.6 × 300 = 180 → 0.67 (smallest)
      expect(a.commute!.homeCharging).toBe(true)
      expect(a.commute!.perWeek.low).toBeCloseTo(0.5, 3)
      expect(a.commute!.perWeek.high).toBeCloseTo(0.667, 3)
      // 300 × 0.8 = 240 and 400 × 0.8 = 320 are both short of 400 km → one stop either way
      expect(a.tripStops).toEqual({ min: 1, max: 1, single: true })
      // typical: 0.6 × 500 = 300 → 0.4, 0.6 × 400 = 240 → 0.5; 500 × 0.8 = 400 makes the trip
      expect(a.typical!.commute!.perWeek.low).toBeCloseTo(0.4, 3)
      expect(a.typical!.commute!.perWeek.high).toBeCloseTo(0.5, 3)
      expect(a.typical!.tripStops).toEqual({ min: 0, max: 1, single: false })
    })

    it('rates each part on its own: the longest trip alone gives the stops, no commute', () => {
      const a = assessModel(twoBatteries, { ...NEEDS_EMPTY, longestTripKm: 400 })
      expect(a.assessable && a.commute).toBeNull()
      expect(a.assessable && a.tripStops).toEqual({ min: 1, max: 1, single: true })
    })

    it('the commute needs km and days; without the home-charging answer it counts charging at home, as the model page does', () => {
      expect(assessModel(twoBatteries, { ...ANNA, homeCharging: null })).toMatchObject({ commute: { homeCharging: true } })
      expect(assessModel(twoBatteries, { ...ANNA, homeCharging: false })).toMatchObject({ commute: { homeCharging: false } })
      expect(assessModel(twoBatteries, { ...ANNA, commuteDays: null })).toMatchObject({ commute: null })
      expect(assessModel(twoBatteries, { ...ANNA, longestTripKm: null })).toMatchObject({ tripStops: null })
    })

    it('without home charging counts fast-charge stops per week', () => {
      const a = assessModel(twoBatteries, { ...ANNA, dailyKm: 40, commuteDays: 5, homeCharging: false })
      if (!a.assessable) throw new Error('assessable expected')
      // 200 km a week: 400 × 0.7 = 280 → 0.71, 300 × 0.7 = 210 → 0.95
      expect(a.commute!.homeCharging).toBe(false)
      expect(a.commute!.perWeek.low).toBeCloseTo(0.714, 3)
      expect(a.commute!.perWeek.high).toBeCloseTo(0.952, 3)
    })

    it('without winter data there is no typical block: the main figures are the typical ones already', () => {
      const a = assessModel(ranges({ typicalRangeMinKm: 350, typicalRangeMaxKm: 350 }), ANNA)
      expect(a.assessable && a.winter).toBe(false)
      expect(a.assessable && a.typical).toBeNull()
    })

    it('counts trip stops with the model DC power when known', () => {
      // winter 300 / 400 km, 600 km trip: flat 70 % → 2 / 1 stops; 100 kW at 16 kWh → 208 km per stop → 2 / 2
      const flat = assessModel(twoBatteries, { ...ANNA, longestTripKm: 600 })
      expect(flat.assessable && flat.tripStops).toEqual({ min: 1, max: 2, single: false })
      const withDc = assessModel({ ...twoBatteries, avgConsumptionKwhPer100km: 16, fastChargePowerKw: 100 }, { ...ANNA, longestTripKm: 600 })
      expect(withDc.assessable && withDc.tripStops).toEqual({ min: 2, max: 2, single: true })
    })

    it('is not assessable without ranges', () => {
      expect(assessModel(ranges({}), ANNA)).toEqual({ assessable: false })
    })
  })

  describe('summarizeNeeds', () => {
    const models = [
      // largest battery 500 × 0.8 = 400 ≥ 400 → trip ok on the typical range, winter 400 × 0.8 = 320 → 1 stop
      ranges({ typicalRangeMinKm: 400, typicalRangeMaxKm: 500, winterRangeMinKm: 300, winterRangeMaxKm: 400 }),
      // 380 × 0.8 = 304 < 400 → 1 stop
      ranges({ typicalRangeMinKm: 450, typicalRangeMaxKm: 450, winterRangeMinKm: 380, winterRangeMaxKm: 380 }),
      ranges({}),
    ]

    it('counts assessable models and trips within the allowed stops', () => {
      expect(summarizeNeeds(models, ANNA)).toEqual({ total: 2, tripOk: 0, tripOkWithin: [0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2] })
      expect(summarizeNeeds(models, { ...ANNA, longestTripKm: 300 }).tripOk).toBe(2)
    })

    it('without the longest trip there is nothing to count', () => {
      expect(summarizeNeeds(models, NEEDS_EMPTY)).toEqual({ total: 2, tripOk: 0, tripOkWithin: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0] })
    })
  })
})

describe('stopsRangeKm', () => {
  it('adds 20 minutes at the community DC power per stop, capped at the fast-charge share', () => {
    // 400 km × 0.8 = 320, 100 kW × 1/3 h = 33.3 kWh → 208 km at 16 kWh/100 km
    expect(stopsRangeKm(400, 16, 100, 1)).toBe(528)
    expect(stopsRangeKm(400, 16, 100, 2)).toBe(737)
    // 300 kW would add 625 km, the stop is capped at 70 % of the battery = 280 km
    expect(stopsRangeKm(400, 16, 300, 1)).toBe(600)
    expect(stopsRangeKm(400, 16, 100, 0)).toBe(320)
  })
  it('falls back to the flat 70 % per stop without consumption or DC data, null without range', () => {
    expect(stopsRangeKm(400, null, 100, 1)).toBe(600)
    expect(stopsRangeKm(400, 16, null, 1)).toBe(600)
    expect(stopsRangeKm(400, 16, 0, 2)).toBe(880)
    expect(stopsRangeKm(null, 16, 100, 1)).toBeNull()
    expect(stopsRangeKm(0, 16, 100, 1)).toBeNull()
  })
})
