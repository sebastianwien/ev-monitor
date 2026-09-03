// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import api from '../axios'
import goeService from '../goeService'

vi.mock('../axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))

beforeEach(() => vi.clearAllMocks())

describe('goeService.connect', () => {
  it('sendet den Heim-Tarif mit, wenn er beim Verbinden angegeben wurde', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { carState: 1 } })

    await goeService.connect('A123', 'key', 'car-1', 'Garage', 'u1hfy', 30)

    expect(api.post).toHaveBeenCalledWith('/goe/connect', {
      serial: 'A123', apiKey: 'key', carId: 'car-1', displayName: 'Garage',
      geohash: 'u1hfy', tariffCentsPerKwh: 30,
    })
  })

  it('sendet tariffCentsPerKwh als null, wenn kein Tarif angegeben wurde', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { carState: 1 } })

    await goeService.connect('A123', 'key', 'car-1', 'Garage')

    expect(api.post).toHaveBeenCalledWith('/goe/connect', {
      serial: 'A123', apiKey: 'key', carId: 'car-1', displayName: 'Garage',
      geohash: null, tariffCentsPerKwh: null,
    })
  })
})
