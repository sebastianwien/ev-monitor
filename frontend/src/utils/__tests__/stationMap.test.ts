import { describe, it, expect } from 'vitest'
import geohash from 'ngeohash'
import {
  cellsForBounds, neighborRing, distanceMeters, sortByDistance, inBounds, routeUrl, MAX_CELLS,
  formatDistance, formatPricePerKwh, plugLine, feeLine, latestVerified,
} from '../stationMap'

/** Hannover Kröpcke, ein öffentlicher Platz. */
const KROEPCKE = { lat: 52.3745, lon: 9.7385 }

/** Ausschnitt um einen Punkt, halbe Breite und Höhe in Grad. */
const around = (p: { lat: number; lon: number }, dLat: number, dLon: number) =>
  ({ south: p.lat - dLat, west: p.lon - dLon, north: p.lat + dLat, east: p.lon + dLon })

describe('cellsForBounds', () => {
  it('deckt den Ausschnitt mit Geohash-6-Zellen ab, die mittlere zuerst', () => {
    const cells = cellsForBounds(around(KROEPCKE, 0.004, 0.006))!
    expect(cells.length).toBeGreaterThan(1)
    expect(cells.every(c => /^[0-9b-hjkmnp-z]{6}$/.test(c))).toBe(true)
    expect(cells[0]).toBe(geohash.encode(KROEPCKE.lat, KROEPCKE.lon, 6))
    expect(new Set(cells).size).toBe(cells.length)
  })

  it('am Rand: auch die Zelle, in die der Ausschnitt nur knapp hineinragt', () => {
    const [s, w, n, e] = geohash.decode_bbox(geohash.encode(KROEPCKE.lat, KROEPCKE.lon, 6))
    // Ausschnitt reicht 10 m über die Ostkante der Zelle
    const cells = cellsForBounds({ south: s + 0.0001, west: w + 0.0001, north: n - 0.0001, east: e + 0.00015 })!
    expect(cells).toHaveLength(2)
    expect(cells).toContain(geohash.neighbor(geohash.encode(KROEPCKE.lat, KROEPCKE.lon, 6), [0, 1]))
  })

  it('mehr als zwölf Zellen: null, die Karte bittet ums Heranzoomen', () => {
    expect(cellsForBounds(around(KROEPCKE, 0.05, 0.08))).toBeNull()
    expect(cellsForBounds(around(KROEPCKE, 3, 5))).toBeNull()
    expect(MAX_CELLS).toBe(12)
  })
})

describe('neighborRing', () => {
  it('die Nachbarn um die Zellen, ohne die Zellen selbst', () => {
    const c = geohash.encode(KROEPCKE.lat, KROEPCKE.lon, 6)
    const ring = neighborRing([c])
    expect(ring).toHaveLength(8)
    expect(ring).not.toContain(c)
    expect(neighborRing([c, ...geohash.neighbors(c)])).toHaveLength(16)
  })
})

describe('Entfernung und Sortierung', () => {
  it('Haversine in Metern', () => {
    // 0,001 Grad Breite sind rund 111 m
    expect(distanceMeters(KROEPCKE, { lat: KROEPCKE.lat + 0.001, lon: KROEPCKE.lon })).toBeCloseTo(111, 0)
  })

  it('nach Entfernung zum Kartenmittelpunkt, die nächste zuerst', () => {
    const far = { id: 'far', latitude: KROEPCKE.lat + 0.01, longitude: KROEPCKE.lon }
    const near = { id: 'near', latitude: KROEPCKE.lat + 0.001, longitude: KROEPCKE.lon }
    expect(sortByDistance([far, near], KROEPCKE).map(s => s.id)).toEqual(['near', 'far'])
  })

  it('im Ausschnitt', () => {
    const b = around(KROEPCKE, 0.01, 0.01)
    expect(inBounds({ latitude: KROEPCKE.lat, longitude: KROEPCKE.lon }, b)).toBe(true)
    expect(inBounds({ latitude: KROEPCKE.lat + 0.02, longitude: KROEPCKE.lon }, b)).toBe(false)
  })
})

describe('Formate', () => {
  it('Entfernung: Meter auf 10 gerundet, ab einem Kilometer mit einer Stelle', () => {
    expect(formatDistance(248, 'de')).toBe('250 m')
    expect(formatDistance(1149, 'de')).toBe('1,1 km')
    expect(formatDistance(1149, 'en')).toBe('1.1 km')
    expect(formatDistance(12_400, 'de')).toBe('12 km')
    // knapp unter einem Kilometer rundet auf 1.000 m: dann schon in Kilometern
    expect(formatDistance(997, 'de')).toBe('1,0 km')
  })

  it('Preis je kWh in Euro mit zwei Stellen', () => {
    expect(formatPricePerKwh(0.519, 'de')).toBe('0,52 €')
    expect(formatPricePerKwh(0.39, 'en')).toBe('€0.39')
  })

  it('Stecker und Leistung der gewählten Ladeart', () => {
    const s = { maxAcKw: 22, maxDcKw: 150, chargePoints: 4, plugTypes: ['Typ 2', 'CCS'] }
    expect(plugLine(s, 'DC')).toEqual({ plugs: 'CCS', kw: 150, points: 4 })
    expect(plugLine(s, 'AC')).toEqual({ plugs: 'Typ 2', kw: 22, points: 4 })
    expect(plugLine({ maxAcKw: 11, maxDcKw: null, chargePoints: 1, plugTypes: ['Schuko', 'Typ 2'] }, 'AC'))
      .toEqual({ plugs: 'Typ 2, Schuko', kw: 11, points: 1 })
  })

  it('jüngstes Prüfdatum der gezeigten Tarife', () => {
    expect(latestVerified([{ verifiedOn: '2026-11-01' }, { verifiedOn: null }, { verifiedOn: '2026-11-03' }])).toBe('2026-11-03')
    expect(latestVerified([{ verifiedOn: null }])).toBeNull()
  })
})

describe('feeLine', () => {
  it('ohne Start- und Blockiergebühr', () => {
    expect(feeLine({ sessionFeeEur: 0, blockingFeePerMin: null, blockingFeeAfterMin: null, blockingFeeCapEur: null }))
      .toEqual({ kind: 'none' })
  })

  it('Startgebühr und Blockiergebühr mit Beginn und Deckel', () => {
    expect(feeLine({ sessionFeeEur: 0.49, blockingFeePerMin: 0.15, blockingFeeAfterMin: 45, blockingFeeCapEur: 12 }))
      .toEqual({ kind: 'fees', sessionFeeEur: 0.49, blocking: { perMin: 0.15, afterMin: 45, capEur: 12 } })
  })

  it('Blockiergebühr ohne Startgebühr', () => {
    expect(feeLine({ sessionFeeEur: null, blockingFeePerMin: 0.1, blockingFeeAfterMin: 240, blockingFeeCapEur: null }))
      .toEqual({ kind: 'fees', sessionFeeEur: null, blocking: { perMin: 0.1, afterMin: 240, capEur: null } })
  })
})

describe('routeUrl', () => {
  it('iPhone öffnet Apple Karten', () => {
    expect(routeUrl(52.3745, 9.7385, true)).toBe('https://maps.apple.com/?daddr=52.3745,9.7385')
  })

  it('sonst Google Maps', () => {
    expect(routeUrl(52.3745, 9.7385, false)).toBe('https://www.google.com/maps/dir/?api=1&destination=52.3745,9.7385')
  })
})
