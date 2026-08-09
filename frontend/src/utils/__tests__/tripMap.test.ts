import { describe, it, expect } from 'vitest'
import { tripMapView } from '../tripMap'

// u33d0k / u33d0m are neighbouring geohash-6 cells near Berlin.
describe('tripMapView', () => {
  it('decodes start and end into cell centres', () => {
    const view = tripMapView('u33d0k', 'u33d0m')!
    expect(view.start!.lat).toBeCloseTo(52.41, 2)
    expect(view.start!.lon).toBeCloseTo(13.38, 2)
    expect(view.end).not.toBeNull()
    expect(view.roundTrip).toBe(false)
  })

  it('marks start and end in the same cell as a round trip - no line to draw', () => {
    const view = tripMapView('u33d0k', 'u33d0k')!
    expect(view.roundTrip).toBe(true)
    expect(view.end).toBeNull()
  })

  it('works with only one known end of the trip', () => {
    expect(tripMapView('u33d0k', null)!.end).toBeNull()
    expect(tripMapView(null, 'u33d0k')!.start).toBeNull()
    expect(tripMapView(null, 'u33d0k')!.end).not.toBeNull()
  })

  it('returns null when nothing is known', () => {
    expect(tripMapView(null, null)).toBeNull()
    expect(tripMapView('', undefined)).toBeNull()
  })

  it('returns null for geohashes that cannot be decoded', () => {
    // 'a', 'i', 'l', 'o' are not part of the geohash alphabet
    expect(tripMapView('ailo!!', null)).toBeNull()
  })

  it('exposes the cell radius in metres so the marker can show the real blur', () => {
    const view = tripMapView('u33d0k', null)!
    // geohash-6 cell is ~1.2 km x 0.6 km → half diagonal ~680 m
    expect(view.cellRadiusMeters).toBeGreaterThan(400)
    expect(view.cellRadiusMeters).toBeLessThan(900)
  })
})
