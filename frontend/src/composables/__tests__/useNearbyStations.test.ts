import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../api/axios'
import { useNearbyStations } from '../useNearbyStations'

describe('useNearbyStations', () => {
  beforeEach(() => vi.mocked(api.get).mockReset())

  it('lädt Standorte über den Umkreis-Endpoint', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [{ name: 'IONITY', known: true, distanceMeters: 40, maxPowerKw: 350, fastCharging: true, chargePoints: 6 }] })
    const { stations, load, loading } = useNearbyStations()
    await load(52.52, 13.405)
    expect(api.get).toHaveBeenCalledWith('/charging-provider-tariffs/cpos/nearby-stations', { params: { lat: 52.52, lon: 13.405 } })
    expect(stations.value).toHaveLength(1)
    expect(loading.value).toBe(false)
  })

  it('Fehler und unerwartete Antworten ergeben eine leere Liste', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('429'))
    const { stations, load } = useNearbyStations()
    await load(52.52, 13.405)
    expect(stations.value).toEqual([])
    vi.mocked(api.get).mockResolvedValue({ data: { message: 'nope' } })
    await load(52.52, 13.405)
    expect(stations.value).toEqual([])
  })
})
