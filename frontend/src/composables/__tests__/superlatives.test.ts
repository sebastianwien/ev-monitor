import { describe, it, expect } from 'vitest'
import { selectSuperlatives, MIN_LOGS_EFFICIENCY } from '../superlatives'
import type { TopModelPreview } from '../../api/publicModelService'

function model(partial: Partial<TopModelPreview>): TopModelPreview {
  return {
    brand: 'X', model: 'X', brandDisplayName: 'X', modelDisplayName: 'X',
    modelUrlSlug: 'X', logCount: 0, avgConsumptionKwhPer100km: null,
    minRealConsumptionKwhPer100km: null, maxRealConsumptionKwhPer100km: null,
    minWltpConsumptionKwhPer100km: null, maxWltpConsumptionKwhPer100km: null,
    avgCostPerKwh: null, category: 'X', categoryDisplayName: 'X', realRangeKm: null,
    ...partial,
  }
}

describe('selectSuperlatives', () => {
  it('drops efficiency models below the log threshold', () => {
    const efficient = [
      model({ model: 'SPRING', avgConsumptionKwhPer100km: 11.6, logCount: 47 }),  // below 50
      model({ model: 'IONIQ', avgConsumptionKwhPer100km: 13.7, logCount: 120 }),
      model({ model: 'CORSA', avgConsumptionKwhPer100km: 15.2, logCount: 131 }),
      model({ model: 'EUP', avgConsumptionKwhPer100km: 16.2, logCount: 297 }),
    ]
    const board = selectSuperlatives(efficient, [], [])
    expect(board.efficiency.map(m => m.model)).toEqual(['IONIQ', 'CORSA', 'EUP'])
  })

  it('keeps efficiency models exactly at the threshold', () => {
    const efficient = [model({ model: 'AT_THRESHOLD', logCount: MIN_LOGS_EFFICIENCY })]
    const board = selectSuperlatives(efficient, [], [])
    expect(board.efficiency.map(m => m.model)).toEqual(['AT_THRESHOLD'])
  })

  it('caps each category at three entries while preserving order', () => {
    const five = Array.from({ length: 5 }, (_, i) => model({ model: `R${i}`, realRangeKm: 500 - i }))
    const popular = Array.from({ length: 5 }, (_, i) => model({ model: `P${i}`, logCount: 1000 - i }))
    const board = selectSuperlatives([], five, popular)
    expect(board.range.map(m => m.model)).toEqual(['R0', 'R1', 'R2'])
    expect(board.popular.map(m => m.model)).toEqual(['P0', 'P1', 'P2'])
  })

  it('does not filter range or popular by log count', () => {
    const board = selectSuperlatives([], [model({ model: 'LOWLOG', logCount: 1, realRangeKm: 499 })], [])
    expect(board.range.map(m => m.model)).toEqual(['LOWLOG'])
  })
})
