import { describe, expect, it } from 'vitest'
import { applyVariant, betterMarks, compareSetKey, compareSummary, variantsWithTrips } from '../compareModels'
import type { RankedModel } from '../../composables/useModelRanking'
import type { WltpVariant } from '../../api/publicModelService'

describe('betterMarks', () => {
  it('marks the lower value when lower is better', () => {
    expect(betterMarks([20.0, 16.4], true)).toEqual([false, true])
  })

  it('marks the higher value when higher is better', () => {
    expect(betterMarks([350, 420], false)).toEqual([false, true])
  })

  it('marks nothing on a tie', () => {
    expect(betterMarks([17.6, 17.6], true)).toEqual([false, false])
  })

  it('marks every model that shares the best value when one is behind', () => {
    expect(betterMarks([17.6, 19.1, 17.6], true)).toEqual([true, false, true])
  })

  it('marks nothing with fewer than two values', () => {
    expect(betterMarks([null, 16.4], true)).toEqual([false, false])
  })

  it('ignores a missing value among three', () => {
    expect(betterMarks([18, null, 16], true)).toEqual([false, false, true])
  })
})

describe('compareSetKey', () => {
  it('sorts the model keys, so A,B and B,A count as one set', () => {
    expect(compareSetKey(['Volkswagen/ID.3', 'Tesla/Model_3'])).toBe('Tesla/Model_3,Volkswagen/ID.3')
    expect(compareSetKey(['Tesla/Model_3', 'Volkswagen/ID.3'])).toBe('Tesla/Model_3,Volkswagen/ID.3')
  })
})

describe('compareSummary', () => {
  const m3 = { name: 'Model 3', consumption: 20.0, winterKm: 320 }
  const id3 = { name: 'ID.3', consumption: 19.2, winterKm: 310 }

  it('names who needs less electricity and who goes further in winter', () => {
    expect(compareSummary([m3, { ...id3, winterKm: 270 }])).toEqual({
      lean: { winner: 'ID.3', other: 'Model 3', pct: 4 },
      winter: { winner: 'Model 3', other: 'ID.3', km: 50 },
    })
  })

  it('leaves out a difference below 1 % or 10 km', () => {
    expect(compareSummary([m3, { ...id3, consumption: 19.95, winterKm: 316 }])).toEqual({ lean: null, winter: null })
  })

  it('compares the best with the runner-up among three', () => {
    const s = compareSummary([m3, id3, { name: 'Model S', consumption: 24.4, winterKm: 360 }])
    expect(s.lean).toEqual({ winner: 'ID.3', other: 'Model 3', pct: 4 })
    expect(s.winter).toEqual({ winner: 'Model S', other: 'Model 3', km: 40 })
  })

  it('skips a figure a model lacks', () => {
    expect(compareSummary([m3, { ...id3, winterKm: null }]).winter).toBeNull()
  })
})

const variant = (o: Partial<WltpVariant>): WltpVariant => ({
  batteryCapacityKwh: 77, variantName: null, displayLabel: null, wltpRangeKm: 550, wltpRangeMinKm: null,
  wltpConsumptionKwhPer100km: 15.5, wltpConsumptionMinKwhPer100km: null, wltpConsumptionMaxKwhPer100km: null,
  realConsumptionKwhPer100km: null, realConsumptionMinKwhPer100km: null, realConsumptionMaxKwhPer100km: null,
  realConsumptionTripCount: null, estimatedConsumptionCount: null, realConsumptionRangeSource: null, seasonalDistribution: null,
  ...o,
})

describe('variantsWithTrips', () => {
  it('keeps only variants the drivers have driven', () => {
    const pro = variant({ displayLabel: 'Pro', realConsumptionTripCount: 12 })
    expect(variantsWithTrips([pro, variant({ displayLabel: 'Neo Pure', realConsumptionTripCount: 0 }), variant({ displayLabel: 'X' })])).toEqual([pro])
  })
})

describe('applyVariant', () => {
  const item = {
    key: 'Volkswagen/ID.3', rank: 2,
    model: {
      avgConsumptionKwhPer100km: 19.2, winterConsumptionKwhPer100km: 22.7, minNetCapacityKwh: 45, maxNetCapacityKwh: 79,
      typicalRangeMinKm: 211, typicalRangeMaxKm: 370, winterRangeMinKm: 170, winterRangeMaxKm: 310,
    },
    wltpKwhPer100km: 14.9, wltpDeviationPct: 29,
    ladder: { zeroStopKm: 370, oneStopKm: 670, twoStopsKm: 960 },
    needs: { commute: { homeCharging: true }, trip: { stops: 1 }, monthlyCostEur: 40 },
  } as unknown as RankedModel

  it('puts the model on the chosen battery: range, winter range, WLTP and battery follow it', () => {
    const r = applyVariant(item, variant({ batteryCapacityKwh: 58, wltpConsumptionKwhPer100km: 15.0 }))
    expect(r.model.minNetCapacityKwh).toBe(58)
    expect(r.model.maxNetCapacityKwh).toBe(58)
    // 58 kWh × 0,9 ÷ 19,2 × 100, rounded to 10 km like the model page
    expect(r.model.typicalRangeMinKm).toBe(270)
    expect(r.model.typicalRangeMaxKm).toBe(270)
    expect(r.model.winterRangeMaxKm).toBe(230)
    expect(r.wltpKwhPer100km).toBe(15.0)
    expect(r.wltpDeviationPct).toBeCloseTo(28, 0)
    // consumption stays the model's drivers' mean
    expect(r.model.avgConsumptionKwhPer100km).toBe(19.2)
  })

  it('drops what the server worked out on the largest battery for a smaller one', () => {
    const r = applyVariant(item, variant({ batteryCapacityKwh: 58 }))
    expect(r.ladder).toBeNull()
    expect(r.needs?.commute).toBeNull()
    expect(r.needs?.trip).toBeNull()
    expect(r.needs?.monthlyCostEur).toBe(40)
  })

  it('keeps them on the largest battery', () => {
    const r = applyVariant(item, variant({ batteryCapacityKwh: 79 }))
    expect(r.ladder).toEqual(item.ladder)
    expect(r.needs).toEqual(item.needs)
  })
})
