import { describe, it, expect } from 'vitest'
import {
  placeClass, benchmarkPairs, benchmarkKey, sortPlaces, placeName, needsOperator, pinSize, legendClasses,
  leadParts, periodWindow, tripWindowParams, drawableTripLines, focusZoom, tapPlace,
  type ChargingPlace, type PlaceClass, type PlaceSelection,
} from './storyPlaces'
import type { ChargingBenchmark } from '../api/chargingBenchmarkService'

function place(over: Partial<ChargingPlace> = {}): ChargingPlace {
  return {
    geohash: 'u1x3k7q',
    kind: 'PUBLIC',
    charges: 3,
    kwh: 128,
    costEur: 42.24,
    pricePerKwh: 0.33,
    lastChargedAt: '2026-08-28T10:00:00',
    lastLogId: 'log-1',
    cpoName: 'Tesla Supercharger Hamburg, Germany - Stillhorn',
    chargingType: 'DC',
    ...over,
  }
}

function bench(median: number, drivers = 15): ChargingBenchmark {
  return {
    cpoName: 'Tesla Supercharger', chargingType: 'DC', drivers, medianPricePerKwh: median,
    lowerQuartilePricePerKwh: median - 0.05, upperQuartilePricePerKwh: median + 0.05, cards: [],
  }
}

// Google-Beispiel: drei Stützpunkte
const LINE = '_p~iF~ps|U_ulLnnqC_mqNvxq`@'

describe('placeClass', () => {
  it('Zuhause ist immer home, auch mit Preis und Vergleich', () => {
    expect(placeClass(place({ kind: 'HOME' }), bench(0.2))).toBe('home')
  })

  it('ab 10 % über dem Median teurer, ab 10 % darunter günstiger, dazwischen üblich', () => {
    expect(placeClass(place({ pricePerKwh: 0.429 }), bench(0.39))).toBe('high')
    expect(placeClass(place({ pricePerKwh: 0.428 }), bench(0.39))).toBe('usual')
    expect(placeClass(place({ pricePerKwh: 0.351 }), bench(0.39))).toBe('low')
    expect(placeClass(place({ pricePerKwh: 0.352 }), bench(0.39))).toBe('usual')
  })

  it('ohne Vergleich, ohne Preis, privat oder unbekannt: none', () => {
    expect(placeClass(place(), null)).toBe('none')
    expect(placeClass(place({ pricePerKwh: null }), bench(0.39))).toBe('none')
    expect(placeClass(place({ kind: 'PRIVATE' }), bench(0.2))).toBe('none')
    expect(placeClass(place({ kind: 'UNKNOWN' }), bench(0.2))).toBe('none')
  })
})

describe('benchmarkPairs', () => {
  it('ein Aufruf je Paar Betreiber und Ladeart, nur öffentliche Orte mit beidem', () => {
    const places = [
      place({ geohash: 'a' }),
      place({ geohash: 'b' }),
      place({ geohash: 'c', chargingType: 'AC' }),
      place({ geohash: 'd', cpoName: null }),
      place({ geohash: 'e', chargingType: null }),
      place({ geohash: 'f', kind: 'PRIVATE', cpoName: 'EnBW' }),
    ]

    expect(benchmarkPairs(places)).toEqual([
      { cpo: 'Tesla Supercharger Hamburg, Germany - Stillhorn', type: 'DC' },
      { cpo: 'Tesla Supercharger Hamburg, Germany - Stillhorn', type: 'AC' },
    ])
    expect(benchmarkKey(places[0])).toBe('Tesla Supercharger Hamburg, Germany - Stillhorn|DC')
    expect(benchmarkKey(places[3])).toBeNull()
  })
})

describe('sortPlaces', () => {
  it('Zuhause zuerst, dann nach Euro absteigend', () => {
    const sorted = sortPlaces([
      place({ geohash: 'cheap', costEur: 5 }),
      place({ geohash: 'home', kind: 'HOME', costEur: 1 }),
      place({ geohash: 'dear', costEur: 80 }),
    ])

    expect(sorted.map(p => p.geohash)).toEqual(['home', 'dear', 'cheap'])
  })
})

describe('placeName', () => {
  it('Betreiber ohne Land und Standortzusatz', () => {
    expect(placeName(place())).toBe('Tesla Supercharger Hamburg')
    expect(placeName(place({ cpoName: 'Tesla Supercharger Graz, Austria - Webling' }))).toBe('Tesla Supercharger Graz')
    expect(placeName(place({ cpoName: 'IONITY - Raststätte Ost' }))).toBe('IONITY')
    expect(placeName(place({ cpoName: 'EWE Go' }))).toBe('EWE Go')
  })

  it('Bindestrich ohne Leerzeichen bleibt Teil des Namens', () => {
    expect(placeName(place({ cpoName: 'Stadtwerke Bergisch-Gladbach' }))).toBe('Stadtwerke Bergisch-Gladbach')
  })

  it('Zuhause und Orte ohne Betreiber haben keinen eigenen Namen', () => {
    expect(placeName(place({ kind: 'HOME', cpoName: 'EnBW' }))).toBeNull()
    expect(placeName(place({ cpoName: null }))).toBeNull()
    expect(placeName(place({ cpoName: '  ' }))).toBeNull()
  })
})

describe('needsOperator', () => {
  it('nur öffentliche Orte ohne Betreiber', () => {
    expect(needsOperator(place({ cpoName: null }))).toBe(true)
    expect(needsOperator(place())).toBe(false)
    expect(needsOperator(place({ kind: 'UNKNOWN', cpoName: null }))).toBe(false)
    expect(needsOperator(place({ kind: 'PRIVATE', cpoName: null }))).toBe(false)
  })
})

describe('pinSize', () => {
  it('16 bis 34 px nach Wurzel der kWh', () => {
    expect(pinSize(0)).toBe(16)
    expect(pinSize(16)).toBe(16)
    expect(pinSize(100)).toBe(23)
    expect(pinSize(400)).toBe(34)
    expect(pinSize(5000)).toBe(34)
  })
})

describe('legendClasses', () => {
  it('nur vorkommende Klassen, feste Reihenfolge, Zuhause ohne Eintrag', () => {
    const classes: PlaceClass[] = ['high', 'home', 'none', 'high', 'usual']

    expect(legendClasses(classes)).toEqual(['usual', 'none', 'high'])
  })
})

describe('leadParts', () => {
  it('ohne Orte: empty', () => {
    expect(leadParts([])).toEqual({ key: 'empty', places: 0, high: 0 })
  })

  it('mit teuren Orten nennt der Satz deren Zahl', () => {
    expect(leadParts(['home', 'high', 'usual', 'high'])).toEqual({ key: 'high', places: 4, high: 2 })
  })

  it('ohne teure Orte endet der Satz nach der Ortszahl', () => {
    expect(leadParts(['home', 'low'])).toEqual({ key: 'plain', places: 2, high: 0 })
  })
})

describe('periodWindow', () => {
  const today = new Date(2026, 9, 25, 18, 30)

  it('Monat der Story: erster bis letzter Tag', () => {
    expect(periodWindow('month', { year: 2026, month: 8 }, today)).toEqual({ from: '2026-08-01', to: '2026-08-31' })
  })

  it('12 Monate: heute vor einem Jahr bis heute, wie LAST_12_MONTHS', () => {
    expect(periodWindow('12m', { year: 2026, month: 8 }, today)).toEqual({ from: '2025-10-25', to: '2026-10-25' })
  })

  it('Alles: offen', () => {
    expect(periodWindow('all', { year: 2026, month: 8 }, today)).toEqual({ from: null, to: null })
  })
})

describe('tripWindowParams', () => {
  it('Tage als Zeitpunkte, das Ende schließt den letzten Tag ein', () => {
    expect(tripWindowParams({ from: '2026-08-01', to: '2026-08-31' }))
      .toEqual({ from: '2026-08-01T00:00:00.000Z', to: '2026-09-01T00:00:00.000Z' })
  })

  it('offenes Fenster ohne Parameter', () => {
    expect(tripWindowParams({ from: null, to: null })).toEqual({})
  })
})

describe('drawableTripLines', () => {
  it('gematchte und rohe Spur ja, Skizze nein', () => {
    const lines = drawableTripLines([
      { id: 'matched', routePolyline: LINE, routeKind: 'MATCHED', tracePolyline: null },
      { id: 'trace', routePolyline: null, routeKind: null, tracePolyline: LINE },
      { id: 'sketch', routePolyline: LINE, routeKind: 'SKETCH', tracePolyline: null },
      { id: 'none', routePolyline: null, routeKind: null, tracePolyline: null },
    ])

    expect(lines.map(l => [l.id, l.source])).toEqual([['matched', 'matched'], ['trace', 'trace']])
    expect(lines[0].points).toHaveLength(3)
  })
})

describe('focusZoom', () => {
  it('nicht genauer, als der Ort gespeichert ist', () => {
    expect(focusZoom('u1x3k7q')).toBe(15)
    expect(focusZoom('u1x0yz')).toBe(14)
    // Ältere Ladungen tragen 5 Zeichen (rund 5 km): dann nur die Gegend
    expect(focusZoom('u33df')).toBe(12)
    expect(focusZoom('u33')).toBe(12)
  })
})

describe('tapPlace', () => {
  const start = { center: [53.5, 10.0] as [number, number], zoom: 10 }
  const elsewhere = { center: [53.6, 10.2] as [number, number], zoom: 15 }
  const none: PlaceSelection<typeof start> = { active: null, saved: null }

  it('erster Tipp markiert, zoomt hin und merkt den Ausschnitt davor', () => {
    expect(tapPlace(none, 'a', start)).toEqual({ state: { active: 'a', saved: start }, focus: 'a', restore: null })
  })

  it('zweiter Tipp auf dasselbe hebt auf und stellt den Ausschnitt wieder her', () => {
    const first = tapPlace(none, 'a', start).state

    expect(tapPlace(first, 'a', elsewhere)).toEqual({ state: none, focus: null, restore: start })
  })

  it('Wechsel auf ein anderes behält den ursprünglichen Ausschnitt', () => {
    const first = tapPlace(none, 'a', start).state
    const second = tapPlace(first, 'b', elsewhere)

    expect(second).toEqual({ state: { active: 'b', saved: start }, focus: 'b', restore: null })
    expect(tapPlace(second.state, 'b', elsewhere).restore).toEqual(start)
  })
})
