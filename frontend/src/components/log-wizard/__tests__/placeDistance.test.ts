import { describe, it, expect } from 'vitest'
import ngeohash from 'ngeohash'
import { sitesByDistance, isLocationInaccurate, formatDistance } from '../placeDistance'
import type { RecentSite } from '../../../composables/useRecentSites'

const site = (id: string, lat: number, lon: number): RecentSite => ({
  id, name: id, cpoName: null, geohash: ngeohash.encode(lat, lon, 7), maxAcKw: 22, maxDcKw: null,
  chargePoints: 2, fastCharging: false, address: null, plugTypes: [], lastUsedAt: '2026-10-01T10:00:00Z', usageCount: 3,
} as RecentSite)

describe('sitesByDistance', () => {
  it('sortiert die letzten Ladeorte nach Entfernung zur Position', () => {
    const far = site('far', 52.60, 13.45)      // rund 7 km nördlich
    const near = site('near', 52.536, 13.458)  // rund 500 m
    const sorted = sitesByDistance([far, near], 52.5342, 13.4516)
    expect(sorted.map(s => s.site.id)).toEqual(['near', 'far'])
    expect(sorted[0].distanceMeters).toBeGreaterThan(300)
    expect(sorted[0].distanceMeters).toBeLessThan(700)
    expect(sorted[1].distanceMeters).toBeGreaterThan(6500)
  })

  it('ohne Position bleibt die Reihenfolge, ohne Entfernung', () => {
    const a = site('a', 52.6, 13.4), b = site('b', 52.5, 13.4)
    expect(sitesByDistance([a, b], null, null)).toEqual([{ site: a, distanceMeters: null }, { site: b, distanceMeters: null }])
  })
})

describe('isLocationInaccurate', () => {
  it('erst ab mehr als 500 m Ungenauigkeit', () => {
    expect(isLocationInaccurate(500)).toBe(false)
    expect(isLocationInaccurate(1200)).toBe(true)
    expect(isLocationInaccurate(null)).toBe(false)
  })
})

describe('formatDistance', () => {
  it('Meter unter 1 km, sonst km mit einer Stelle', () => {
    expect(formatDistance(480, 'de')).toBe('480 m')
    expect(formatDistance(4120, 'de')).toBe('4,1 km')
    expect(formatDistance(2500, 'en')).toBe('2.5 km')
    expect(formatDistance(1000, 'de')).toBe('1 km')
  })
})
