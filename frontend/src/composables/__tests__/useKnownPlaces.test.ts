import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../api/axios'
import { useKnownPlaces } from '../useKnownPlaces'

const place = { geohash: 'u33dc0', isPublic: false, usageCount: 4, lastUsedAt: '2026-09-27T18:00:00', cpoName: null,
  lastProviderId: null, placeName: 'Mitte', site: null, distanceMeters: null, here: false }

describe('useKnownPlaces', () => {
  beforeEach(() => vi.mocked(api.get).mockReset())

  it('lädt ohne Position die bekannten Orte des Nutzers', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [place] })
    const { places, load } = useKnownPlaces()
    await load()
    expect(api.get).toHaveBeenCalledWith('/charging-sites/known', { params: undefined })
    expect(places.value).toHaveLength(1)
  })

  it('gibt die Position nur als Vergleichswert mit', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [{ ...place, distanceMeters: 120, here: true }] })
    const { places, load } = useKnownPlaces()
    await load(52.52, 13.405)
    expect(api.get).toHaveBeenCalledWith('/charging-sites/known', { params: { lat: 52.52, lon: 13.405 } })
    expect(places.value[0].here).toBe(true)
  })

  it('Fehler ergeben eine leere Liste, eine spätere Antwort überholt keine neuere Anfrage', async () => {
    vi.mocked(api.get).mockImplementationOnce(() => Promise.reject(new Error('500')))
    const { places, load } = useKnownPlaces()
    await load()
    expect(places.value).toEqual([])

    let resolveOld!: (v: unknown) => void
    vi.mocked(api.get).mockImplementationOnce(() => new Promise(r => { resolveOld = r }))
    vi.mocked(api.get).mockResolvedValueOnce({ data: [place] })
    const old = load()
    await load(1, 1)
    resolveOld({ data: [] })
    await old
    expect(places.value).toHaveLength(1)
  })
})
