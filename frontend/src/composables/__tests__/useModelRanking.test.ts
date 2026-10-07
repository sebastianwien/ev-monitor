import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick, ref } from 'vue'
import { useModelRanking, PRICE_STORAGE_KEY, NEEDS_STORAGE_KEY, COST_STORAGE_KEY, PRIORITY_STORAGE_KEY } from '../useModelRanking'
import type { TopModelPreview, ModelWithoutData } from '../../api/publicModelService'
import { COST_DEFAULTS } from '../../utils/costMix'

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

function withoutData(over: Partial<ModelWithoutData> & { brandDisplayName: string, modelUrlSlug: string }): ModelWithoutData {
  return {
    brand: over.brandDisplayName.toUpperCase(),
    model: over.modelUrlSlug.toUpperCase(),
    modelDisplayName: `${over.brandDisplayName} ${over.modelUrlSlug.replace(/_/g, ' ')}`,
    category: 'SEDAN',
    categoryDisplayName: 'Limousine',
    minWltpConsumptionKwhPer100km: 16,
    avgWltpConsumptionKwhPer100km: 16,
    maxWltpConsumptionKwhPer100km: 16,
    minNetCapacityKwh: 60,
    maxNetCapacityKwh: 60,
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

    it('range: prefers the typical span of the largest battery, real range only as fallback', () => {
      const r = useModelRanking(ref([
        model({ brandDisplayName: 'A', modelUrlSlug: 'real', realRangeKm: 450 }),
        model({ brandDisplayName: 'B', modelUrlSlug: 'typical', realRangeKm: 300, typicalRangeMinKm: 320, typicalRangeMaxKm: 480 }),
      ]))
      r.sort.value = 'range'
      expect(keys(r.ranked.value)).toEqual(['B/typical', 'A/real'])
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

  describe('cost assumptions', () => {
    it('takes the old price key as the home price and starts at the community averages otherwise', () => {
      localStorage.setItem(PRICE_STORAGE_KEY, '0.42')
      const migrated = useModelRanking(ref([]))
      expect(migrated.cost.value.homePricePerKwh).toBe(0.42)

      localStorage.clear()
      const fresh = useModelRanking(ref([]))
      fresh.applyReferencePrices({ homePricePerKwh: 0.274, publicPricePerKwh: 0.561, petrolPricePerLiter: 1.789, dieselPricePerLiter: 1.659, combustionLitersPer100km: 7.0 }, true)
      expect(fresh.cost.value.homePricePerKwh).toBe(0.27)
      expect(fresh.cost.value.publicPricePerKwh).toBe(0.56)
      expect(fresh.cost.value.fuelPricePerLiter).toBe(1.79)
      // the API's flat 7.0 l does not replace the litres per class
      expect(fresh.cost.value.litersPer100km).toBeNull()

      localStorage.setItem(COST_STORAGE_KEY, JSON.stringify({ ...fresh.cost.value, homePricePerKwh: 0.5 }))
      const stored = useModelRanking(ref([]))
      stored.applyReferencePrices({ homePricePerKwh: 0.274, publicPricePerKwh: 0.561, petrolPricePerLiter: 1.789, dieselPricePerLiter: 1.659, combustionLitersPer100km: 7.0 }, true)
      expect(stored.cost.value.homePricePerKwh).toBe(0.5)
    })

    it('seeds public and fuel price even when only the classic price key exists', () => {
      localStorage.setItem(PRICE_STORAGE_KEY, '0.42')
      const r = useModelRanking(ref([]))
      r.applyReferencePrices({ homePricePerKwh: 0.274, publicPricePerKwh: 0.561, petrolPricePerLiter: 1.789, dieselPricePerLiter: 1.659, combustionLitersPer100km: 7.0 }, true)
      expect(r.cost.value.homePricePerKwh).toBe(0.42)
      expect(r.cost.value.publicPricePerKwh).toBe(0.56)
      expect(r.cost.value.fuelPricePerLiter).toBe(1.79)
    })

    it('fills a missing fuel price from the API on a later visit in Germany', () => {
      localStorage.setItem(COST_STORAGE_KEY, JSON.stringify({ ...COST_DEFAULTS, homePricePerKwh: 0.5, fuelPricePerLiter: null }))
      const api = { homePricePerKwh: 0.274, publicPricePerKwh: 0.561, petrolPricePerLiter: 1.789, dieselPricePerLiter: 1.659, combustionLitersPer100km: 7.0 }
      const de = useModelRanking(ref([]))
      de.applyReferencePrices(api, true)
      expect(de.cost.value.homePricePerKwh).toBe(0.5)
      expect(de.cost.value.fuelPricePerLiter).toBe(1.79)
      const abroad = useModelRanking(ref([]))
      abroad.applyReferencePrices(api, false)
      expect(abroad.cost.value.fuelPricePerLiter).toBeNull()
    })

    it('leaves the fuel price empty outside Germany', () => {
      const r = useModelRanking(ref([]))
      r.applyReferencePrices({ homePricePerKwh: 0.3, publicPricePerKwh: 0.5, petrolPricePerLiter: 1.8, dieselPricePerLiter: 1.7, combustionLitersPer100km: 7.0 }, false)
      expect(r.cost.value.fuelPricePerLiter).toBeNull()
    })

    it('prices every row with the mixed price and persists changes', async () => {
      const r = useModelRanking(ref([model({ brandDisplayName: 'A', modelUrlSlug: 'x', avgConsumptionKwhPer100km: 20 })]))
      r.cost.value = { ...r.cost.value, homePricePerKwh: 0.3, publicPricePerKwh: 0.6, fuelPricePerLiter: 1.8 }
      // needs default: home charging → 80 % → 0.36 €/kWh
      expect(r.price.value).toBeCloseTo(0.36, 6)
      expect(r.ranked.value[0].costPer100kmEur).toBeCloseTo(7.2, 5)
      // SEDAN petrol 7.6 l × 1.8 = 13.68 → 6.48 cheaper, over 40 km × 300 days = 777.6
      expect(r.ranked.value[0].combustionLitersPer100km).toBe(7.6)
      expect(r.ranked.value[0].savingsPer100kmEur).toBeCloseTo(6.48, 5)
      expect(r.ranked.value[0].savingsPerYearEur).toBeCloseTo(777.6, 3)
      // diesel and a typed litre value change every row
      r.cost.value = { ...r.cost.value, fuel: 'diesel' }
      expect(r.ranked.value[0].combustionLitersPer100km).toBe(6.0)
      r.cost.value = { ...r.cost.value, litersPer100km: 9 }
      expect(r.ranked.value[0].combustionLitersPer100km).toBe(9)
      expect(r.ranked.value[0].savingsPer100kmEur).toBeCloseTo(9 * 1.8 - 7.2, 5)
      await nextTick()
      expect(JSON.parse(localStorage.getItem(COST_STORAGE_KEY) ?? '{}').homePricePerKwh).toBe(0.3)

      r.needs.value = { ...r.needs.value, homeCharging: false }
      expect(r.price.value).toBeCloseTo(0.6, 6)
      r.cost.value = { ...r.cost.value, homeShare: 0.5 }
      expect(r.price.value).toBeCloseTo(0.45, 6)
    })

    it('has no combustion comparison without a fuel price or outside metric markets', () => {
      const r = useModelRanking(ref([model({ brandDisplayName: 'A', modelUrlSlug: 'x', avgConsumptionKwhPer100km: 20 })]))
      expect(r.ranked.value[0].savingsPer100kmEur).toBeNull()
      expect(r.ranked.value[0].combustionLitersPer100km).toBeNull()
      r.cost.value = { ...r.cost.value, fuelPricePerLiter: 1.8 }
      expect(r.ranked.value[0].savingsPer100kmEur).not.toBeNull()
      r.combustionMarket.value = false
      expect(r.ranked.value[0].savingsPer100kmEur).toBeNull()
    })

    it('resets the assumptions to the defaults', () => {
      const r = useModelRanking(ref([]))
      r.cost.value = { ...r.cost.value, homeShare: 0.2, litersPer100km: 9, mainValue: 'cost' }
      r.resetCost()
      expect(r.cost.value.homeShare).toBeNull()
      expect(r.cost.value.litersPer100km).toBeNull()
      expect(r.cost.value.mainValue).toBe('consumption')
    })
  })

  describe('priority', () => {
    it('maps the answer to a sort and keeps it in the browser', async () => {
      const r = useModelRanking(ref([]))
      expect(r.priority.value).toBeNull()
      r.priority.value = 'winter'
      expect(r.sort.value).toBe('winter')
      r.priority.value = 'cost'
      expect(r.sort.value).toBe('efficient')
      await nextTick()
      expect(localStorage.getItem(PRIORITY_STORAGE_KEY)).toBe('cost')
      const again = useModelRanking(ref([]))
      expect(again.priority.value).toBe('cost')
      expect(again.sort.value).toBe('efficient')
    })

    it('changing the sort by chip clears the priority answer', () => {
      const r = useModelRanking(ref([]))
      r.priority.value = 'data'
      r.sort.value = 'range'
      expect(r.priority.value).toBeNull()
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

  describe('data basis', () => {
    it('flags a model whose values come from a single driver', () => {
      const r = useModelRanking(ref([
        model({ brandDisplayName: 'A', modelUrlSlug: 'solo', contributorCount: 1 }),
        model({ brandDisplayName: 'B', modelUrlSlug: 'many', contributorCount: 4 }),
        model({ brandDisplayName: 'C', modelUrlSlug: 'old', contributorCount: undefined }),
      ]))
      const byKey = Object.fromEntries(r.ranked.value.map(x => [x.key, x.singleDriver]))
      expect(byKey).toEqual({ 'A/solo': true, 'B/many': false, 'C/old': false })
    })
  })

  describe('models without driver data', () => {
    const ranked = [model({ brandDisplayName: 'Hyundai', modelUrlSlug: 'Ioniq_5' })]
    const unranked = [
      withoutData({ brandDisplayName: 'Lotus', modelUrlSlug: 'Emeya', category: 'SPORTS' }),
      withoutData({ brandDisplayName: 'Audi', modelUrlSlug: 'Q8_e-tron', category: 'SUV' }),
    ]

    it('filters the list by search and class like the ranking', () => {
      const r = useModelRanking(ref(ranked), ref(unranked))
      expect(r.withoutData.value.map(m => m.modelUrlSlug)).toEqual(['Emeya', 'Q8_e-tron'])
      r.category.value = 'SUV'
      expect(r.withoutData.value.map(m => m.modelUrlSlug)).toEqual(['Q8_e-tron'])
      r.category.value = null
      r.query.value = 'lotus'
      expect(r.withoutData.value.map(m => m.modelUrlSlug)).toEqual(['Emeya'])
    })

    it('knows when a search only hits models without data', () => {
      const r = useModelRanking(ref(ranked), ref(unranked))
      expect(r.searchHitsOnlyWithoutData.value).toBe(false)
      r.query.value = 'emeya'
      expect(r.ranked.value).toEqual([])
      expect(r.searchHitsOnlyWithoutData.value).toBe(true)
      r.query.value = 'nothing'
      expect(r.searchHitsOnlyWithoutData.value).toBe(false)
    })
  })

  describe('needs check', () => {
    const models = [
      model({ brandDisplayName: 'A', modelUrlSlug: 'small', winterRangeMinKm: 200, winterRangeMaxKm: 200, typicalRangeMinKm: 260, typicalRangeMaxKm: 260 }),
      model({ brandDisplayName: 'B', modelUrlSlug: 'big', winterRangeMinKm: 420, winterRangeMaxKm: 520, typicalRangeMinKm: 500, typicalRangeMaxKm: 600 }),
      model({ brandDisplayName: 'C', modelUrlSlug: 'none' }),
    ]

    it('starts with the defaults and summarises over all models', () => {
      const r = useModelRanking(ref(models))
      expect(r.needs.value).toEqual({ dailyKm: 40, longestTripKm: 400, homeCharging: true })
      // 200 × 0.8 / 40 = 4 days (no), 420 × 0.8 / 40 = 8.4 (yes); 520 × 0.8 = 416 ≥ 400 (yes), 200 × 0.8 (no)
      expect(r.needsSummary.value).toEqual({ total: 2, weeklyOk: 1, tripOk: 1 })
    })

    it('persists the inputs and restores them', async () => {
      const r = useModelRanking(ref(models))
      r.needs.value = { dailyKm: 60, longestTripKm: 250, homeCharging: false }
      await nextTick()
      expect(JSON.parse(localStorage.getItem(NEEDS_STORAGE_KEY) ?? '{}')).toEqual({ dailyKm: 60, longestTripKm: 250, homeCharging: false })
      expect(useModelRanking(ref(models)).needs.value).toEqual({ dailyKm: 60, longestTripKm: 250, homeCharging: false })
      localStorage.setItem(NEEDS_STORAGE_KEY, '{"dailyKm":"x"}')
      expect(useModelRanking(ref(models)).needs.value).toEqual({ dailyKm: 40, longestTripKm: 400, homeCharging: true })
    })

    it('attaches an assessment to every row and can filter to trips without a stop', () => {
      const r = useModelRanking(ref(models))
      const big = r.ranked.value.find(x => x.key === 'B/big')!
      expect(big.needs.assessable && big.needs.tripStops).toEqual({ min: 0, max: 1, single: false })
      expect(r.ranked.value.find(x => x.key === 'C/none')!.needs).toEqual({ assessable: false })
      r.tripOnly.value = true
      expect(keys(r.ranked.value)).toEqual(['B/big'])
      r.needs.value = { ...r.needs.value, longestTripKm: 600 }
      expect(keys(r.ranked.value)).toEqual([])
    })
  })
})
