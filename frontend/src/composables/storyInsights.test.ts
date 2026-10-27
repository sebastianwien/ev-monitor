import { describe, it, expect } from 'vitest'
import {
  temperatureEffect, scatterYScale, modelPeers, dataQuality, countPricelessInMonth, bestRank, ranksFromStandings, buildLatest, sohIsEstimated, buildTripMonth,
  COLD_MAX_C, MILD_MIN_C, MIN_TRIPS_PER_SIDE,
} from './storyInsights'
import type { PeerModelComparisonItem } from '../api/peerModelComparisonService'

function trip(temp: number | null, kwh: number, km = 50) {
  return { id: `${temp}-${kwh}`, tripStartedAt: null, tripEndedAt: null, distanceKm: km, socStart: null, socEnd: null, estimatedConsumedKwh: kwh, outsideTempCelsius: temp }
}

describe('temperatureEffect', () => {
  it('liefert null unter drei auswertbaren Fahrten', () => {
    expect(temperatureEffect([trip(2, 10), trip(20, 7)], null)).toBeNull()
    expect(temperatureEffect([trip(null, 10), trip(null, 7), trip(null, 8)], null)).toBeNull()
  })

  it('bildet Punkte, 5-Grad-Bins mit streckengewichtetem Mittel und das Kalt-gegen-Mild-Delta', () => {
    const cold = [trip(-3, 12), trip(1, 11), trip(4, 11.5)]
    const mild = [trip(16, 8), trip(18, 8.2), trip(22, 7.8)]
    const e = temperatureEffect([...cold, ...mild], null)!
    expect(e.points).toHaveLength(6)
    expect(e.points[0]).toEqual({ temp: -3, kwh100: 24, km: 50 })
    expect(e.bins.map(b => b.tempFrom)).toEqual([-5, 0, 15, 20])
    expect(e.bins.find(b => b.tempFrom === 0)!.kwh100).toBeCloseTo(22.5)
    // gleiche Strecken: kalt (24+22+23)/3 = 23, mild 16 -> +43,75 %
    expect(e.coldKwh100).toBeCloseTo(23)
    expect(e.mildKwh100).toBeCloseTo(16)
    expect(e.deltaPercent).toBeCloseTo(43.75)
  })

  it('hat kein Delta, wenn eine Seite zu wenige Fahrten hat', () => {
    const e = temperatureEffect([trip(1, 12), trip(2, 11), trip(18, 8), trip(19, 8), trip(20, 8)], null)!
    expect(e.deltaPercent).toBeNull()
    expect(e.coldKwh100).toBeNull()
    expect(MIN_TRIPS_PER_SIDE).toBe(3)
    expect(COLD_MAX_C).toBeLessThan(MILD_MIN_C)
  })

  it('behaelt Kurzstrecken, gewichtet aber nach Strecke wie das Backend (Summe kWh durch Summe km)', () => {
    // 0,4 km mit 0,2 kWh sind 50 kWh/100 km als Punkt, im Bin zaehlen sie nur mit 0,4 km
    const short = trip(14, 0.2, 0.4)
    const e = temperatureEffect([short, trip(12, 9), trip(13, 9), trip(16, 8)], null)!
    expect(e.points.map(p => p.temp)).toEqual([12, 13, 14, 16])
    expect(e.points.find(p => p.temp === 14)!.kwh100).toBeCloseTo(50)
    // Bin 10 bis 15: (9 + 9 + 0,2) kWh auf 100,4 km, ein einfaches Mittel laege bei 22,7
    expect(e.bins.find(b => b.tempFrom === 10)!.kwh100).toBeCloseTo(18.13, 1)
    expect(e.bins.find(b => b.tempFrom === 10)!.count).toBe(3)
  })

  it('gewichtet auch das Kalt-gegen-Mild-Delta nach Strecke', () => {
    // kalt: 200 km mit 40 kWh (20) und zwei 1-km-Fahrten mit 1 kWh (100) -> 42/202 = 20,8
    const cold = [trip(1, 40, 200), trip(2, 1, 1), trip(3, 1, 1)]
    const mild = [trip(18, 16, 100), trip(19, 16, 100), trip(20, 16, 100)]
    const e = temperatureEffect([...cold, ...mild], null)!
    expect(e.coldKwh100).toBeCloseTo(20.79, 1)
    expect(e.mildKwh100).toBeCloseTo(16)
    expect(e.deltaPercent).toBeCloseTo(29.95, 0)
  })

  it('nutzt SoC-Delta mit Kapazitaet, wenn keine kWh gemeldet sind', () => {
    const t = { ...trip(10, 0), estimatedConsumedKwh: null, socStart: 80, socEnd: 60 }
    const e = temperatureEffect([t, trip(12, 9), trip(14, 9)], 50)!
    // 20 % von 50 kWh = 10 kWh auf 50 km = 20 kWh/100 km
    expect(e.points.find(p => p.temp === 10)!.kwh100).toBeCloseTo(20)
  })
})

describe('modelPeers', () => {
  const item = (name: string, cons: number | null, cost: number | null, cars: number, isUser = false): PeerModelComparisonItem => ({
    vehicleSpecificationId: name,
    displayName: name,
    isUserVehicleSpec: isUser,
    netBatteryCapacityKwh: 60,
    userMetrics: isUser ? { consumptionKwhPer100km: 18.8, costPerKwh: 0.31 } : null,
    peerMetrics: { avgConsumptionKwhPer100km: cons, avgCostPerKwh: cost, uniqueCars: cars },
  })
  const all = { avgConsumptionKwhPer100km: 17.9, avgCostPerKwh: 0.24, uniqueCars: 50 }
  const mine = { costPerKwh: 0.31, consumption: 18.8 }

  it('alle Varianten gegen den eigenen Wert: Unterschied je 100 km, Preis erklärt mehr als Verbrauch', () => {
    const r = modelPeers({ modelComparisons: [], allVariants: all }, mine)!
    expect(r.cars).toBe(50)
    expect(r.theirs.costPer100km).toBeCloseTo(4.296)
    expect(r.mine.costPer100km).toBeCloseTo(5.828)
    expect(r.deltaPercent).toBeCloseTo(-26.3, 1)
    expect(r.reason).toBe('price')
  })

  it('Verbrauch als Grund, wenn er den größeren Anteil hat', () => {
    const r = modelPeers({ modelComparisons: [], allVariants: { avgConsumptionKwhPer100km: 15, avgCostPerKwh: 0.30, uniqueCars: 9 } }, mine)!
    expect(r.reason).toBe('consumption')
  })

  it('ohne Werte aller Varianten oder ohne eigenen Wert kein Vergleich', () => {
    expect(modelPeers({ modelComparisons: [], allVariants: null }, mine)).toBeNull()
    expect(modelPeers({ modelComparisons: [], allVariants: { ...all, avgCostPerKwh: null } }, mine)).toBeNull()
    expect(modelPeers({ modelComparisons: [], allVariants: all }, null)).toBeNull()
    expect(modelPeers(null, mine)).toBeNull()
  })

  it('Varianten mit vollständigen Werten, nach Euro je 100 km, Name ohne Akkugröße', () => {
    const r = modelPeers({ modelComparisons: [
      item('Teuer (75.00kWh)', 20, 0.40, 12), item('Meins (60.00kWh)', 18, 0.25, 1, true), item('Billig (82.00kWh)', 15, 0.20, 30), item('Leer', null, 0.2, 8),
    ], allVariants: all }, mine)!
    expect(r.variants.map(v => v.name)).toEqual(['Billig', 'Meins', 'Teuer'])
    expect(r.variants[1]).toMatchObject({ isUser: true, cars: 1 })
    expect(r.variants[0].costPer100km).toBeCloseTo(3)
  })
})

describe('dataQuality', () => {
  const base = { totalCharges: 20, estimatedConsumptionCount: 0 }

  it('ist ok ohne Schaetzungen und ohne preislose Ladungen', () => {
    const q = dataQuality(base, 0)
    expect(q.severity).toBe('ok')
    expect(q.estimatedShare).toBe(0)
  })

  it('warnt ab 25 Prozent Schaetzung oder einer preislosen Ladung', () => {
    expect(dataQuality({ ...base, estimatedConsumptionCount: 5 }, 0).severity).toBe('warn')
    expect(dataQuality(base, 1).severity).toBe('warn')
  })

  it('ist schlecht ab 50 Prozent Schaetzung oder einem Viertel ohne Preis', () => {
    expect(dataQuality({ ...base, estimatedConsumptionCount: 10 }, 0).severity).toBe('bad')
    expect(dataQuality(base, 5).severity).toBe('bad')
  })

  it('teilt nicht durch null', () => {
    expect(dataQuality({ totalCharges: 0, estimatedConsumptionCount: 0 }, 0).estimatedShare).toBe(0)
  })

  it('trennt offene Lücken von Preisen, die der Nutzer nicht mehr weiß', () => {
    expect(dataQuality(base, 3, 1)).toMatchObject({ priceless: 3, pricelessOpen: 1, severity: 'warn' })
    expect(dataQuality(base, 2).pricelessOpen).toBe(2)
  })
})

describe('countPricelessInMonth', () => {
  it('zaehlt nur Logs im Monat', () => {
    const logs = [{ loggedAt: '2026-09-30T23:30:00Z' }, { loggedAt: '2026-10-01T06:00:00Z' }, { loggedAt: '2026-10-31T22:00:00Z' }, { loggedAt: '2026-11-01T00:00:00Z' }]
    expect(countPricelessInMonth(logs, 2026, 10)).toBe(2)
  })
})

describe('bestRank', () => {
  it('nimmt den besten eigenen Rang ueber alle Kategorien', () => {
    const r = bestRank([
      { category: 'MONTHLY_KWH', ownEntry: { rank: 7, value: 300, unit: 'kWh' } },
      null,
      { category: 'MONTHLY_NIGHT_OWL', ownEntry: { rank: 2, value: 9, unit: '' } },
      { category: 'MONTHLY_DISTANCE', ownEntry: null },
    ])
    expect(r).toEqual({ category: 'MONTHLY_NIGHT_OWL', rank: 2, value: 9, unit: '' })
  })

  it('liefert null ohne eigenen Eintrag', () => {
    expect(bestRank([null, { category: 'MONTHLY_KWH', ownEntry: null }])).toBeNull()
  })
})

describe('ranksFromStandings', () => {
  /** Antwort von GET /public/leaderboard/standings/me, wie das Backend sie baut (MyLeaderboardStandingDTO). */
  const standings = [
    { category: 'MONTHLY_KWH', displayName: 'kWh', unit: 'kWh', lowerIsBetter: false, rank: 2, value: 312.5, rankDelta: 1, isNew: false, carLabel: 'Tesla Model 3' },
    { category: 'MONTHLY_CHARGES', displayName: 'Ladungen', unit: '', lowerIsBetter: false, rank: 14, value: 9, rankDelta: null, isNew: false, carLabel: 'Tesla Model 3' },
    { category: 'MONTHLY_DISTANCE', displayName: 'km', unit: 'km', lowerIsBetter: false, rank: null, value: null, rankDelta: null, isNew: false, carLabel: null },
  ]

  it('liest den eigenen Platz auch in den Top 10, den die Kategorie-Liste nicht als ownEntry nennt', () => {
    const r = ranksFromStandings(standings)
    expect(r).toEqual([
      { category: 'MONTHLY_KWH', ownEntry: { rank: 2, value: 312.5, unit: 'kWh' } },
      { category: 'MONTHLY_CHARGES', ownEntry: { rank: 14, value: 9, unit: '' } },
      { category: 'MONTHLY_DISTANCE', ownEntry: null },
    ])
    expect(bestRank(r)).toEqual({ category: 'MONTHLY_KWH', rank: 2, value: 312.5, unit: 'kWh' })
  })

  it('ohne Platzierung in allen Kategorien gibt es keinen Rang', () => {
    expect(bestRank(ranksFromStandings([standings[2]]))).toBeNull()
    expect(bestRank(ranksFromStandings([]))).toBeNull()
  })
})

describe('buildLatest', () => {
  const log = { id: 'l1', loggedAt: '2026-10-01T18:00:00Z', kwhCharged: 34.2, kwhAtVehicle: 31.9, costEur: 12.4 }
  const trip = { id: 't1', tripStartedAt: '2026-10-02T07:00:00Z', tripEndedAt: '2026-10-02T07:40:00Z', distanceKm: 23.4 }

  it('nimmt Fahrzeug-kWh vor Saeulen-kWh und das Fahrtende als Zeitpunkt', () => {
    const l = buildLatest(log, trip)!
    expect(l.charge).toEqual({ id: 'l1', at: '2026-10-01T18:00:00Z', kwh: 31.9, costEur: 12.4 })
    expect(l.trip).toEqual({ id: 't1', at: '2026-10-02T07:40:00Z', km: 23.4 })
  })

  it('faellt auf Saeulen-kWh zurueck, Preis darf fehlen', () => {
    const l = buildLatest({ ...log, kwhAtVehicle: null, costEur: null }, null)!
    expect(l.charge).toEqual({ id: 'l1', at: '2026-10-01T18:00:00Z', kwh: 34.2, costEur: null })
    expect(l.trip).toBeNull()
  })

  it('nimmt Ladeort, Ladeart und Streckentyp mit, wenn bekannt', () => {
    const l = buildLatest({ ...log, cpoName: 'Tesla', chargingType: 'AC' }, { ...trip, routeType: 'CITY' })!
    expect(l.charge).toMatchObject({ cpoName: 'Tesla', chargingType: 'AC' })
    expect(l.trip).toMatchObject({ routeType: 'CITY' })
  })

  it('ist null ohne beides', () => {
    expect(buildLatest(null, null)).toBeNull()
    expect(buildLatest(undefined, undefined)).toBeNull()
  })
})

describe('sohIsEstimated: Tilde am Akkuwert', () => {
  const e = (recordedAt: string, source: string) => ({ recordedAt, source })

  it('entscheidet nach dem jüngsten Eintrag, egal in welcher Reihenfolge', () => {
    expect(sohIsEstimated([e('2026-10-01', 'MANUAL'), e('2026-09-01', 'CHARGE_LOG')])).toBe(false)
    expect(sohIsEstimated([e('2026-09-01', 'MANUAL'), e('2026-10-01', 'CHARGE_LOG')])).toBe(true)
  })

  it('ohne Verlauf keine Tilde: der Wert kommt aus der eigenen Angabe am Auto', () => {
    expect(sohIsEstimated([])).toBe(false)
  })
})

describe('scatterYScale', () => {
  it('ohne Ausreißer: Achse ab 0 bis knapp über dem höchsten Wert, glatte Striche', () => {
    const s = scatterYScale([12, 15, 18, 20], 14.7)
    expect(s.max).toBe(25)
    expect(s.ticks).toEqual([0, 5, 10, 15, 20, 25])
    expect(s.above).toBe(0)
  })

  it('ein Ausreißer drückt die Achse nicht: er wird gezählt statt gezeichnet', () => {
    const normal = Array.from({ length: 20 }, (_, i) => 15 + (i % 10))
    const s = scatterYScale([...normal, 274], 14.7)
    expect(s.max).toBeLessThanOrEqual(50)
    expect(s.max).toBeGreaterThanOrEqual(24)
    expect(s.above).toBe(1)
    expect(s.aboveMax).toBe(274)
  })

  it('Herstellerwert über allen Fahrten bleibt im Bild', () => {
    const s = scatterYScale([10, 11, 12], 30)
    expect(s.max).toBeGreaterThanOrEqual(30)
    expect(s.above).toBe(0)
  })

  it('höchstens sieben Striche, auch bei großem Bereich', () => {
    const s = scatterYScale([20, 40, 60, 70], null)
    expect(s.ticks.length).toBeLessThanOrEqual(7)
    expect(s.ticks[0]).toBe(0)
    expect(s.ticks[s.ticks.length - 1]).toBe(s.max)
  })
})

describe('buildTripMonth: Fahrten des Monats für Kachel und Sheet', () => {
  const tr = (id: string, start: string, km: number, routeType: string | null, kwh: number | null = null, temp: number | null = null) => ({
    id, tripStartedAt: start, tripEndedAt: start, distanceKm: km, socStart: null, socEnd: null, estimatedConsumedKwh: kwh, outsideTempCelsius: temp, routeType,
  })

  it('zählt Fahrten und km, Anteile nach km je Streckentyp, längste Fahrt', () => {
    const m = buildTripMonth([
      tr('a', '2026-10-02T08:00:00Z', 70, 'CITY', 12, 8),
      tr('b', '2026-10-03T08:00:00Z', 20, 'HIGHWAY', 5, 12),
      tr('c', '2026-10-04T08:00:00Z', 10, 'COMBINED', 2),
      tr('d', '2026-10-05T08:00:00Z', 5, null, 1),
    ], 57, true)!
    expect(m.trips).toBe(4)
    expect(m.km).toBe(105)
    expect(m.longestKm).toBe(70)
    expect(m.routeShares).toEqual([{ key: 'CITY', pct: 70 }, { key: 'COMBINED', pct: 10 }, { key: 'HIGHWAY', pct: 20 }])
    expect(m.mainRoute).toBe('CITY')
    expect(m.driveKwhPer100km).toBeCloseTo(19, 0)
    expect(m.driveKwh).toBeCloseTo(20, 1)
    expect([m.tempMin, m.tempMax]).toEqual([8, 12])
  })

  it('ohne klaren Schwerpunkt kein Hauptstreckentyp, ohne Streckentypen keine Anteile', () => {
    expect(buildTripMonth([tr('a', '2026-10-02T08:00:00Z', 40, 'CITY'), tr('b', '2026-10-03T08:00:00Z', 60, 'HIGHWAY')], 57, true)!.mainRoute).toBe('HIGHWAY')
    expect(buildTripMonth([tr('a', '2026-10-02T08:00:00Z', 40, 'CITY'), tr('b', '2026-10-03T08:00:00Z', 35, 'HIGHWAY'), tr('c', '2026-10-03T09:00:00Z', 25, 'COMBINED')], 57, true)!.mainRoute).toBeNull()
    expect(buildTripMonth([tr('a', '2026-10-02T08:00:00Z', 40, null)], 57, true)!.routeShares).toEqual([])
    expect(buildTripMonth([tr('a', '2026-10-02T08:00:00Z', 40, null)], 57, true)!.driveKwh).toBeNull()
  })

  it('Standverlust nur sichtbar, wenn freigeschaltet', () => {
    const locked = buildTripMonth([tr('a', '2026-10-02T08:00:00Z', 40, 'CITY', 8)], 57, false)!
    expect(locked.standbyKwh).toBeNull()
    expect(locked.standbyLocked).toBe(true)
  })

  it('Standverluste: je Standzeit kWh, Beginn und Ende, größte zuerst, höchstens sechs, nur wenn freigeschaltet', () => {
    const e = (id: string, start: string, end: string, eStart: number, eEnd: number) => ({
      id, tripStartedAt: start, tripEndedAt: end, distanceKm: 10, socStart: null, socEnd: null, estimatedConsumedKwh: 2,
      energyRemainingStartKwh: eStart, energyRemainingEndKwh: eEnd, routeType: null,
    })
    const trips = [
      e('a', '2026-10-02T08:00:00Z', '2026-10-02T08:30:00Z', 60, 58),
      e('b', '2026-10-03T08:00:00Z', '2026-10-03T08:30:00Z', 57.2, 55), // 0,8 kWh über Nacht
      e('c', '2026-10-06T08:00:00Z', '2026-10-06T08:30:00Z', 52.5, 50), // 2,5 kWh übers Wochenende
      e('d', '2026-10-06T18:00:00Z', '2026-10-06T18:30:00Z', 70, 68),   // dazwischen geladen: kein Verlust
    ]
    const m = buildTripMonth(trips, 57, true)!
    expect(m.standbyGaps).toEqual([
      { kwh: 2.5, from: '2026-10-03T08:30:00Z', to: '2026-10-06T08:00:00Z', eur: null },
      { kwh: 0.8, from: '2026-10-02T08:30:00Z', to: '2026-10-03T08:00:00Z', eur: null },
    ])
    expect(buildTripMonth(trips, 57, false)!.standbyGaps).toEqual([])
  })

  it('Standverluste in Euro: Preis der letzten Ladung mit Preis davor, sonst Monatsschnitt, sonst keiner', () => {
    const e = (id: string, start: string, end: string, eStart: number, eEnd: number) => ({
      id, tripStartedAt: start, tripEndedAt: end, distanceKm: 10, socStart: null, socEnd: null, estimatedConsumedKwh: 2,
      energyRemainingStartKwh: eStart, energyRemainingEndKwh: eEnd, routeType: null,
    })
    const trips = [
      e('a', '2026-10-02T08:00:00Z', '2026-10-02T08:30:00Z', 60, 58),
      e('b', '2026-10-03T08:00:00Z', '2026-10-03T08:30:00Z', 57, 55),   // Stand davor: 1 kWh, letzte Ladung 01.10. zu 0,40 €/kWh
      e('c', '2026-10-06T08:00:00Z', '2026-10-06T08:30:00Z', 53, 50),   // Stand davor: 2 kWh, letzte Ladung 02.10. abends zu 0,60 €/kWh
    ]
    const charges = [
      { loggedAt: '2026-10-01T18:00:00Z', costEur: 8, kwhCharged: 20, kwhAtVehicle: null },
      { loggedAt: '2026-10-02T20:00:00Z', costEur: 3, kwhCharged: 5, kwhAtVehicle: null },
      { loggedAt: '2026-10-02T22:00:00Z', costEur: null, kwhCharged: 10, kwhAtVehicle: null }, // ohne Preis: zählt nicht
    ]
    const m = buildTripMonth(trips, 57, true, { charges, avgCostPerKwh: 0.3 })!
    expect(m.standbyGaps.map(g => g.eur)).toEqual([1.2, 0.4])
    expect(m.standbyEur).toBeCloseTo(1.6, 2)
    // ohne passende Ladung: Monatsschnitt
    expect(buildTripMonth(trips, 57, true, { charges: [], avgCostPerKwh: 0.3 })!.standbyGaps.map(g => g.eur)).toEqual([0.6, 0.3])
    // ohne jeden Preis: kein Euro-Betrag
    const none = buildTripMonth(trips, 57, true, { charges: [], avgCostPerKwh: null })!
    expect(none.standbyGaps.map(g => g.eur)).toEqual([null, null])
    expect(none.standbyEur).toBeNull()
  })

  it('Fahrtlängen: Anteil der km auf Fahrten unter 5 km, 5 bis 30 km, ab 30 km, dazu Ø km je Fahrt', () => {
    const m = buildTripMonth([
      tr('a', '2026-10-02T08:00:00Z', 2, null), tr('b', '2026-10-02T09:00:00Z', 3, null), tr('c', '2026-10-02T10:00:00Z', 4.9, null),
      tr('d', '2026-10-03T08:00:00Z', 12, null), tr('e', '2026-10-04T08:00:00Z', 30, null),
    ], 57, true)!
    // 51,9 km: 9,9 km kurz, 12 km mittel, 30 km lang. Wenige lange Fahrten tragen die meisten km
    expect(m.lengthShares).toEqual([{ key: 'SHORT', pct: 19 }, { key: 'MEDIUM', pct: 23 }, { key: 'LONG', pct: 58 }])
    expect(m.avgKm).toBeCloseTo(10.4, 1)
    expect(buildTripMonth([tr('a', '2026-10-02T08:00:00Z', 2, null)], 57, true)!.lengthShares).toEqual([{ key: 'SHORT', pct: 100 }])
  })

  it('ohne Fahrten oder ohne km nichts', () => {
    expect(buildTripMonth([], 57, true)).toBeNull()
    expect(buildTripMonth([tr('a', '2026-10-02T08:00:00Z', 0, 'CITY')], 57, true)).toBeNull()
  })
})
