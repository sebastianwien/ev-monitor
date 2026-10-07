import { describe, it, expect } from 'vitest'
import {
  NEEDS_DEFAULTS, USABLE_BATTERY_SHARE, FAST_CHARGE_SHARE,
  chargeIntervalDays, stopsPerWeek, tripStops, rangeBasis, assessModel, summarizeNeeds, formatSpan,
  type RangeFields,
} from '../needsCheck'

const ranges = (over: Partial<RangeFields>): RangeFields => ({
  typicalRangeMinKm: null, typicalRangeMaxKm: null, winterRangeMinKm: null, winterRangeMaxKm: null, ...over,
})

describe('needsCheck', () => {
  it('defaults are 40 km a day, 400 km longest trip, charging at home', () => {
    expect(NEEDS_DEFAULTS).toEqual({ dailyKm: 40, longestTripKm: 400, homeCharging: true })
    expect(USABLE_BATTERY_SHARE).toBe(0.8)
    expect(FAST_CHARGE_SHARE).toBe(0.7)
  })

  describe('chargeIntervalDays', () => {
    it('counts whole days the usable 80 % of the range covers', () => {
      // 300 km × 0.8 = 240 km usable, 40 km a day → 6 days
      expect(chargeIntervalDays(300, 40)).toBe(6)
      // 250 × 0.8 = 200, 30 a day → 6.67 → 6
      expect(chargeIntervalDays(250, 30)).toBe(6)
    })

    it('never drops below one day and is null for a zero daily distance', () => {
      expect(chargeIntervalDays(100, 500)).toBe(1)
      expect(chargeIntervalDays(300, 0)).toBeNull()
    })
  })

  describe('stopsPerWeek', () => {
    it('divides the weekly distance by the 70 % a fast-charge stop adds', () => {
      // 40 × 7 = 280 km a week, 300 × 0.7 = 210 per stop → 2 stops
      expect(stopsPerWeek(300, 40)).toBe(2)
      // 20 × 7 = 140, 210 per stop → 1
      expect(stopsPerWeek(300, 20)).toBe(1)
    })

    it('is zero without daily driving and null without range', () => {
      expect(stopsPerWeek(300, 0)).toBe(0)
      expect(stopsPerWeek(0, 40)).toBeNull()
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

  describe('assessModel', () => {
    const twoBatteries = ranges({ typicalRangeMinKm: 400, typicalRangeMaxKm: 500, winterRangeMinKm: 300, winterRangeMaxKm: 400 })

    it('gives the charging interval per battery when charging at home', () => {
      const a = assessModel(twoBatteries, { dailyKm: 40, longestTripKm: 400, homeCharging: true })
      expect(a).toEqual({
        assessable: true,
        winter: true,
        interval: { min: 6, max: 8, single: false },
        stopsPerWeek: null,
        // 300 × 0.8 = 240 and 400 × 0.8 = 320 are both short of 400 km → one stop either way
        tripStops: { min: 1, max: 1, single: true },
      })
    })

    it('gives stops per week without home charging', () => {
      const a = assessModel(twoBatteries, { dailyKm: 40, longestTripKm: 200, homeCharging: false })
      expect(a.assessable && a.interval).toBeNull()
      // 280 a week: 300 × 0.7 = 210 → 2, 400 × 0.7 = 280 → 1
      expect(a.assessable && a.stopsPerWeek).toEqual({ min: 1, max: 2, single: false })
      expect(a.assessable && a.tripStops).toEqual({ min: 0, max: 0, single: true })
    })

    it('marks the fallback when winter data is missing and is not assessable without ranges', () => {
      const typicalOnly = assessModel(ranges({ typicalRangeMinKm: 350, typicalRangeMaxKm: 350 }), NEEDS_DEFAULTS)
      expect(typicalOnly.assessable && typicalOnly.winter).toBe(false)
      expect(typicalOnly.assessable && typicalOnly.interval).toEqual({ min: 7, max: 7, single: true })
      expect(assessModel(ranges({}), NEEDS_DEFAULTS)).toEqual({ assessable: false })
    })
  })

  describe('summarizeNeeds', () => {
    const models = [
      // smallest battery: 300 × 0.8 / 40 = 6 days → not weekly; largest 500 × 0.8 = 400 ≥ 400 → trip ok
      ranges({ typicalRangeMinKm: 400, typicalRangeMaxKm: 500, winterRangeMinKm: 300, winterRangeMaxKm: 400 }),
      // 380 × 0.8 / 40 = 7.6 → 7 days → weekly; 380 × 0.8 = 304 < 400 → trip not ok
      ranges({ typicalRangeMinKm: 450, typicalRangeMaxKm: 450, winterRangeMinKm: 380, winterRangeMaxKm: 380 }),
      ranges({}),
    ]

    it('counts assessable models, weekly chargers and trips without a stop', () => {
      expect(summarizeNeeds(models, { dailyKm: 40, longestTripKm: 400, homeCharging: true }))
        .toEqual({ total: 2, weeklyOk: 1, tripOk: 0 })
      // largest battery of the first model covers 400 km in winter: 400 × 0.8 = 320 < 400 → still 1 stop;
      // with a 300 km trip both models make it
      expect(summarizeNeeds(models, { dailyKm: 40, longestTripKm: 300, homeCharging: true }).tripOk).toBe(2)
    })

    it('uses at most one stop per week as the bar without home charging', () => {
      // 280 km a week: 300 × 0.7 = 210 → 2 stops (no), 380 × 0.7 = 266 → 2 stops (no)
      expect(summarizeNeeds(models, { dailyKm: 40, longestTripKm: 400, homeCharging: false }).weeklyOk).toBe(0)
      // 20 km a day = 140 a week → both one stop
      expect(summarizeNeeds(models, { dailyKm: 20, longestTripKm: 400, homeCharging: false }).weeklyOk).toBe(2)
    })
  })
})
