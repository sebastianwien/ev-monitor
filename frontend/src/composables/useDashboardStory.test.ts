import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'

const getMock = vi.fn()
vi.mock('../api/axios', () => ({ default: { get: (...a: unknown[]) => getMock(...a) } }))
vi.mock('../api/chargingSavingsService', () => ({ default: { get: vi.fn().mockResolvedValue({ savings: null }) } }))
vi.mock('../api/peerModelComparisonService', () => ({ peerModelComparisonService: { getPeerModelComparison: vi.fn().mockResolvedValue({ modelComparisons: [] }) } }))
vi.mock('../api/carService', () => ({ carService: { getSohHistory: vi.fn().mockResolvedValue([]) } }))

import { useDashboardStory, clearStoryCache } from './useDashboardStory'
import { PETROL_EUR_PER_L_DE } from './storyModel'

const stats = (totalCostEur: number) => ({
  totalKwhCharged: 100, energyCostEur: totalCostEur, fixedCostEur: 0, fixedIncomeEur: 0, totalCostEur,
  avgCostPerKwh: 0.3, cheapestChargeEur: 1, mostExpensiveChargeEur: 2, avgChargeDurationMinutes: 1, totalCharges: 3,
  totalDistanceKm: 500, avgConsumptionKwhPer100km: 18, estimatedConsumptionCount: 0,
  summerConsumptionKwhPer100km: null, winterConsumptionKwhPer100km: null, chargesOverTime: [],
  chargingTypeSplit: null, locationSplit: null, peerBenchmark: null, chargingEfficiencySplit: null,
})

const ctx = () => ({ wltpConsumption: null, sohPercent: null, odometerKm: null, effectiveBatteryCapacityKwh: null, energySplitLocked: false, canViewLiveAnalytics: false })

const flush = async () => { for (let i = 0; i < 6; i++) await nextTick(); await new Promise(r => setTimeout(r, 0)) }

describe('useDashboardStory - Monats-Cache', () => {
  beforeEach(() => {
    clearStoryCache()
    getMock.mockReset()
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(getMock.mock.calls.length) })
      if (url.includes('/trips')) return Promise.resolve({ data: [] })
      if (url.includes('/leaderboard')) return Promise.resolve({ data: { ownEntry: null } })
      if (url.includes('/public/fuel-prices')) return Promise.resolve({ data: { petrolEurPerLitre: 2.13, dieselEurPerLitre: 2.25, updatedAt: '2026-10-02T06:00:00Z' } })
      return Promise.resolve({ data: [] })
    })
  })

  it('zeigt einen bereits geladenen Monat sofort und laedt im Hintergrund nach', async () => {
    const carId = ref<string | null>('car-1')
    const story = useDashboardStory(carId, ctx)
    await flush()
    expect(story.loading.value).toBe(false)
    const firstCost = story.monthStats.value!.totalCostEur
    const callsAfterFirst = getMock.mock.calls.length

    story.setOffset(-1)
    await flush()
    const callsAfterSecond = getMock.mock.calls.length
    expect(callsAfterSecond).toBeGreaterThan(callsAfterFirst)

    // Zurueck auf den ersten Monat: Daten sofort aus dem Cache, kein Skeleton.
    story.setOffset(0)
    expect(story.loading.value).toBe(false)
    expect(story.monthStats.value!.totalCostEur).toBe(firstCost)
    await flush()
    // Hintergrund-Reload hat stattgefunden und den Wert aktualisiert.
    expect(getMock.mock.calls.length).toBeGreaterThan(callsAfterSecond)
    expect(story.monthStats.value!.totalCostEur).not.toBe(firstCost)
  })

  it('trennt den Cache nach Auto', async () => {
    const carId = ref<string | null>('car-1')
    const story = useDashboardStory(carId, ctx)
    await flush()
    carId.value = 'car-2'
    await nextTick()
    expect(story.loading.value).toBe(true)
  })
})

describe('useDashboardStory - Benzinpreis', () => {
  beforeEach(() => {
    clearStoryCache()
    getMock.mockReset()
  })

  it('nimmt den Tankerkoenig-Preis aus der API und merkt sich, dass er live ist', async () => {
    getMock.mockImplementation((url: string) => {
      if (url.includes('/public/fuel-prices')) return Promise.resolve({ data: { petrolEurPerLitre: 2.13, dieselEurPerLitre: 2.25, updatedAt: '2026-10-02T06:00:00Z' } })
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    expect(story.petrol.value).toEqual({ eurPerLitre: 2.13, live: true })
  })

  it('faellt ohne API-Antwort auf die Konstante zurueck und ohne Abrufzeitpunkt auf nicht live', async () => {
    getMock.mockImplementation((url: string) => {
      if (url.includes('/public/fuel-prices')) return Promise.reject(new Error('down'))
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    expect(story.petrol.value).toEqual({ eurPerLitre: PETROL_EUR_PER_L_DE, live: false })

    clearStoryCache()
    getMock.mockImplementation((url: string) => {
      if (url.includes('/public/fuel-prices')) return Promise.resolve({ data: { petrolEurPerLitre: 2.15, dieselEurPerLitre: 2.32, updatedAt: null } })
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      return Promise.resolve({ data: [] })
    })
    const fallback = useDashboardStory(ref<string | null>('car-2'), ctx)
    await flush()
    expect(fallback.petrol.value).toEqual({ eurPerLitre: 2.15, live: false })
  })
})

describe('useDashboardStory - Zuletzt-Zeile', () => {
  beforeEach(() => { clearStoryCache(); getMock.mockReset() })

  it('holt letzte Ladung und letzte Fahrt je Auto mit limit=1, Fahrt darf fehlen', async () => {
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      if (url.includes('/logs?carId=car-1&limit=1')) return Promise.resolve({ data: [{ id: 'l9', loggedAt: '2026-10-01T18:00:00Z', kwhCharged: 30, kwhAtVehicle: 28.5, costEur: 9.9 }] })
      if (url.includes('/trips?carId=car-1&limit=1')) return Promise.reject(new Error('403'))
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    expect(story.latest.value).toEqual({ charge: { id: 'l9', at: '2026-10-01T18:00:00Z', kwh: 28.5, costEur: 9.9 }, trip: null })
  })
})
