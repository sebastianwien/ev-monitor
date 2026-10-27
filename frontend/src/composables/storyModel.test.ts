import { describe, it, expect } from 'vitest'
import {
  buildStoryTiles,
  buildStoryFeed,
  buildDayCoverage,
  petrolCostEur,
  petrolLitersPer100km,
  heroLine,
  co2Comparison,
  monthWindow,
  type StoryInput, type StoryFeedItem, MIN_SOLID_CHARGES } from './storyModel'
import type { StatisticsData } from './useDashboardStats'
import type { TripMonth } from './storyInsights'

function stats(over: Partial<StatisticsData> = {}): StatisticsData {
  return {
    totalKwhCharged: 524,
    energyCostEur: 128.4,
    fixedCostEur: 0,
    fixedIncomeEur: 0,
    totalCostEur: 128.4,
    avgCostPerKwh: 0.245,
    cheapestChargeEur: 3.2,
    mostExpensiveChargeEur: 19.1,
    avgChargeDurationMinutes: 180,
    totalCharges: 14,
    totalDistanceKm: 2931,
    avgConsumptionKwhPer100km: 17.9,
    estimatedConsumptionCount: 0,
    summerConsumptionKwhPer100km: null,
    winterConsumptionKwhPer100km: null,
    chargesOverTime: [],
    chargingTypeSplit: null,
    locationSplit: { publicKwh: 94, privateKwh: 430, unknownKwh: 0 },
    peerBenchmark: {
      userPeriodConsumptionKwhPer100km: 17.9,
      peerAvgConsumptionKwhPer100km: 18.5,
      userPeriodCostPerKwh: 0.245,
      peerAvgCostPerKwh: 0.225,
      uniquePeerUsers: 312,
      peerTripCount: 900,
      peerLogCount: 4000,
      matchType: 'SPEC',
    },
    chargingEfficiencySplit: null,
    ...over,
  }
}

function input(over: Partial<StoryInput> = {}): StoryInput {
  return {
    month: stats(),
    previousMonth: stats({ energyCostEur: 148.2, totalCostEur: 148.2, avgConsumptionKwhPer100km: 18.7 }),
    monthlyCosts: [176, 139, 121, 164, 148, 128.4],
    monthlyConsumption: [21.4, 20.5, 18.2, 16.8, 17.5, 17.9],
    wltpConsumption: 16.1,
    sohPercent: 96,
    odometerKm: 41000,
    savingsEur: 212,
    amortisationYearsRemaining: 1.2,
    flow: null,
    range: null,
    modelPeers: null,
    rank: null,
    quality: null,
    temperature: null,
    pricelessPrevious: 0,
    monthOfYear: 5,
    elapsedDays: 31,
    recent: { distanceKm: 840, days: 28 },
    vehicleCategory: null,
    lifetimeSeries: [],
    ...over,
  }
}

describe('buildStoryTiles', () => {
  it('liefert die Kacheln, wenn alle Daten da sind, Kosten zuletzt', () => {
    const tiles = buildStoryTiles(input())
    expect(tiles.map(t => t.id)).toEqual(['co2', 'battery', 'peer', 'wltp', 'cost'])
  })

  it('CO₂-Kachel: gesparte kg gegen Benziner, dazu g/km für Benziner, Strommix und Ökostrom', () => {
    // 2.931 km, 17,9 kWh/100 km: Benzin 7,5 l x 2,37 kg = 521,0 kg, Strommix 524,6 kWh x 380 g = 199,4 kg
    const co2 = buildStoryTiles(input()).find(t => t.id === 'co2')!
    expect(co2.value).toBeCloseTo(321.6, 0)
    expect(co2.sentiment).toBe('good')
    expect(co2.co2!.petrolGPerKm).toBeCloseTo(177.75, 2)
    expect(co2.co2!.gridGPerKm).toBeCloseTo(68.0, 1)
    expect(co2.co2!.greenGPerKm).toBeCloseTo(5.4, 1)
  })

  it('CO₂-Kachel: Benziner der eigenen Fahrzeugklasse, Liter des Monats', () => {
    // Kleinwagen 5,8 l/100 km: 2.931 km x 5,8 l = 170 l, 5,8 x 2,37 kg = 137,5 g/km
    const co2 = buildStoryTiles(input({ vehicleCategory: 'CITY_CAR' })).find(t => t.id === 'co2')!
    expect(co2.co2!.petrolLitersPer100km).toBe(5.8)
    expect(co2.co2!.petrolLiters).toBeCloseTo(170.0, 1)
    expect(co2.co2!.petrolGPerKm).toBeCloseTo(137.46, 2)
    expect(co2.co2!.category).toBe('CITY_CAR')
  })

  it('CO₂-Kachel: CO₂ weniger seit der ersten Ladung, nur wenn es mehr als der Monat ist', () => {
    const series = [
      { timestamp: '2025-03-04T10:00:00Z', distanceKm: 5000, consumptionKwhPer100km: 18 },
      { timestamp: '2026-10-02T10:00:00Z', distanceKm: 2931, consumptionKwhPer100km: 17.9 },
    ]
    const co2 = buildStoryTiles(input({ lifetimeSeries: series })).find(t => t.id === 'co2')!
    // 5.000 km x (177,75 - 68,4) g + 2.931 km x (177,75 - 68,02) g
    expect(co2.co2!.lifetime!.kg).toBeCloseTo(868.3, 0)
    expect(co2.co2!.lifetime!.since).toBe(Date.parse('2025-03-04T10:00:00Z'))
    const onlyMonth = buildStoryTiles(input({ lifetimeSeries: series.slice(1) })).find(t => t.id === 'co2')!
    expect(onlyMonth.co2!.lifetime).toBeNull()
  })

  it('Lade-Kachel: Anteil Schnellladen an den kWh, Preis je kWh je Art', () => {
    const split = { acKwh: 300, dcKwh: 100, unknownKwh: 0, acEurPerKwh: 0.3, dcEurPerKwh: 0.61 }
    const c = buildStoryTiles(input({ month: stats({ chargingTypeSplit: split }) })).find(t => t.id === 'charging')!
    expect(c.value).toBe(25)
    expect(c.charging).toEqual(split)
    expect(buildStoryTiles(input({ month: stats({ chargingTypeSplit: { acKwh: 0, dcKwh: 0, unknownKwh: 40 } }) })).map(t => t.id)).not.toContain('charging')
  })

  it('Verlust im Stand: keine eigene Kachel, die Standzeiten hängen an der Strom-Kachel', () => {
    const tripMonth: TripMonth = { trips: 14, km: 151, avgKm: 10.8, lengthShares: [], driveKwhPer100km: 18.2, driveKwh: 27.5, routeShares: [], mainRoute: null, longestKm: 42, tempMin: 6, tempMax: 14,
      standbyKwh: 3.3, standbyLocked: false, standbyGaps: [{ kwh: 2.5, from: '2026-10-03T08:30:00Z', to: '2026-10-06T08:00:00Z', eur: 0.75 }, { kwh: 0.8, from: '2026-10-02T08:30:00Z', to: '2026-10-03T08:00:00Z', eur: 0.24 }], standbyEur: 0.99 }
    const tiles = buildStoryTiles(input({ tripMonth }))
    // Keine eigene Kachel: der Stand steht in "Wo bleibt mein Strom?", die Standzeiten fürs Sheet hängen an ihr
    expect(tiles.map(t => t.id)).not.toContain('standby')
    expect(tiles.find(t => t.id === 'flow')!.tripMonth!.standbyGaps).toHaveLength(2)
  })

  it('Kosten-Kachel: Wert, Delta zum Vormonat, Sparkline', () => {
    const cost = buildStoryTiles(input()).find(t => t.id === 'cost')!
    expect(cost.value).toBeCloseTo(128.4)
    expect(cost.delta).toBeCloseTo(-19.8)
    expect(cost.sentiment).toBe('good')
    expect(cost.spark).toEqual([176, 139, 121, 164, 148, 128.4])
  })

  it('Kosten-Kachel: Ladungen ohne Preis im Monat - kein Delta, stattdessen ihre Zahl', () => {
    const quality = { estimated: 0, total: 14, estimatedShare: 0, priceless: 2, pricelessOpen: 2, severity: 'warn' as const }
    const cost = buildStoryTiles(input({ quality })).find(t => t.id === 'cost')!
    expect(cost.delta).toBeNull()
    expect(cost.pricelessCharges).toBe(2)
    expect(cost.sentiment).toBe('warn')
  })

  it('Kosten-Kachel: Vormonat mit Ladungen ohne Preis - kein Delta, kein Vergleich', () => {
    const cost = buildStoryTiles(input({ pricelessPrevious: 1 })).find(t => t.id === 'cost')!
    expect(cost.delta).toBeNull()
    expect(cost.previousIncomplete).toBe(true)
    expect(cost.sentiment).toBe('neutral')
  })

  it('WLTP-Kachel: Abweichung in Prozent, Warnung über 5 Prozent', () => {
    const wltp = buildStoryTiles(input()).find(t => t.id === 'wltp')!
    expect(wltp.value).toBeCloseTo(17.9)
    expect(wltp.deltaPercent).toBeCloseTo(11.2, 0)
    expect(wltp.sentiment).toBe('warn')
  })

  it('Peer-Kachel: Prozent besser als Durchschnitt, Fahrerzahl', () => {
    const peer = buildStoryTiles(input()).find(t => t.id === 'peer')!
    expect(peer.deltaPercent).toBeCloseTo(-3.2, 0)
    expect(peer.sentiment).toBe('good')
    expect(peer.peerCount).toBe(312)
  })

  it('markiert Monatskacheln mit weniger als drei Ladungen als duenne Basis', () => {
    const thin = buildStoryTiles(input({ month: stats({ totalCharges: 2 }) }))
    expect(thin.find(t => t.id === 'cost')!.thinCharges).toBe(2)
    expect(thin.find(t => t.id === 'wltp')!.thinCharges).toBe(2)
    expect(thin.find(t => t.id === 'peer')!.thinCharges).toBe(2)
    expect(thin.find(t => t.id === 'range')?.thinCharges).toBeUndefined()
    const solid = buildStoryTiles(input({ month: stats({ totalCharges: 3 }) }))
    expect(solid.find(t => t.id === 'cost')!.thinCharges).toBeUndefined()
    expect(MIN_SOLID_CHARGES).toBe(3)
  })

  it('ohne WLTP, Peer und SoH fehlen die Kacheln, kein Platzhalter', () => {
    const tiles = buildStoryTiles(input({
      wltpConsumption: null,
      sohPercent: null,
      month: stats({ peerBenchmark: null }),
    }))
    // CO₂ braucht nur Strecke und Verbrauch
    expect(tiles.map(t => t.id)).toEqual(['co2', 'cost'])
  })

  it('ohne Strecke gibt es weder WLTP noch CO2', () => {
    const tiles = buildStoryTiles(input({
      month: stats({ totalDistanceKm: null, avgConsumptionKwhPer100km: null }),
    }))
    expect(tiles.map(t => t.id)).toEqual(['battery', 'peer', 'cost'])
  })

  it('Kosten-Kachel vergleicht Stromkosten, einmalige Fixkosten verzerren das Delta nicht', () => {
    const month = stats({ energyCostEur: 55, fixedCostEur: 0, totalCostEur: 55 })
    const previousMonth = stats({ energyCostEur: 126, fixedCostEur: 2001, totalCostEur: 2127 })
    const cost = buildStoryTiles(input({ month, previousMonth })).find(t => t.id === 'cost')!
    expect(cost.value).toBe(55)
    expect(cost.delta).toBe(-71)
    const feed = buildStoryFeed(input({ month, previousMonth })).find(f => f.id === 'cost_trend')!
    expect(feed.params.diff).toBe(-71)
  })

  it('ohne Vormonat hat die Kosten-Kachel kein Delta', () => {
    const cost = buildStoryTiles(input({ previousMonth: null })).find(t => t.id === 'cost')!
    expect(cost.delta).toBeNull()
    expect(cost.sentiment).toBe('neutral')
  })
})

describe('Kosten im laufenden Monat', () => {
  // Oktober bis heute: 200 kWh für 60 € (30 ct/kWh). September: 400 kWh für 100 €.
  const month = stats({ totalKwhCharged: 200, energyCostEur: 60, totalCostEur: 60 })
  const previousMonth = stats({ totalKwhCharged: 400, energyCostEur: 100, totalCostEur: 100 })
  const cost = (over: Partial<StoryInput>) => buildStoryTiles(input({ month, previousMonth, ...over })).find(t => t.id === 'cost')!

  it('weniger Strom als im Vormonat: Prognose bei gleicher Menge statt Delta, kein Kostentrend', () => {
    const c = cost({ runningMonth: true })
    expect(c.delta).toBeNull()
    expect(c.projection).toEqual({ eur: 120, previousEur: 100 })
    expect(c.sentiment).toBe('neutral')
    expect(buildStoryFeed(input({ month, previousMonth, runningMonth: true })).map(f => f.id)).not.toContain('cost_trend')
  })

  it('Strommenge des Vormonats erreicht: echtes Delta und Kostentrend', () => {
    const full = stats({ totalKwhCharged: 400, energyCostEur: 92, totalCostEur: 92 })
    const c = cost({ month: full, runningMonth: true })
    expect(c.delta).toBe(-8)
    expect(c.projection).toBeUndefined()
    expect(buildStoryFeed(input({ month: full, previousMonth, runningMonth: true })).map(f => f.id)).toContain('cost_trend')
  })

  it('abgeschlossener Monat vergleicht immer, auch mit weniger Strom', () => {
    expect(cost({}).delta).toBe(-40)
  })

  it('ohne Preise im Monat keine Prognose, nur ihre Zahl', () => {
    const quality = { estimated: 0, total: 6, estimatedShare: 0, priceless: 1, pricelessOpen: 1, severity: 'warn' as const }
    const c = cost({ runningMonth: true, quality })
    expect(c.projection).toBeUndefined()
    expect(c.pricelessCharges).toBe(1)
  })
})

describe('Strecke gegen den Vormonat', () => {
  const previousMonth = stats({ totalDistanceKm: 512 })
  const distance = (km: number | null, over: Partial<StoryInput> = {}) =>
    buildStoryFeed(input({ month: stats({ totalDistanceKm: km }), previousMonth, ...over })).find(f => f.id === 'distance_trend')

  it('mehr gefahren: sofort, auch im laufenden Monat', () => {
    const item = distance(600, { runningMonth: true })!
    expect(item.params).toEqual({ diff: 88, current: 600, previous: 512 })
    expect(item.sentiment).toBe('neutral')
    expect(item.question).toBe(1)
  })

  it('weniger gefahren: erst wenn der Monat vorbei ist, vorher hiesse das nur weniger Tage', () => {
    expect(distance(274, { runningMonth: true })).toBeUndefined()
    expect(distance(430)!.params.diff).toBe(-82)
  })

  it('unter 10 % Unterschied oder ohne Strecke kein Satz', () => {
    expect(distance(540)).toBeUndefined()
    expect(distance(null)).toBeUndefined()
    expect(buildStoryFeed(input({ month: stats({ totalDistanceKm: 600 }), previousMonth: stats({ totalDistanceKm: null }) })).map(f => f.id)).not.toContain('distance_trend')
  })
})

describe('buildStoryFeed', () => {
  it('kein Kostentrend, wenn einem der beiden Monate Preise fehlen: 0 € heißt dort ohne Preis', () => {
    const month = stats({ energyCostEur: 55, totalCostEur: 55 })
    const previousMonth = stats({ energyCostEur: 126, totalCostEur: 126 })
    const quality = { estimated: 0, total: 14, estimatedShare: 0, priceless: 2, pricelessOpen: 2, severity: 'warn' as const }
    expect(buildStoryFeed(input({ month, previousMonth })).map(f => f.id)).toContain('cost_trend')
    expect(buildStoryFeed(input({ month, previousMonth, quality })).map(f => f.id)).not.toContain('cost_trend')
    expect(buildStoryFeed(input({ month, previousMonth, pricelessPrevious: 1 })).map(f => f.id)).not.toContain('cost_trend')
  })

  it('sortiert nach Relevanz und begrenzt auf drei', () => {
    const feed = buildStoryFeed(input())
    expect(feed.length).toBeLessThanOrEqual(3)
    for (let i = 1; i < feed.length; i++) {
      expect(feed[i - 1].relevance).toBeGreaterThanOrEqual(feed[i].relevance)
    }
  })

  it('enthält Ersparnis und teuerste Ladung, keine gesperrten Sätze und keinen Verbrauchstrend', () => {
    const feed = buildStoryFeed(input())
    const ids = feed.map(f => f.id)
    expect(ids).toContain('home_savings')
    expect(ids).toContain('most_expensive')
    expect(feed.every(f => !f.locked)).toBe(true)
    expect(ids).not.toContain('consumption_trend')
  })

  it('kein Trend-Satz, wenn sich fast nichts geändert hat', () => {
    const ids = buildStoryFeed(input({ previousMonth: stats({ totalCostEur: 128.9, avgConsumptionKwhPer100km: 17.95 }) })).map(f => f.id)
    expect(ids).not.toContain('cost_trend')
  })

  it('Kosten-Kachel ist neutral bei Delta unter einem Euro', () => {
    const cost = buildStoryTiles(input({ previousMonth: stats({ energyCostEur: 128.9, totalCostEur: 128.9 }) })).find(t => t.id === 'cost')!
    expect(cost.sentiment).toBe('neutral')
  })

  it('Ersparnis fehlt ohne Savings-Daten', () => {
    const ids = buildStoryFeed(input({ savingsEur: null })).map(f => f.id)
    expect(ids).not.toContain('home_savings')
  })

  it('teuerste Ladung nur, wenn sie deutlich über dem Schnitt liegt', () => {
    const ids = buildStoryFeed(input({ month: stats({ mostExpensiveChargeEur: 9.5 }) })).map(f => f.id)
    expect(ids).not.toContain('most_expensive')
  })
})

describe('buildDayCoverage', () => {
  it('trägt je Tag Ladungen und kWh für die Tagesanzeige', () => {
    const points = [
      { timestamp: '2026-09-02T10:00:00', costEur: 5, kwhCharged: 31.9, distanceKm: 100, consumptionKwhPer100km: 18, charges: 2 },
      { timestamp: '2026-09-05T10:00:00', costEur: 5, kwhCharged: 12, distanceKm: 100, consumptionKwhPer100km: 18 },
    ]
    const days = buildDayCoverage(points, 2026, 9)
    expect(days[1]).toMatchObject({ day: 2, charges: 2, kwh: 31.9 })
    expect(days[4]).toMatchObject({ day: 5, charges: 1, kwh: 12 })
    expect(days[0]).toMatchObject({ day: 1, charges: 0, kwh: 0 })
  })

  it('Fahrten zählen als erfasst: km und Zahl je Tag, keine Lücke an Fahrtagen', () => {
    const points = [{ timestamp: '2026-09-01T10:00:00', costEur: 5, kwhCharged: 20, distanceKm: 100, consumptionKwhPer100km: 18 }]
    const trips = [
      { tripStartedAt: '2026-09-03T07:00:00', distanceKm: 12.4 },
      { tripStartedAt: '2026-09-03T17:00:00', distanceKm: 30 },
      { tripStartedAt: '2026-09-04T07:00:00', distanceKm: null },
    ]
    const days = buildDayCoverage(points, 2026, 9, new Date(2026, 9, 1).getTime(), trips)
    expect(days[2]).toMatchObject({ day: 3, hasData: true, trips: 2, tripKm: 42.4, charges: 0 })
    expect(days[3]).toMatchObject({ day: 4, hasData: true, trips: 1, tripKm: 0 })
    expect(days[1]).toMatchObject({ day: 2, hasData: false, trips: 0, tripKm: 0 })
  })

  it('markiert Tage ohne Daten als Lücke', () => {
    const points = [
      { timestamp: '2026-09-01T10:00:00', costEur: 5, kwhCharged: 20, distanceKm: 100, consumptionKwhPer100km: 18 },
      { timestamp: '2026-09-03T10:00:00', costEur: 5, kwhCharged: 20, distanceKm: 100, consumptionKwhPer100km: 18 },
    ]
    const days = buildDayCoverage(points, 2026, 9)
    expect(days).toHaveLength(30)
    expect(days[0].hasData).toBe(true)
    expect(days[1].hasData).toBe(false)
    expect(days[2].hasData).toBe(true)
  })

  it('findet die längste Lücke', () => {
    const points = [1, 2, 3, 4, 5, 6, 7, 8, 9, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30].map(d => ({
      timestamp: `2026-09-${String(d).padStart(2, '0')}T08:00:00`, costEur: 1, kwhCharged: 1, distanceKm: 1, consumptionKwhPer100km: 1,
    }))
    const days = buildDayCoverage(points, 2026, 9)
    const gap = days.filter(d => d.inLongestGap)
    expect(gap[0].day).toBe(10)
    expect(gap[gap.length - 1].day).toBe(17)
  })

  it('zählt Tage nach heute weder als Lücke noch als fehlend', () => {
    const points = [1, 3, 6, 9, 12].map(d => ({
      timestamp: `2026-10-${String(d).padStart(2, '0')}T08:00:00`, costEur: 1, kwhCharged: 1, distanceKm: 1, consumptionKwhPer100km: 1,
    }))
    const days = buildDayCoverage(points, 2026, 10, new Date(2026, 9, 14, 12).getTime())
    expect(days).toHaveLength(31)
    expect(days.filter(d => d.future).map(d => d.day)).toEqual(Array.from({ length: 17 }, (_, i) => 15 + i))
    expect(days.filter(d => d.inLongestGap).every(d => d.day <= 14)).toBe(true)
  })

  it('kennt in vergangenen Monaten keine Zukunft', () => {
    const days = buildDayCoverage([], 2026, 9, new Date(2026, 9, 14, 12).getTime())
    expect(days.some(d => d.future)).toBe(false)
    expect(days.filter(d => d.inLongestGap)).toHaveLength(30)
  })
})

describe('petrolCostEur', () => {
  it('rechnet mit Litern je 100 km und Literpreis', () => {
    expect(petrolCostEur(1000, 1.75, 7.5)).toBeCloseTo(131.25)
    expect(petrolCostEur(1000, 1.75, 5.8)).toBeCloseTo(101.5)
  })
  it('null ohne Strecke', () => {
    expect(petrolCostEur(null, 1.75, 7.5)).toBeNull()
  })
})

describe('petrolLitersPer100km', () => {
  it('Benziner der Fahrzeugklasse, ohne Klasse Mittelwert 7,5 l', () => {
    expect(petrolLitersPer100km('CITY_CAR')).toBe(5.8)
    expect(petrolLitersPer100km('LARGE_SUV')).toBe(10.5)
    expect(petrolLitersPer100km(null)).toBe(7.5)
  })
})

describe('heroLine', () => {
  it('Strecke und Stromkosten: km für Euro', () => {
    expect(heroLine({ distanceKm: 274, energyCostEur: 33.4, charges: 6 })).toEqual({ kind: 'km_cost', km: 274, eur: 33.4, charges: 6 })
  })
  it('ohne Strecke: Euro für Ladungen', () => {
    expect(heroLine({ distanceKm: null, energyCostEur: 33.4, charges: 6 })).toEqual({ kind: 'cost_charges', km: null, eur: 33.4, charges: 6 })
    expect(heroLine({ distanceKm: 0, energyCostEur: 33.4, charges: 6 }).kind).toBe('cost_charges')
  })
  it('ohne Kosten: km und Ladungen, ohne beides nur Ladungen', () => {
    expect(heroLine({ distanceKm: 274, energyCostEur: 0, charges: 6 })).toEqual({ kind: 'km_charges', km: 274, eur: null, charges: 6 })
    expect(heroLine({ distanceKm: 0, energyCostEur: null, charges: 2 })).toEqual({ kind: 'charges', km: null, eur: null, charges: 2 })
  })
})

describe('co2Comparison', () => {
  it('liefert Benziner, Strommix, Ökostrom in kg', () => {
    const c = co2Comparison(2931, 17.9, 7.5)
    expect(c.petrolKg).toBeCloseTo(521.0, 0)
    expect(c.gridKg).toBeCloseTo(199.4, 0)
    expect(c.greenKg).toBeCloseTo(15.7, 0)
  })
})

describe('monthWindow', () => {
  it('liefert Start und Ende eines Monats als ISO-Datum', () => {
    expect(monthWindow(2026, 9)).toEqual({ start: '2026-09-01', end: '2026-09-30' })
    expect(monthWindow(2026, 2)).toEqual({ start: '2026-02-01', end: '2026-02-28' })
  })
  it('zählt Monate zurück über den Jahreswechsel', () => {
    expect(monthWindow(2026, 1, -1)).toEqual({ start: '2025-12-01', end: '2025-12-31' })
  })
})

const flow = { gridKwh: 524, vehicleKwh: 472, lossKwh: 52, lossPercent: 10, acKwh: 430, dcKwh: 94, privateKwh: 430, publicKwh: 94, distanceKm: 2931, coveredLogCount: 12, totalLogCount: 14, partial: true }
const range = { consumption: 21, km: 229, windows: [{ socMax: 90, socMin: 10, recommended: true, km: 229 }], summer: null, winter: { consumption: 21, km: 229 }, peerWinter: null, emptyKm: 206 }

describe('buildStoryTiles v2: Energiefluss und Reichweite', () => {
  it('ordnet die Kacheln fest: Reichweite zuerst, Kosten zuletzt', () => {
    const tiles = buildStoryTiles(input({ flow, range }))
    expect(tiles.map(t => t.id)).toEqual(['range', 'co2', 'battery', 'flow', 'peer', 'wltp', 'cost'])
  })

  it('Vergleich mit anderen erst ab 5 Fahrern: die Kachel entfällt darunter', () => {
    const few = stats({ peerBenchmark: { ...stats().peerBenchmark!, uniquePeerUsers: 4 } })
    expect(buildStoryTiles(input({ month: few })).map(t => t.id)).not.toContain('peer')
    const five = stats({ peerBenchmark: { ...stats().peerBenchmark!, uniquePeerUsers: 5 } })
    expect(buildStoryTiles(input({ month: five })).map(t => t.id)).toContain('peer')
  })

  it('Akku-Kachel trägt, ob der Wert geschätzt ist', () => {
    expect(buildStoryTiles(input({ sohEstimated: true })).find(t => t.id === 'battery')!.estimated).toBe(true)
    expect(buildStoryTiles(input({ sohEstimated: false })).find(t => t.id === 'battery')!.estimated).toBe(false)
  })
})

describe('buildStoryFeed: Testansicht ohne Grenze', () => {
  it('max hebt die drei Plätze auf', () => {
    const neg = (id: string, relevance: number) => ({ id: 'moment', question: 1, relevance, sentiment: 'warn', params: {}, moment: { key: id } }) as never
    const extra = [neg('a', 99), neg('b', 98), neg('c', 97), neg('d', 96), neg('e', 95), neg('f', 94)]
    const feed = buildStoryFeed(input({ savingsEur: 120 }), { extra, max: Infinity })
    expect(feed.length).toBeGreaterThan(6)
  })
})

describe('buildStoryFeed: mindestens ein positiver Satz', () => {
  it('holt den besten positiven Satz in die drei Plätze, wenn keiner darin ist', () => {
    const neg = (id: string, relevance: number) => ({ id: 'moment', question: 1, relevance, sentiment: 'warn', params: {}, moment: { key: id } }) as never
    const feed = buildStoryFeed(input({ savingsEur: 120 }), {
      extra: [neg('a', 99), neg('b', 98), neg('c', 97)],
    })
    expect(feed).toHaveLength(3)
    expect(feed.some(f => f.sentiment === 'good')).toBe(true)
    expect(feed[feed.length - 1].sentiment).toBe('good')
  })

  it('Fluss-Kachel: Verlust in Prozent, Stimmung nach Schwelle, Rohdaten fuer die Grafik', () => {
    const t = buildStoryTiles(input({ flow })).find(x => x.id === 'flow')!
    expect(t.value).toBe(10)
    expect(t.sentiment).toBe('good')
    expect(t.flow).toBe(flow)
    expect(buildStoryTiles(input({ flow: { ...flow, lossPercent: 16 } })).find(x => x.id === 'flow')!.sentiment).toBe('warn')
    expect(buildStoryTiles(input({ flow: { ...flow, lossPercent: 25 } })).find(x => x.id === 'flow')!.sentiment).toBe('bad')
  })

  it('Reichweiten-Kachel: Kilometer im empfohlenen Fenster, neutral', () => {
    const t = buildStoryTiles(input({ range })).find(x => x.id === 'range')!
    expect(t.value).toBe(229)
    expect(t.sentiment).toBe('neutral')
    expect(t.range).toBe(range)
  })
})

describe('buildStoryFeed v2: Insights mit Vergleichsbasis, drei Plaetze', () => {
  const peers = (costPerKwh = 0.36, cars = 50) => ({
    cars, deltaPercent: -20, reason: 'price' as const,
    theirs: { costPerKwh, consumption: 17.9, costPer100km: costPerKwh * 17.9 },
    mine: { costPerKwh: 0.245, consumption: 17.9, costPer100km: 4.39 },
    variants: [],
  })
  const ok = { estimated: 0, total: 14, estimatedShare: 0, priceless: 0, pricelessOpen: 0, severity: 'ok' as const }

  it('Preisvergleich in Euro: Modellfahrer-Preis mal eigene kWh, gut wenn du weniger zahlst', () => {
    const item = buildStoryFeed(input({ savingsEur: null, modelPeers: peers(), quality: ok })).find(i => i.id === 'peer_cost_eur')!
    expect(item.question).toBe(3)
    expect(item.sentiment).toBe('good')
    expect(item.params.theirsEur).toBeCloseTo(524 * 0.36)
    expect(item.params.diffEur).toBeCloseTo(128.4 - 524 * 0.36)
    expect(item.params.cars).toBe(50)
    const pricier = buildStoryFeed(input({ savingsEur: null, modelPeers: peers(0.2), quality: ok })).find(i => i.id === 'peer_cost_eur')!
    expect(pricier.sentiment).toBe('neutral')
    expect(pricier.params.diffEur).toBeGreaterThan(0)
  })

  it('Preisvergleich erst ab 5 Autos, 3 Ladungen, 14 Tagen und nur ohne preislose Ladungen', () => {
    const find = (over: Partial<StoryInput>) => buildStoryFeed(input({ savingsEur: null, modelPeers: peers(), quality: ok, ...over })).find(i => i.id === 'peer_cost_eur')
    expect(find({ modelPeers: peers(0.36, 4) })).toBeUndefined()
    expect(find({ month: stats({ totalCharges: 2 }) })).toBeUndefined()
    expect(find({ elapsedDays: 13 })).toBeUndefined()
    expect(find({ quality: { ...ok, priceless: 1, severity: 'warn' } })).toBeUndefined()
    expect(find({ month: stats({ energyCostEur: 524 * 0.36 + 0.5 }) })).toBeUndefined()
    expect(find({})).toBeDefined()
  })

  it('Herbst-Prognose im September und Oktober des laufenden Monats: km der letzten Tage mal Winter-Mehrverbrauch mal Preis', () => {
    const range = { consumption: 17.9, windows: [], km: 300, summer: { consumption: 16, km: 320 }, winter: { consumption: 21, km: 250 }, peerWinter: null, emptyKm: 350 }
    const base = { savingsEur: null, quality: ok, range, monthOfYear: 10, runningMonth: true, recent: { distanceKm: 840, days: 28 } }
    const item = buildStoryFeed(input(base)).find(i => i.id === 'autumn_forecast')!
    // 900 km in 30 Tagen x 5 kWh/100 km x 0,245 € = 11,03 €
    expect(item.params.deltaEur).toBeCloseTo(900 * 5 / 100 * 0.245)
    expect(item.params.nextEur).toBeCloseTo(128.4 + item.params.deltaEur)
    expect(item.params.nextMonth).toBe(11)
    expect(item.params.peerWinter).toBe(0)
    expect(item.sentiment).toBe('neutral')
    const find = (over: Partial<StoryInput>) => buildStoryFeed(input({ ...base, ...over })).find(i => i.id === 'autumn_forecast')
    expect(find({ monthOfYear: 9 })).toBeDefined()
    expect(find({ monthOfYear: 11 })).toBeUndefined()
    expect(find({ runningMonth: false })).toBeUndefined()
    expect(find({ range: { ...range, summer: null } })).toBeUndefined()
    expect(find({ range: { ...range, winter: null, peerWinter: { consumption: 20, km: 260 } } })!.params.peerWinter).toBe(1)
    expect(find({ range: { ...range, winter: null } })).toBeUndefined()
    expect(find({ recent: { distanceKm: 100, days: 28 } })).toBeUndefined()
    expect(find({ recent: null })).toBeUndefined()
  })

  it('Datenqualitaet: nur bei warn oder bad, bad ist relevanter', () => {
    const ok = buildStoryFeed(input({ quality: { estimated: 0, total: 14, estimatedShare: 0, priceless: 0, pricelessOpen: 0, severity: 'ok' } }))
    expect(ok.find(i => i.id === 'data_quality')).toBeUndefined()
    const warn = buildStoryFeed(input({ savingsEur: null, quality: { estimated: 4, total: 14, estimatedShare: 0.29, priceless: 0, pricelessOpen: 0, severity: 'warn' } })).find(i => i.id === 'data_quality')!
    const bad = buildStoryFeed(input({ savingsEur: null, quality: { estimated: 8, total: 14, estimatedShare: 0.57, priceless: 2, pricelessOpen: 2, severity: 'bad' } })).find(i => i.id === 'data_quality')!
    expect(warn.question).toBe(1)
    expect(bad.relevance).toBeGreaterThan(warn.relevance)
    expect(bad.sentiment).toBe('bad')
  })

  it('Temperatur: Satz nur mit Delta', () => {
    const withDelta = buildStoryFeed(input({ savingsEur: null, temperature: { points: [], bins: [], coldKwh100: 23, mildKwh100: 16, deltaPercent: 43.75 } })).find(i => i.id === 'temperature_effect')!
    expect(withDelta.question).toBe(2)
    expect(withDelta.params.deltaPercent).toBeCloseTo(43.75)
    const noDelta = buildStoryFeed(input({ temperature: { points: [], bins: [], coldKwh100: null, mildKwh100: null, deltaPercent: null } }))
    expect(noDelta.find(i => i.id === 'temperature_effect')).toBeUndefined()
  })

  it('bleibt bei drei Saetzen, auch wenn alle Quellen liefern', () => {
    const all = buildStoryFeed(input({
      modelPeers: peers(),
      quality: { estimated: 8, total: 14, estimatedShare: 0.57, priceless: 0, pricelessOpen: 0, severity: 'bad' },
      temperature: { points: [], bins: [], coldKwh100: 23, mildKwh100: 16, deltaPercent: 43.75 },
    }))
    expect(all).toHaveLength(3)
  })
})

describe('buildStoryFeed mit Momenten', () => {
  it('nimmt Momente nach Relevanz auf und lässt ausgelassene Einträge weg', () => {
    const extra: StoryFeedItem = {
      id: 'moment', question: 4, relevance: 99, sentiment: 'neutral', params: {},
      moment: { kind: 'battery', key: 'battery:s', at: 0, lifetimeMs: 1, actionable: false, dismissible: false, sohPercent: 92, estimated: true, previousSohPercent: null, previousAt: null },
    }
    const feed = buildStoryFeed(input(), { extra: [extra], omit: ['home_savings'] })
    expect(feed[0].id).toBe('moment')
    expect(feed.some(f => f.id === 'home_savings')).toBe(false)
  })
})

describe('Fahrten-Kachel und Abgleich mit den Ladungen', () => {
  const tripMonth = { trips: 14, km: 151, avgKm: 10.8, lengthShares: [], driveKwhPer100km: 18.2, driveKwh: 27.5, routeShares: [{ key: 'CITY' as const, pct: 70 }, { key: 'HIGHWAY' as const, pct: 30 }], mainRoute: 'CITY' as const, longestKm: 42, tempMin: 6, tempMax: 14, standbyKwh: null, standbyLocked: true, standbyGaps: [], standbyEur: null }

  it('steht nach der Reichweite, Zahl ist der Antriebsverbrauch', () => {
    const tiles = buildStoryTiles(input({ flow, range, tripMonth }))
    expect(tiles.map(t => t.id)).toEqual(['range', 'trips', 'co2', 'battery', 'flow', 'peer', 'wltp', 'cost'])
    const t = tiles.find(x => x.id === 'trips')!
    expect(t.value).toBe(18.2)
    expect(t.tripMonth).toBe(tripMonth)
  })

  describe('Strom-Kachel mit Fahrten: Fahren, Stand, Laden', () => {
    const month = stats({ avgCostPerKwh: 0.29, chargingEfficiencySplit: { gridKwh: 20, vehicleKwh: 17, coveredLogCount: 1, totalLogCount: 2, lossKwh: 3, estimatedLossLogCount: 1 } })
    const tm = { ...tripMonth, driveKwh: 33.5, standbyKwh: 34, standbyLocked: false, standbyGaps: [{ kwh: 34, from: '', to: '', eur: 9.86 }], standbyEur: 9.86 }
    const tile = (over: Partial<StoryInput>) => buildStoryTiles(input({ month, flow, tripMonth: tm, ...over })).find(x => x.id === 'flow')

    it('Zahl ist der Verlust in Euro, Stimmung neutral, Netz und Akku bleiben fürs Sheet', () => {
      const t = tile({})!
      expect(t.value).toBeCloseTo(10.73)
      expect(t.sentiment).toBe('neutral')
      expect(t.use!.shares.map(s => s.key)).toEqual(['drive', 'standby', 'charge'])
      expect(t.flow).toBe(flow)
    })

    it('ohne Preis ist die Zahl der Verlust in kWh', () => {
      const t = tile({ month: stats({ ...month, avgCostPerKwh: 0 }), tripMonth: { ...tm, standbyEur: null } })!
      expect(t.value).toBeCloseTo(37)
      expect(t.use!.lossEur).toBeNull()
    })

    it('auch ohne gemessene Ladung, dann ohne Netz und Akku', () => {
      const t = tile({ flow: null })!
      expect(t.use).toBeDefined()
      expect(t.flow).toBeUndefined()
    })

    it('ohne Energiedaten der Fahrten bleibt die Kachel beim Ladeverlust in Prozent', () => {
      const t = tile({ tripMonth: { ...tm, driveKwh: null } })!
      expect(t.value).toBe(flow.lossPercent)
      expect(t.use).toBeUndefined()
    })
  })

  it('ohne Antriebsverbrauch keine Kachel', () => {
    expect(buildStoryTiles(input({ tripMonth: { ...tripMonth, driveKwhPer100km: null } })).map(t => t.id)).not.toContain('trips')
  })
})
