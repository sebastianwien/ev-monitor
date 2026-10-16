import { describe, it, expect } from 'vitest'
import { percentileSpan, rankingAnswers, sortByPriority, availablePriorities, BREAKS_MIN_SHARE } from '../rankingAnswers'
import type { PersonalRangeRankingEntry } from '../../api/personalRangeService'

function e(over: Partial<PersonalRangeRankingEntry> = {}): PersonalRangeRankingEntry {
  return {
    brand: 'X', model: 'Y', summerRangeKm: 400, winterRangeKm: 300, longTripStops: 1, longTripTotalMinutes: 360,
    chargesPerWeekSummer: 0.7, chargesPerWeekWinter: 1.0, monthlyCostEur: 60, monthlyKm: 1250,
    zeroStopReachKm: 330, oneStopReachKm: 610, twoStopsReachKm: 890, ...over,
  }
}
const PROFILE = { dailyKm: 40, longestTripKm: 600, petrolPricePerLiter: 2.15 }

describe('percentileSpan', () => {
  it('10th to 90th percentile by nearest rank, one value when they meet, null without values', () => {
    const values = Array.from({ length: 10 }, (_, i) => (i + 1) * 10) // 10..100
    expect(percentileSpan(values)).toEqual({ lo: 10, hi: 90 })
    expect(percentileSpan([5, 5, 5])).toEqual({ lo: 5, hi: 5 })
    expect(percentileSpan([42])).toEqual({ lo: 42, hi: 42 })
    expect(percentileSpan([])).toBeNull()
  })
})

describe('rankingAnswers', () => {
  it('no models, no answers', () => {
    expect(rankingAnswers([], PROFILE)).toBeNull()
  })

  it('spans over all models for range, charging, stops and cost, and the petrol car of the same month', () => {
    const entries = [
      e({ winterRangeKm: 180, summerRangeKm: 240, chargesPerWeekWinter: 7 / 6, longTripStops: 1, monthlyCostEur: 52 }),
      e({ winterRangeKm: 260, summerRangeKm: 330, chargesPerWeekWinter: 7 / 8, longTripStops: 1, monthlyCostEur: 60 }),
      e({ winterRangeKm: 340, summerRangeKm: 430, chargesPerWeekWinter: 7 / 10, longTripStops: 2, monthlyCostEur: 82 }),
    ]
    const a = rankingAnswers(entries, PROFILE)!
    expect(a.total).toBe(3)
    expect(a.winterKm).toEqual({ lo: 180, hi: 340 })
    expect(a.summerKm).toEqual({ lo: 240, hi: 430 })
    // every model's winter range covers the 40 km commute
    expect(a.commuteOk).toBe(3)
    expect(a.chargeEveryDays).toEqual({ lo: 6, hi: 10 })
    expect(a.stops).toEqual({ lo: 1, hi: 2 })
    // the fewest stops any model needs, and how many models manage with them
    expect(a.fewestStops).toBe(1)
    expect(a.fewestStopsCount).toBe(2)
    expect(a.costEur).toEqual({ lo: 52, hi: 82 })
    // 1.250 km × 7,0 l / 100 × 2,15 € = 188,1 → 188; saving 188 - 82 = 106 to 188 - 52 = 136
    expect(a.petrolEur).toBe(188)
    expect(a.savingEur).toEqual({ lo: 106, hi: 136 })
  })

  it('names how many models make the commute when not all do, and leaves out what was not asked', () => {
    const a = rankingAnswers([e({ winterRangeKm: 30 }), e({ winterRangeKm: 300 })], { dailyKm: 40, longestTripKm: null, petrolPricePerLiter: null })!
    expect(a.commuteOk).toBe(1)
    expect(a.stops).toBeNull()
    expect(a.petrolEur).toBeNull()
    expect(a.savingEur).toBeNull()
  })

  it('no commute charging when the commute is zero: no division by zero', () => {
    const a = rankingAnswers([e({ chargesPerWeekWinter: 0 })], PROFILE)!
    expect(a.chargeEveryDays).toBeNull()
  })

  it('a saving only where the electric car is cheaper', () => {
    const a = rankingAnswers([e({ monthlyCostEur: 300 })], PROFILE)!
    expect(a.savingEur).toBeNull()
  })
})

describe('sortByPriority', () => {
  const a = e({ model: 'A', longTripStops: 2, winterRangeKm: 400, monthlyCostEur: 50, longTripTotalMinutes: 400 })
  const b = e({ model: 'B', longTripStops: 1, winterRangeKm: 250, monthlyCostEur: 70, longTripTotalMinutes: null })
  const c = e({ model: 'C', longTripStops: 1, winterRangeKm: 300, monthlyCostEur: 60, longTripTotalMinutes: 380 })

  it('range: fewest stops, then the larger winter range', () => {
    expect(sortByPriority([a, b, c], 'range').map(x => x.model)).toEqual(['C', 'B', 'A'])
  })

  it('cost: cheapest month first, no floor on the stops', () => {
    expect(sortByPriority([a, b, c], 'cost').map(x => x.model)).toEqual(['A', 'C', 'B'])
  })

  it('breaks: shortest travel time, only models that have one', () => {
    expect(sortByPriority([a, b, c], 'breaks').map(x => x.model)).toEqual(['C', 'A'])
  })

  it('leaves the input alone', () => {
    const input = [a, b, c]
    sortByPriority(input, 'cost')
    expect(input.map(x => x.model)).toEqual(['A', 'B', 'C'])
  })
})

describe('availablePriorities', () => {
  it('"Kurze Ladepausen" only once enough models have a travel time', () => {
    const timed = (n: number, total: number) => Array.from({ length: total }, (_, i) => e({ longTripTotalMinutes: i < n ? 300 : null }))
    expect(BREAKS_MIN_SHARE).toBe(0.8)
    expect(availablePriorities(timed(26, 61))).toEqual(['range', 'cost'])
    expect(availablePriorities(timed(49, 61))).toEqual(['range', 'cost', 'breaks'])
    expect(availablePriorities(timed(48, 61))).toEqual(['range', 'cost'])
    expect(availablePriorities([])).toEqual(['range', 'cost'])
  })
})
