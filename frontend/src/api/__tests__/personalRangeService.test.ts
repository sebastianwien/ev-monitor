// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import api from '../axios'
import { personalRangeService } from '../personalRangeService'

vi.mock('../axios', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

const INPUTS = { commuteKmPerDay: 40, highwaySharePercent: 30, homeCharging: true, longTripKm: 500 }

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.post).mockResolvedValue({ data: { winterRangeKm: 310 } } as never)
})

describe('personalRangeService.calculate', () => {
  it('posts the inputs without a mail flag by default', async () => {
    await personalRangeService.calculate('Tesla', 'Model_3', INPUTS)

    expect(api.post).toHaveBeenCalledWith('/personal-range/Tesla/Model_3', INPUTS)
  })

  it('asks for the result mail right after the gate', async () => {
    await personalRangeService.calculate('Tesla', 'Model_3', INPUTS, true)

    expect(api.post).toHaveBeenCalledWith('/personal-range/Tesla/Model_3', INPUTS, { params: { email: true } })
  })
})

describe('personalRangeService.preview', () => {
  it('posts to the public preview endpoint and returns the band', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { minKm: 230, maxKm: 320, dataLevel: 'MEASURED' } } as never)

    const band = await personalRangeService.preview('Volkswagen', 'ID.3', { ...INPUTS, pricePerKwh: 0.28 })

    expect(api.post).toHaveBeenCalledWith('/public/personal-range/Volkswagen/ID.3/preview', { ...INPUTS, pricePerKwh: 0.28 })
    expect(band).toEqual({ minKm: 230, maxKm: 320, dataLevel: 'MEASURED' })
  })
})
