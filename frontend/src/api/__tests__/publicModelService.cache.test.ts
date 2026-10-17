import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.hoisted(() => vi.fn())
vi.mock('../axios', () => ({ default: { get } }))

import { clearPublicModelCache, getModelStats, getTopModels } from '../publicModelService'

beforeEach(() => { clearPublicModelCache(); get.mockReset() })

describe('publicModelService cache', () => {
  it('asks the server once for the same figures, again for another model', async () => {
    get.mockResolvedValue({ data: { modelDisplayName: 'Model 3' } })
    await getModelStats('Tesla', 'Model_3')
    await getModelStats('Tesla', 'Model_3')
    await getModelStats('Tesla', 'Model_Y')
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('forgets a failed request, the next call asks again', async () => {
    get.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: [] })
    await expect(getTopModels(200)).rejects.toThrow('offline')
    await expect(getTopModels(200)).resolves.toEqual([])
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('asks again after five minutes', async () => {
    vi.useFakeTimers()
    get.mockResolvedValue({ data: [] })
    await getTopModels(200)
    vi.advanceTimersByTime(5 * 60_000)
    await getTopModels(200)
    vi.useRealTimers()
    expect(get).toHaveBeenCalledTimes(2)
  })
})
