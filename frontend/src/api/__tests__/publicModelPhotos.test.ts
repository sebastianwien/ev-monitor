import { describe, it, expect, vi, beforeEach } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../axios', () => ({ default: { get } }))

import { carPhotoUrl, getModelPhotoSummaries, getModelPhotoCarIds } from '../publicModelService'

describe('public model photo service', () => {
  beforeEach(() => get.mockReset())

  describe('carPhotoUrl', () => {
    it('builds a hero URL by default', () => {
      expect(carPhotoUrl('abc-123')).toBe('/api/public/car-photos/abc-123?size=hero')
    })
    it('honours the requested size', () => {
      expect(carPhotoUrl('abc-123', 'thumb')).toBe('/api/public/car-photos/abc-123?size=thumb')
    })
  })

  it('fetches the photo summary from the summary endpoint', async () => {
    get.mockResolvedValue({ data: [{ model: 'MODEL_3', heroCarId: 'c1', count: 3 }] })
    const result = await getModelPhotoSummaries()
    expect(get).toHaveBeenCalledWith('/public/model-photos/summary')
    expect(result).toEqual([{ model: 'MODEL_3', heroCarId: 'c1', count: 3 }])
  })

  it('fetches per-model car ids with an encoded model path', async () => {
    get.mockResolvedValue({ data: ['c1', 'c2'] })
    const result = await getModelPhotoCarIds('MODEL_3')
    expect(get).toHaveBeenCalledWith('/public/model-photos/MODEL_3')
    expect(result).toEqual(['c1', 'c2'])
  })
})
