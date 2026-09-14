import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../api/axios'
import { useRecentSites } from '../useRecentSites'

describe('useRecentSites', () => {
  beforeEach(() => vi.mocked(api.get).mockReset())

  it('lädt die zuletzt genutzten Standorte des Nutzers', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [{ id: 's1', name: 'Kaufland', cpoName: 'Kaufland', geohash: 'u33dc0c', maxPowerKw: 93, chargePoints: 2, fastCharging: true, lastUsedAt: '2026-09-10T10:00:00', usageCount: 3 }] })
    const { sites, load } = useRecentSites()
    await load()
    expect(api.get).toHaveBeenCalledWith('/charging-sites/recent')
    expect(sites.value).toHaveLength(1)
  })

  it('Fehler ergeben eine leere Liste', async () => {
    vi.mocked(api.get).mockImplementationOnce(() => Promise.reject(new Error('500')))
    const { sites, load } = useRecentSites()
    await load()
    expect(sites.value).toEqual([])
  })
})
