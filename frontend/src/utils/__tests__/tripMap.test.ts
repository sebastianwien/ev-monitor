import { describe, it, expect } from 'vitest'
import { tripMapView, tripLine, hasTripMap } from '../tripMap'

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

/**
 * Zwei Punkte bei Berlin als encodierte Polyline - der Inhalt ist hier egal, nur die
 * Rangfolge zaehlt: aufgezeichnet schlaegt gerechnet schlaegt gar nichts.
 */
const TRACE = '_ibmIsvspAoJoJ'
const SKETCH = '_ibmIsvspAwXwX'

describe('tripLine', () => {
  it('prefers the road-matched line - it is the trace, only snapped to real streets', () => {
    const line = tripLine(TRACE, SKETCH, 'MATCHED')!
    expect(line.source).toBe('matched')
    expect(line.points.length).toBe(2)
  })

  it('prefers the raw trace over a route that was merely guessed between the ends', () => {
    expect(tripLine(TRACE, SKETCH, 'SKETCH')!.source).toBe('trace')
    expect(tripLine(TRACE, SKETCH, null)!.source).toBe('trace')
  })

  it('falls back to the sketch when nothing was recorded', () => {
    expect(tripLine(null, SKETCH, 'SKETCH')!.source).toBe('sketch')
    expect(tripLine(undefined, SKETCH, null)!.source).toBe('sketch')
  })

  it('falls back to the trace when the matched line is unusable', () => {
    expect(tripLine(TRACE, null, 'MATCHED')!.source).toBe('trace')
    expect(tripLine(TRACE, '_p~iF~ps|U', 'MATCHED')!.source).toBe('trace')
  })

  it('returns null without any line - the caller then draws the beeline', () => {
    expect(tripLine(null, null, null)).toBeNull()
    expect(tripLine('', '', null)).toBeNull()
  })

  it('ignores a line of a single point - that is a position, not a route', () => {
    const singlePoint = '_p~iF~ps|U'
    expect(tripLine(singlePoint, null, null)).toBeNull()
    expect(tripLine(singlePoint, SKETCH, 'SKETCH')!.source).toBe('sketch')
  })
})

describe('hasTripMap', () => {
  it('is true as soon as any location data reached the client', () => {
    expect(hasTripMap({ locationStartGeohash: 'u33d0k' })).toBe(true)
    expect(hasTripMap({ locationEndGeohash: 'u33d0k' })).toBe(true)
    expect(hasTripMap({ tracePolyline: TRACE })).toBe(true)
  })

  it('is false without it - the backend withholds it from older trips of free users', () => {
    expect(hasTripMap({})).toBe(false)
    expect(hasTripMap(null)).toBe(false)
    expect(hasTripMap({ locationStartGeohash: null, tracePolyline: '' })).toBe(false)
  })

  it('ignores a route that has no place to sit - the line alone draws nothing', () => {
    // routePolyline ohne Geohashes kann es nicht geben; kaeme es doch vor, waere die
    // Karte leer statt falsch.
    expect(hasTripMap({ routePolyline: SKETCH })).toBe(false)
  })
})
