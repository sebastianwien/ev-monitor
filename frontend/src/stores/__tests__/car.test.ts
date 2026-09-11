import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../../api/carService', () => ({
  carService: { getCars: vi.fn().mockResolvedValue([{ id: 'c1' }]) },
}))

import { useCarStore } from '../car'
import { carService } from '../../api/carService'

describe('car store carsLoaded', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(carService.getCars).mockClear()
  })

  it('is reactive: false initially, true after load, false after invalidate', async () => {
    const store = useCarStore()
    expect(store.carsLoaded).toBe(false)
    await store.getCars()
    expect(store.carsLoaded).toBe(true)
    store.invalidateCars()
    expect(store.carsLoaded).toBe(false)
    await store.getCars()
    expect(store.carsLoaded).toBe(true)
    expect(carService.getCars).toHaveBeenCalledTimes(2)
  })
})
