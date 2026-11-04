import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'

const getMock = vi.fn()
vi.mock('../../api/axios', () => ({ default: { get: (...a: unknown[]) => getMock(...a) } }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (k: string) => k }) }))
vi.mock('../useTimeRangeOptions', () => ({ useTimeRangeOptions: () => [] }))
vi.mock('../useTeslaStatus', () => ({ useTeslaStatus: () => ({ teslaStatus: ref(null) }) }))
vi.mock('../useSmartcarStatus', () => ({ useSmartcarStatus: () => ({ smartcarStatus: ref(null) }) }))
vi.mock('../../api/carService', () => ({ carService: { getCars: vi.fn().mockResolvedValue([]), getCarImageBlobUrl: vi.fn() } }))
vi.mock('../../api/vehicleSpecificationService', () => ({ vehicleSpecificationService: { lookup: vi.fn(), lookupById: vi.fn() } }))

import { useDashboardStats } from '../useDashboardStats'

const statUrls = () => getMock.mock.calls.map(([u]) => String(u)).filter(u => u.includes('/logs/statistics'))

describe('useDashboardStats - Vergleichsstatistiken', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    getMock.mockReset()
    getMock.mockResolvedValue({ data: { totalCharges: 0 } })
  })

  it('laedt fuer das alte Dashboard den Zeitraum plus Vormonat und laufenden Monat', async () => {
    const dash = useDashboardStats()
    dash.selectedCarId.value = 'car-1'
    await dash.fetchStatistics()
    expect(statUrls()).toHaveLength(3)
    expect(statUrls().some(u => u.includes('timeRange=LAST_MONTH'))).toBe(true)
    expect(statUrls().some(u => u.includes('timeRange=THIS_MONTH'))).toBe(true)
  })

  it('laesst die Vergleichsstatistiken weg, wenn keine Ansicht sie liest (neues Design)', async () => {
    const dash = useDashboardStats({ insightStats: ref(false) })
    dash.selectedCarId.value = 'car-1'
    await dash.fetchStatistics()
    expect(statUrls()).toHaveLength(1)
    expect(dash.stats.value).toEqual({ totalCharges: 0 })
    expect(dash.lastMonthStats.value).toBeNull()
  })
})
