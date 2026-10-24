import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
const flushPromises = () => new Promise(r => setTimeout(r, 0))

const getOdometer = vi.fn()
vi.mock('../../api/carService', () => ({ carService: { getOdometer: (id: string) => getOdometer(id) } }))
const apiGet = vi.fn()
vi.mock('../../api/axios', () => ({ default: { get: (url: string, cfg: unknown) => apiGet(url, cfg) } }))

import { useCarOdometer, useCarAvgConsumption } from '../useCarFacts'

describe('useCarOdometer', () => {
  beforeEach(() => getOdometer.mockReset())

  it('loads the odometer for the selected car', async () => {
    getOdometer.mockResolvedValue({ currentOdometerKm: 66113 })
    const km = useCarOdometer(ref('car-1'))
    await flushPromises()
    expect(getOdometer).toHaveBeenCalledWith('car-1')
    expect(km.value).toBe(66113)
  })

  it('stays null without a car and does not call the API', async () => {
    const km = useCarOdometer(ref<string | null>(null))
    await flushPromises()
    expect(getOdometer).not.toHaveBeenCalled()
    expect(km.value).toBeNull()
  })

  it('clears the old value immediately on car switch', async () => {
    getOdometer.mockResolvedValueOnce({ currentOdometerKm: 66113 })
      .mockImplementationOnce(() => new Promise(() => {}))
    const carId = ref('car-1')
    const km = useCarOdometer(carId)
    await flushPromises()
    expect(km.value).toBe(66113)
    carId.value = 'car-2'
    await flushPromises()
    expect(km.value).toBeNull()
  })

  it('ignores a late answer for the previous car', async () => {
    let resolveFirst!: (v: unknown) => void
    getOdometer.mockImplementationOnce(() => new Promise(r => { resolveFirst = r }))
      .mockResolvedValueOnce({ currentOdometerKm: 12000 })
    const carId = ref('car-1')
    const km = useCarOdometer(carId)
    carId.value = 'car-2'
    await flushPromises()
    resolveFirst({ currentOdometerKm: 66113 })
    await flushPromises()
    expect(km.value).toBe(12000)
  })

  it('reloads when a reload source changes', async () => {
    getOdometer.mockResolvedValueOnce({ currentOdometerKm: 100 }).mockResolvedValueOnce({ currentOdometerKm: 150 })
    const version = ref(0)
    const km = useCarOdometer(ref('car-1'), [version])
    await flushPromises()
    version.value++
    await flushPromises()
    expect(getOdometer).toHaveBeenCalledTimes(2)
    expect(km.value).toBe(150)
  })

  it('keeps the last value when a reload fails', async () => {
    getOdometer.mockResolvedValueOnce({ currentOdometerKm: 100 }).mockRejectedValueOnce(new Error('offline'))
    const version = ref(0)
    const km = useCarOdometer(ref('car-1'), [version])
    await flushPromises()
    version.value++
    await flushPromises()
    expect(km.value).toBe(100)
  })
})

describe('useCarAvgConsumption', () => {
  beforeEach(() => apiGet.mockReset())

  it('asks the statistics endpoint for all time and returns the average as number', async () => {
    apiGet.mockResolvedValue({ data: { avgConsumptionKwhPer100km: '17.3' } })
    const avg = useCarAvgConsumption(ref('car-1'))
    await flushPromises()
    expect(apiGet).toHaveBeenCalledWith('/logs/statistics', { params: { carId: 'car-1', timeRange: 'ALL_TIME', groupBy: 'MONTH' } })
    expect(avg.value).toBe(17.3)
  })

  it('is null when the backend has no average', async () => {
    apiGet.mockResolvedValue({ data: { avgConsumptionKwhPer100km: null } })
    const avg = useCarAvgConsumption(ref('car-1'))
    await flushPromises()
    expect(avg.value).toBeNull()
  })
})
