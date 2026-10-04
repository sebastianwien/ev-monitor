import { describe, it, expect } from 'vitest'
import { placeKindOf, knownPlaceTitle, stationIsKnown } from '../knownPlace'
import type { KnownPlace, RecentSite } from '../../../composables/useKnownPlaces'
import type { StationMatch } from '../../../composables/useNearbyStations'

const site: RecentSite = { id: 's1', name: 'EnBW Kaufland', cpoName: 'EnBW', geohash: 'u33dc0c', maxAcKw: null, maxDcKw: 150,
  chargePoints: 4, fastCharging: true, address: null, plugTypes: ['CCS'], lastUsedAt: '2026-09-24T10:00:00', usageCount: 7 }
const known = (o: Partial<KnownPlace>): KnownPlace => ({ geohash: 'u33dc0', isPublic: false, usageCount: 4, lastUsedAt: '2026-09-27T18:00:00',
  cpoName: null, lastProviderId: null, placeName: null, site: null, here: false, ...o })
const t = (k: string) => k

describe('placeKindOf', () => {
  it('Säule, öffentlicher Anbieter ohne Säule, sonst Zuhause', () => {
    expect(placeKindOf(known({ site, isPublic: true }))).toBe('site')
    expect(placeKindOf(known({ isPublic: true, cpoName: 'Ionity' }))).toBe('other')
    expect(placeKindOf(known({ isPublic: false }))).toBe('home')
  })
})

describe('knownPlaceTitle', () => {
  it('Säulenname vor Ortsteil vor Anbieter vor Privat, nie ein nacktes Ort', () => {
    expect(knownPlaceTitle(known({ site, placeName: 'Mitte' }), t)).toBe('EnBW Kaufland')
    expect(knownPlaceTitle(known({ placeName: 'Prenzlauer Berg', isPublic: true, cpoName: 'Ionity' }), t)).toBe('Prenzlauer Berg')
    expect(knownPlaceTitle(known({ isPublic: true, cpoName: 'Ionity' }), t)).toBe('Ionity')
    expect(knownPlaceTitle(known({ isPublic: false, cpoName: 'Ionity' }), t)).toBe('logwizard.known_private')
  })
})

describe('stationIsKnown', () => {
  const enbw: StationMatch = { name: 'enbw kaufland', known: true, maxAcKw: null, maxDcKw: 150, fastCharging: true, chargePoints: 4,
    address: null, plugTypes: [], registerId: null, geohash: 'u33dc0c' }

  it('gleiche Zelle und gleicher Name ohne Groß-Kleinschreibung, sonst nicht', () => {
    expect(stationIsKnown(enbw, [known({ site, isPublic: true, here: true })])).toBe(true)
    expect(stationIsKnown(enbw, [known({ site: { ...site, geohash: 'u33dc0d' }, isPublic: true })])).toBe(false)
    expect(stationIsKnown(enbw, [known({ placeName: 'Mitte' })])).toBe(false)
  })
})
