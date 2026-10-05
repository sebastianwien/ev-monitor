import { describe, it, expect, vi, afterEach } from 'vitest'
import { geocodeSpokenAddress, addressPlace } from '../voiceAddress'
import type { StationMatch } from '../../../composables/useNearbyStations'

const station = (name: string): StationMatch => ({ name, known: true, maxAcKw: 22, maxDcKw: null, fastCharging: false, chargePoints: 2,
  address: null, plugTypes: [], registerId: 1, geohash: 'u33dbcd', latitude: 1, longitude: 1 } as StationMatch)

afterEach(() => vi.unstubAllGlobals())

describe('geocodeSpokenAddress', () => {
  it('takes the first Nominatim hit with a short name', async () => {
    const fetch = vi.fn().mockResolvedValue({ json: async () => [{ place_id: 1, lat: '49.89', lon: '10.88', display_name: 'x',
      address: { road: 'Lindenweg', house_number: '4', city: 'Bamberg' } }] })
    vi.stubGlobal('fetch', fetch)
    expect(await geocodeSpokenAddress('Lindenweg 4, Bamberg')).toEqual({ latitude: 49.89, longitude: 10.88, name: 'Lindenweg 4, Bamberg' })
    expect(fetch.mock.calls[0][0]).toContain('q=Lindenweg%204%2C%20Bamberg')
  })

  it('returns null without a hit or when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: async () => [] }))
    expect(await geocodeSpokenAddress('Nirgendwo 1')).toBeNull()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    expect(await geocodeSpokenAddress('Nirgendwo 1')).toBeNull()
  })
})

describe('addressPlace', () => {
  it('a spoken operator picks its station at the address', () => {
    const enbw = station('EnBW')
    expect(addressPlace([station('Aral'), enbw], 'enbw')).toEqual({ choice: { kind: 'station', station: enbw }, unsure: false })
  })

  it('an operator without a station there stays a free operator', () => {
    expect(addressPlace([station('Aral')], 'EnBW')).toEqual({ choice: { kind: 'other', cpoName: 'EnBW' }, unsure: true })
  })

  it('no operator means private at the address, unsure if stations are nearby', () => {
    expect(addressPlace([], null)).toEqual({ choice: { kind: 'home' }, unsure: false })
    expect(addressPlace([station('Aral')], null)).toEqual({ choice: { kind: 'home' }, unsure: true })
  })
})
