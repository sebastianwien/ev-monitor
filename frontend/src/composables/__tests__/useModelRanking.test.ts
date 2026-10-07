import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick, ref } from 'vue'
import { useModelRanking, PRICE_STORAGE_KEY } from '../useModelRanking'
import type { TopModelPreview } from '../../api/publicModelService'

function model(over: Partial<TopModelPreview> & { brandDisplayName: string, modelUrlSlug: string }): TopModelPreview {
  return {
    brand: over.brandDisplayName.toUpperCase(),
    model: over.modelUrlSlug.toUpperCase(),
    modelDisplayName: `${over.brandDisplayName} ${over.modelUrlSlug.replace(/_/g, ' ')}`,
    logCount: 100,
    avgConsumptionKwhPer100km: 18,
    minRealConsumptionKwhPer100km: null,
    maxRealConsumptionKwhPer100km: null,
    minWltpConsumptionKwhPer100km: 16,
    maxWltpConsumptionKwhPer100km: 16,
    avgWltpConsumptionKwhPer100km: 16,
    avgCostPerKwh: null,
    category: 'SEDAN',
    categoryDisplayName: 'Limousine',
    realRangeKm: null,
    summerConsumptionKwhPer100km: null,
    winterConsumptionKwhPer100km: null,
    ...over,
  }
}

const keys = (list: { key: string }[]) => list.map(r => r.key)

describe('useModelRanking', () => {
  beforeEach(() => localStorage.clear())

  describe('sorting', () => {
    it('efficient: lowest real consumption first, missing values last', () => {
      const r = useModelRanking(ref([
        model({ brandDisplayName: 'A', modelUrlSlug: 'high', avgConsumptionKwhPer100km: 22 }),
        model({ brandDisplayName: 'B', modelUrlSlug: 'none', avgConsumptionKwhPer100km: null }),
        model({ brandDisplayName: 'C', modelUrlSlug: 'low', avgConsumptionKwhPer100km: 14 }),
      ]))
      r.sort.value = 'efficient'
      expect(keys(r.ranked.value)).toEqual(['C/low', 'A/high', 'B/none'])
      expect(r.ranked.value.map(m => m.rank)).toEqual([1, 2, 3])
    })

    it('range: longest real range first, missing values last', () => {
      const r = useModelRanking(ref([
        model({ brandDisplayName: 'A', modelUrlSlug: 'none', realRangeKm: null }),
        model({ brandDisplayName: 'B', modelUrlSlug: 'short', realRangeKm: 300 }),
        model({ brandDisplayName: 'C', modelUrlSlug: 'long', realRangeKm: 520 }),
      ]))
      r.sort.value = 'range'
      expect(keys(r.ranked.value)).toEqual(['C/long', 'B/short', 'A/none'])
    })

    it('wltp: smallest absolute deviation first, both directions count', () => {
      const r = useModelRanking(ref([
        // +12.5 %
        model({ brandDisplayName: 'A', modelUrlSlug: 'above', avgConsumptionKwhPer100km: 18, avgWltpConsumptionKwhPer100km: 16 }),
        // -11.8 %
        model({ brandDisplayName: 'B', modelUrlSlug: 'below', avgConsumptionKwhPer100km: 15, avgWltpConsumptionKwhPer100km: 17 }),
        model({ brandDisplayName: 'C', modelUrlSlug: 'nowltp', avgWltpConsumptionKwhPer100km: null,
          minWltpConsumptionKwhPer100km: null, maxWltpConsumptionKwhPer100km: null }),
      ]))
      r.sort.value = 'wltp'
      expect(keys(r.ranked.value)).toEqual(['B/below', 'A/above', 'C/nowltp'])
      expect(r.ranked.value[0].wltpDeviationPct).toBeCloseTo(-11.76, 1)
    })

    it('wltp: falls back to the middle of min and max when the average is missing (older backend)', () => {
      const r = useModelRanking(ref([
        model({ brandDisplayName: 'A', modelUrlSlug: 'old', avgWltpConsumptionKwhPer100km: undefined,
          minWltpConsumptionKwhPer100km: 15, maxWltpConsumptionKwhPer100km: 17 }),
      ]))
      expect(r.ranked.value[0].wltpKwhPer100km).toBe(16)
    })

    it('winter: smallest winter surcharge over summer first, missing seasons last', () => {
      const r = useModelRanking(ref([
        model({ brandDisplayName: 'A', modelUrlSlug: 'cold', summerConsumptionKwhPer100km: 15, winterConsumptionKwhPer100km: 21 }),
        model({ brandDisplayName: 'B', modelUrlSlug: 'nowinter', summerConsumptionKwhPer100km: 15, winterConsumptionKwhPer100km: null }),
        model({ brandDisplayName: 'C', modelUrlSlug: 'robust', summerConsumptionKwhPer100km: 16, winterConsumptionKwhPer100km: 18 }),
      ]))
      r.sort.value = 'winter'
      expect(keys(r.ranked.value)).toEqual(['C/robust', 'A/cold', 'B/nowinter'])
      expect(r.ranked.value[0].winterSurchargePct).toBeCloseTo(12.5, 5)
      expect(r.ranked.value[2].winterSurchargePct).toBeNull()
    })

    it('data: most charging logs first', () => {
      const r = useModelRanking(ref([
        model({ brandDisplayName: 'A', modelUrlSlug: 'few', logCount: 12 }),
        model({ brandDisplayName: 'B', modelUrlSlug: 'many', logCount: 900 }),
      ]))
      r.sort.value = 'data'
      expect(keys(r.ranked.value)).toEqual(['B/many', 'A/few'])
    })
  })

  describe('filters', () => {
    const models = ref([
      model({ brandDisplayName: 'Tesla', modelUrlSlug: 'Model_3', category: 'SEDAN' }),
      model({ brandDisplayName: 'Tesla', modelUrlSlug: 'Model_Y', category: 'SUV' }),
      model({ brandDisplayName: 'Hyundai', modelUrlSlug: 'Ioniq_6', category: 'SEDAN' }),
    ])

    it('filters by category', () => {
      const r = useModelRanking(models)
      r.category.value = 'SEDAN'
      expect(keys(r.ranked.value).sort()).toEqual(['Hyundai/Ioniq_6', 'Tesla/Model_3'])
    })

    it('search matches every term against brand and model, underscores as spaces', () => {
      const r = useModelRanking(models)
      r.query.value = 'tesla 3'
      expect(keys(r.ranked.value)).toEqual(['Tesla/Model_3'])
    })

    it('combines category and search, ranks within the result', () => {
      const r = useModelRanking(models)
      r.category.value = 'SEDAN'
      r.query.value = 'ioniq'
      expect(keys(r.ranked.value)).toEqual(['Hyundai/Ioniq_6'])
      expect(r.ranked.value[0].rank).toBe(1)
    })
  })

  describe('compare selection', () => {
    it('allows at most three models and toggles off again', () => {
      const r = useModelRanking(ref([]))
      r.toggleCompare('A/1'); r.toggleCompare('B/2'); r.toggleCompare('C/3')
      expect(r.canAddCompare.value).toBe(false)
      r.toggleCompare('D/4')
      expect(r.compareKeys.value).toEqual(['A/1', 'B/2', 'C/3'])
      r.toggleCompare('B/2')
      expect(r.compareKeys.value).toEqual(['A/1', 'C/3'])
      expect(r.isInCompare('C/3')).toBe(true)
      r.clearCompare()
      expect(r.compareKeys.value).toEqual([])
    })
  })

  describe('electricity price', () => {
    it('reads a valid stored price', () => {
      localStorage.setItem(PRICE_STORAGE_KEY, '0.42')
      expect(useModelRanking(ref([])).price.value).toBe(0.42)
    })

    it('ignores an invalid stored price and starts at the default', () => {
      localStorage.setItem(PRICE_STORAGE_KEY, 'abc')
      expect(useModelRanking(ref([])).price.value).toBe(0.3)
    })

    it('community home price replaces the default only when nothing is stored', () => {
      const fresh = useModelRanking(ref([]))
      fresh.applyDefaultPrice(0.274)
      expect(fresh.price.value).toBe(0.27)

      localStorage.setItem(PRICE_STORAGE_KEY, '0.55')
      const stored = useModelRanking(ref([]))
      stored.applyDefaultPrice(0.274)
      expect(stored.price.value).toBe(0.55)
    })

    it('persists changes and prices every row', async () => {
      const r = useModelRanking(ref([model({ brandDisplayName: 'A', modelUrlSlug: 'x', avgConsumptionKwhPer100km: 20 })]))
      r.price.value = 0.5
      await nextTick()
      expect(localStorage.getItem(PRICE_STORAGE_KEY)).toBe('0.5')
      expect(r.ranked.value[0].costPer100kmEur).toBeCloseTo(10, 5)
    })
  })

  it('averages the WLTP deviation over all listed models, independent of filters', () => {
    const r = useModelRanking(ref([
      model({ brandDisplayName: 'A', modelUrlSlug: 'a', avgConsumptionKwhPer100km: 18, avgWltpConsumptionKwhPer100km: 16, category: 'SUV' }), // +12.5
      model({ brandDisplayName: 'B', modelUrlSlug: 'b', avgConsumptionKwhPer100km: 22, avgWltpConsumptionKwhPer100km: 20 }), // +10
      model({ brandDisplayName: 'C', modelUrlSlug: 'c', avgWltpConsumptionKwhPer100km: null,
        minWltpConsumptionKwhPer100km: null, maxWltpConsumptionKwhPer100km: null }),
    ]))
    r.category.value = 'SEDAN'
    expect(r.avgWltpDeviationPct.value).toBeCloseTo(11.25, 5)
  })
})
