import { describe, it, expect } from 'vitest'
import { NEEDS_EMPTY } from '../needsCheck'
import { needsToInputs } from '../drivingProfile'

describe('needsToInputs', () => {
  it('sends the answers like the model page, open questions on the slider start, community price', () => {
    expect(needsToInputs({ annualKm: 20_000, highwayKm: 5_000, dailyKm: 40, commuteDays: 3, longestTripKm: 900, homeCharging: false, maxStops: 0, priority: 'range' }))
      .toEqual({ commuteKmPerDay: 40, commuteDaysPerWeek: 3, highwaySharePercent: 25, homeCharging: false, longTripKm: 900, annualKm: 20_000, pricePerKwh: null })
  })

  it('nothing answered: start values, home charging counts, no yearly km so the cost rests on the commute', () => {
    expect(needsToInputs(NEEDS_EMPTY))
      .toEqual({ commuteKmPerDay: 40, commuteDaysPerWeek: 5, highwaySharePercent: 27, homeCharging: true, longTripKm: 500, annualKm: null, pricePerKwh: null })
  })

  it('sends the price the list shows, so answers, top 5 and table rest on one price', () => {
    expect(needsToInputs(NEEDS_EMPTY, 0.242).pricePerKwh).toBe(0.242)
  })

  it('keeps values within the server bounds', () => {
    const i = needsToInputs({ ...NEEDS_EMPTY, dailyKm: 900, longestTripKm: 4_000, annualKm: 300_000 })
    expect(i.commuteKmPerDay).toBe(500)
    expect(i.longTripKm).toBe(2_000)
    expect(i.annualKm).toBe(100_000)
  })
})
