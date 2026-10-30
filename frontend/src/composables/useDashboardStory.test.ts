import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'

const getMock = vi.fn()
const patchMock = vi.fn()
vi.mock('../api/axios', () => ({ default: { get: (...a: unknown[]) => getMock(...a), patch: (...a: unknown[]) => patchMock(...a) } }))
vi.mock('../api/chargingSavingsService', () => ({ default: { get: vi.fn().mockResolvedValue({ savings: null }) } }))
vi.mock('../api/peerModelComparisonService', () => ({ peerModelComparisonService: { getPeerModelComparison: vi.fn().mockResolvedValue({ modelComparisons: [] }) } }))
vi.mock('../api/carService', () => ({ carService: { getSohHistory: vi.fn().mockResolvedValue([]) } }))

import { useDashboardStory, clearStoryCache, wantsWeatherOutlook } from './useDashboardStory'
import { PETROL_EUR_PER_L_DE, monthWindow } from './storyModel'

const stats = (totalCostEur: number) => ({
  totalKwhCharged: 100, energyCostEur: totalCostEur, fixedCostEur: 0, fixedIncomeEur: 0, totalCostEur,
  avgCostPerKwh: 0.3, cheapestChargeEur: 1, mostExpensiveChargeEur: 2, avgChargeDurationMinutes: 1, totalCharges: 3,
  totalDistanceKm: 500, avgConsumptionKwhPer100km: 18, estimatedConsumptionCount: 0,
  summerConsumptionKwhPer100km: null, winterConsumptionKwhPer100km: null, chargesOverTime: [],
  chargingTypeSplit: null, locationSplit: null, peerBenchmark: null, chargingEfficiencySplit: null,
})

const ctx = () => ({ wltpConsumption: null, sohPercent: null, odometerKm: null, vehicleCategory: null, effectiveBatteryCapacityKwh: null, canViewEnergySplit: false, autoSync: false, supporterAsk: false })

const flush = async () => { for (let i = 0; i < 6; i++) await nextTick(); await new Promise(r => setTimeout(r, 0)) }

describe('useDashboardStory - Monats-Cache', () => {
  beforeEach(() => {
    clearStoryCache()
    getMock.mockReset()
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(getMock.mock.calls.length) })
      if (url.includes('/trips')) return Promise.resolve({ data: [] })
      if (url.includes('/leaderboard')) return Promise.resolve({ data: [] })
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
    expect(story.latest.value).toEqual({ charge: { id: 'l9', at: '2026-10-01T18:00:00Z', kwh: 28.5, costEur: 9.9, socFrom: null, socTo: null, rangeKm: null }, trip: null })
  })
})

describe('useDashboardStory - Karten von unten', () => {
  const fresh = { id: 'l1', loggedAt: new Date(Date.now() - 30 * 60_000).toISOString(), kwhAtVehicle: 20, costEur: null, dataSource: 'SMARTCAR_LIVE' }
  const standings = [{ category: 'MONTHLY_DISTANCE', unit: 'km', rank: 2, value: 900 }, { category: 'MONTHLY_KWH', unit: 'kWh', rank: 7, value: 100 }]

  beforeEach(async () => {
    clearStoryCache()
    getMock.mockReset()
    getMock.mockImplementation((url: string, cfg?: { params?: { monthsBack?: number } }) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      if (url.includes('/leaderboard/standings/me')) return Promise.resolve({ data: cfg?.params?.monthsBack === 1 ? standings : [] })
      if (url.startsWith('/logs?')) return Promise.resolve({ data: [fresh] })
      return Promise.resolve({ data: [] })
    })
    const { carService } = await import('../api/carService')
    vi.mocked(carService.getSohHistory).mockResolvedValue([
      { id: 's1', carId: 'car-1', sohPercent: 92, recordedAt: new Date().toISOString().slice(0, 10), createdAt: '', source: 'CHARGE_LOG', sampleSize: 3, socHubPercent: 50 },
    ])
  })

  it('Rueckblick, Ranglisten-Abschluss (Vormonat, nur Treppchen) und Akku als Karten, beste zuerst; keine Karte fuer die frische Ladung', async () => {
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    const kinds = story.cards.value.map(c => c.moment.kind)
    // Akku verfällt über 3 Tage, der Abschluss nicht: ihre Reihenfolge hängt am Tag.
    expect(kinds[0]).toBe('month_change')
    expect([...kinds].sort()).toEqual(['battery', 'month_change', 'rank'])
    expect(kinds).not.toContain('just_charged')
    const rank = story.cards.value.find(c => c.moment.kind === 'rank')!.moment
    expect(rank.kind === 'rank' && rank.places).toEqual([{ category: 'MONTHLY_DISTANCE', rank: 2, value: 900, unit: 'km' }])
    expect(getMock.mock.calls.some(([url, cfg]) => String(url).includes('/standings/me') && cfg?.params?.monthsBack === 1)).toBe(true)
  })

  it('im Vormonat entfallen Rueckblick und Abschluss, die anderen Karten bleiben', async () => {
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    story.setOffset(-1)
    await flush()
    expect(story.cards.value.map(c => c.moment.kind)).toEqual(['battery'])
  })
})

describe('useDashboardStory - Auto-Daten beim Öffnen', () => {
  const log = { id: 'l9', loggedAt: '2026-10-01T18:00:00Z', kwhCharged: 30, kwhAtVehicle: 28.5, costEur: 9.9 }
  const callsTo = (url: string) => getMock.mock.calls.filter(c => c[0] === url).length

  beforeEach(async () => {
    clearStoryCache()
    getMock.mockReset()
    const { carService } = await import('../api/carService')
    vi.mocked(carService.getSohHistory).mockResolvedValue([])
  })

  it('zeigt den Monat erst, wenn auch letzte Ladung und Fahrt da sind', async () => {
    let releaseTrip: (v: unknown) => void = () => {}
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      if (url === '/trips?carId=car-1&limit=1') return new Promise(r => { releaseTrip = r })
      if (url.startsWith('/logs?')) return Promise.resolve({ data: [log] })
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    // Monat ist geladen, die letzte Fahrt noch nicht: weiter Skeleton statt einer Seite, die danach umspringt.
    expect(story.loading.value).toBe(true)
    expect(story.monthStats.value).toBeNull()

    releaseTrip({ data: [] })
    await flush()
    expect(story.loading.value).toBe(false)
    expect(story.monthStats.value).not.toBeNull()
    expect(story.latest.value?.charge?.id).toBe('l9')
  })

  it('zeigt die Auto-Daten beim nächsten Öffnen sofort und lädt sie im Hintergrund neu', async () => {
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      if (url.startsWith('/logs?')) return Promise.resolve({ data: [log] })
      return Promise.resolve({ data: [] })
    })
    useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    const before = callsTo('/trips?carId=car-1&limit=1')

    const again = useDashboardStory(ref<string | null>('car-1'), ctx)
    expect(again.loading.value).toBe(false)
    expect(again.latest.value?.charge?.id).toBe('l9')
    await flush()
    expect(callsTo('/trips?carId=car-1&limit=1')).toBe(before + 1)
  })

  it('reload holt auch die letzte Ladung neu', async () => {
    let logs = [log]
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      if (url.startsWith('/logs?')) return Promise.resolve({ data: logs })
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    logs = [{ ...log, id: 'l10', loggedAt: '2026-10-02T18:00:00Z' }]
    story.reload()
    await flush()
    expect(story.latest.value?.charge?.id).toBe('l10')
  })

  it('"Weiß ich nicht mehr" gilt auch für den gecachten Stand', async () => {
    const priceless = { id: 'p1', loggedAt: new Date(Date.now() - 3 * 86_400_000).toISOString(), kwhAtVehicle: 20, costEur: null }
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      if (url.startsWith('/logs/priceless')) return Promise.resolve({ data: [priceless] })
      if (url.startsWith('/logs?')) return Promise.resolve({ data: [priceless] })
      return Promise.resolve({ data: [] })
    })
    patchMock.mockResolvedValue({})
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    expect(story.cards.value.map(c => c.moment.kind)).toContain('gap')
    await story.markPriceUnknown('p1')

    const again = useDashboardStory(ref<string | null>('car-1'), ctx)
    expect(again.latest.value?.charge?.id).toBe('p1')
    expect(again.cards.value.map(c => c.moment.kind)).not.toContain('gap')
  })
})

describe('useDashboardStory - Monatswechsel aus einem Guss', () => {
  const prev = (() => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth() - 1, 1) })()
  const prevDay = `startDate=${monthWindow(prev.getFullYear(), prev.getMonth() + 1).start}`
  const place = (cpoName: string) => ({ geohash: 'u33db', kind: 'PUBLIC', charges: 1, kwh: 30, costEur: 15, pricePerKwh: 0.5, lastChargedAt: '2026-10-01T10:00:00Z', lastLogId: 'l1', cpoName, chargingType: 'DC' })

  beforeEach(async () => {
    clearStoryCache()
    getMock.mockReset()
    const { carService } = await import('../api/carService')
    vi.mocked(carService.getSohHistory).mockResolvedValue([])
  })

  it('zeigt den neuen Monat erst zusammen mit seinen Daten, der Schalter springt sofort', async () => {
    let releasePrev: (v: unknown) => void = () => {}
    const prevStats = new Promise(r => { releasePrev = r })
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics') && url.includes(prevDay) && url.includes('groupBy=DAY')) return prevStats
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    const current = story.monthStats.value

    story.setOffset(-1)
    await flush()
    expect(story.offset.value).toBe(-1)
    expect(story.month.value.offset).toBe(0)
    expect(story.monthStats.value).toBe(current)
    expect(story.switching.value).toBe(true)

    releasePrev({ data: stats(20) })
    await flush()
    expect(story.month.value.offset).toBe(-1)
    expect(story.monthStats.value!.totalCostEur).toBe(20)
    expect(story.switching.value).toBe(false)
  })

  it('lädt die anderen Monate des Schalters vor: der Wechsel steht sofort', async () => {
    getMock.mockImplementation((url: string) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    await flush()

    story.setOffset(-2)
    expect(story.month.value.offset).toBe(-2)
    expect(story.loading.value).toBe(false)
  })

  it('die Ladeorte des Teasers gehören zum Monat und wechseln mit ihm', async () => {
    getMock.mockImplementation((url: string, config?: { params?: { from?: string } }) => {
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      if (url === '/logs/places') {
        const isPrev = config?.params?.from === monthWindow(prev.getFullYear(), prev.getMonth() + 1).start
        return Promise.resolve({ data: [place(isPrev ? 'EnBW' : 'Ionity')] })
      }
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    await flush()
    expect(story.placeRows.value.map(r => r.place.cpoName)).toEqual(['Ionity'])

    story.setOffset(-1)
    expect(story.placeRows.value.map(r => r.place.cpoName)).toEqual(['EnBW'])
  })
})

describe('useDashboardStory - ältere Monate', () => {
  beforeEach(async () => {
    clearStoryCache()
    getMock.mockReset()
    const { carService } = await import('../api/carService')
    vi.mocked(carService.getSohHistory).mockResolvedValue([])
  })

  it('erlaubt Monate bis zur ersten Ladung, nicht davor', async () => {
    const now = new Date()
    const first = new Date(now.getFullYear(), now.getMonth() - 7, 5, 12)
    getMock.mockImplementation((url: string) => {
      if (url === '/logs/statistics?carId=car-1&groupBy=DAY') {
        return Promise.resolve({ data: { ...stats(1), chargesOverTime: [{ timestamp: first.toISOString(), charges: 1, distanceKm: null, consumptionKwhPer100km: null }] } })
      }
      if (url.includes('/logs/statistics')) return Promise.resolve({ data: stats(10) })
      return Promise.resolve({ data: [] })
    })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    expect(story.minOffset.value).toBe(-7)
    expect(story.dataMonths.value.has(`${first.getFullYear()}-${first.getMonth() + 1}`)).toBe(true)

    story.setOffset(-7)
    await flush()
    expect(story.month.value.offset).toBe(-7)
    story.setOffset(-8)
    expect(story.offset.value).toBe(-7)
  })
})

describe('wantsWeatherOutlook', () => {
  it('holt den Wetter-Ausblick nur im Oktober, dem Monat der Herbst-Prognose', () => {
    expect(wantsWeatherOutlook(new Date(2026, 9, 15))).toBe(true)
    expect(wantsWeatherOutlook(new Date(2026, 8, 30))).toBe(false)
    expect(wantsWeatherOutlook(new Date(2026, 10, 1))).toBe(false)
  })
})

describe('useDashboardStory - Heimlade-Ersparnis gesperrt oder im Probemonat', () => {
  beforeEach(() => {
    clearStoryCache()
    getMock.mockReset()
    getMock.mockImplementation((url: string) => Promise.resolve({ data: url.includes('/logs/statistics') ? stats(10) : [] }))
  })

  it('reicht Sperre und Probemonat-Ende an das Kosten-Sheet weiter', async () => {
    const savings = (await import('../api/chargingSavingsService')).default
    vi.mocked(savings.get).mockResolvedValueOnce({ savings: null, entitled: false, locked: true, viaTrial: false, trialEndsAt: null })
    const story = useDashboardStory(ref<string | null>('car-1'), ctx)
    await flush()
    expect(story.savingsAccess.value).toEqual({ locked: true, trialEndsAt: null })

    clearStoryCache()
    vi.mocked(savings.get).mockResolvedValueOnce({ savings: null, entitled: true, locked: false, viaTrial: true, trialEndsAt: '2026-11-15' })
    const trial = useDashboardStory(ref<string | null>('car-2'), ctx)
    await flush()
    expect(trial.savingsAccess.value).toEqual({ locked: false, trialEndsAt: '2026-11-15' })
  })
})
