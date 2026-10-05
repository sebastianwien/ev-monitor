import { describe, it, expect, vi, afterEach } from 'vitest'
import { geocodeSpokenAddress, addressPlace, resolveSpokenAddress, placeFromAddress } from '../voiceAddress'
import type { VoiceDraft } from '../wizardLogic'
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

describe('geocodeSpokenAddress near a position', () => {
  it('prefers hits around the known position without excluding others', async () => {
    const fetch = vi.fn().mockResolvedValue({ json: async () => [] })
    vi.stubGlobal('fetch', fetch)
    await geocodeSpokenAddress('Lindenweg 4', { lat: 49.9, lon: 10.9 })
    const url = fetch.mock.calls[0][0] as string
    expect(url).toContain('&viewbox=10.4,50.4,11.4,49.4')
    expect(url).not.toContain('bounded')
  })
})

const draft = (address: string | null, place: VoiceDraft['place']): VoiceDraft => ({ transcript: 'x', place, chargingProviderId: null,
  usage: { plan: 'admin', limit: null, remaining: null, resetsOn: '2026-11-01' },
  fields: { kwhCharged: null, kwhAtVehicle: null, socBefore: null, socAfter: null, odometerKm: null, costEur: null, pricePerKwh: null,
    loggedAt: null, chargeDurationMinutes: null, maxChargingPowerKw: null, chargingType: null, routeType: null, tireType: null,
    spokenAddress: address, uncertain: [] } })

describe('placeFromAddress', () => {
  it('only when an address was said and no concrete listed place came back', () => {
    expect(placeFromAddress(draft('Lindenweg 4', null))).toBe(true)
    expect(placeFromAddress(draft('Lindenweg 4', { kind: 'other', station: null, site: null, cpoName: 'Aral' }))).toBe(true)
    expect(placeFromAddress(draft('Lindenweg 4', { kind: 'station', station: station('EnBW'), site: null, cpoName: null }))).toBe(false)
    expect(placeFromAddress(draft(null, null))).toBe(false)
  })
})

describe('resolveSpokenAddress', () => {
  it('loads the stations at the address and picks the spoken operator there', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: async () => [{ place_id: 1, lat: '49.89', lon: '10.88', display_name: 'Hauptstraße 5, Bamberg' }] }))
    const aral = station('Aral')
    const loadStations = vi.fn().mockResolvedValue([aral])
    const r = await resolveSpokenAddress(draft('Hauptstraße 5', { kind: 'other', station: null, site: null, cpoName: 'Aral' }), { loadStations })
    expect(loadStations).toHaveBeenCalledWith(49.89, 10.88)
    expect(r).toMatchObject({ hit: { latitude: 49.89 }, choice: { kind: 'station', station: aral }, unsure: false })
  })

  it('is null when the address is not found, without loading stations', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: async () => [] }))
    const loadStations = vi.fn()
    expect(await resolveSpokenAddress(draft('Nirgendwo 1', null), { loadStations })).toBeNull()
    expect(loadStations).not.toHaveBeenCalled()
  })
})
