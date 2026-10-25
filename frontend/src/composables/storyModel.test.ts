import { describe, it, expect } from 'vitest'
import {
  visibleGap,
  buildStoryTiles,
  buildStoryFeed,
  buildDayCoverage,
  petrolCostEur,
  heroLine,
  co2Comparison,
  monthWindow,
  type StoryInput, type StoryFeedItem, MIN_SOLID_CHARGES } from './storyModel'
import type { StatisticsData } from './useDashboardStats'

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
    energySplitLocked: true,
    flow: null,
    range: null,
    modelPeers: null,
    rank: null,
    quality: null,
    temperature: null,
    temperatureLocked: false,
    pricelessPrevious: 0,
    ...over,
  }
}

describe('buildStoryTiles', () => {
  it('liefert vier Kacheln, wenn alle Daten da sind, keine CO₂-Kachel', () => {
    const tiles = buildStoryTiles(input())
    expect(tiles.map(t => t.id)).toEqual(['cost', 'wltp', 'peer', 'battery'])
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
    expect(tiles.map(t => t.id)).toEqual(['cost'])
  })

  it('ohne Strecke gibt es weder WLTP noch CO2', () => {
    const tiles = buildStoryTiles(input({
      month: stats({ totalDistanceKm: null, avgConsumptionKwhPer100km: null }),
    }))
    expect(tiles.map(t => t.id)).toEqual(['cost', 'peer', 'battery'])
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

  it('sortiert nach Relevanz und begrenzt auf fünf', () => {
    const feed = buildStoryFeed(input())
    expect(feed.length).toBeLessThanOrEqual(5)
    for (let i = 1; i < feed.length; i++) {
      expect(feed[i - 1].relevance).toBeGreaterThanOrEqual(feed[i].relevance)
    }
  })

  it('enthält Verbrauchstrend, Ersparnis, teuerste Ladung und gesperrten Standverlust', () => {
    const ids = buildStoryFeed(input()).map(f => f.id)
    expect(ids).toContain('consumption_trend')
    expect(ids).toContain('home_savings')
    expect(ids).toContain('most_expensive')
    expect(ids).toContain('phantom_locked')
  })

  it('gesperrter Eintrag fehlt, wenn Energy-Split freigeschaltet ist', () => {
    const ids = buildStoryFeed(input({ energySplitLocked: false })).map(f => f.id)
    expect(ids).not.toContain('phantom_locked')
  })

  it('kein Trend-Satz, wenn sich fast nichts geändert hat', () => {
    const ids = buildStoryFeed(input({ previousMonth: stats({ totalCostEur: 128.9, avgConsumptionKwhPer100km: 17.95 }) })).map(f => f.id)
    expect(ids).not.toContain('cost_trend')
    expect(ids).not.toContain('consumption_trend')
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
  it('rechnet mit 7,5 l und Literpreis', () => {
    expect(petrolCostEur(1000, 1.75)).toBeCloseTo(131.25)
  })
  it('null ohne Strecke', () => {
    expect(petrolCostEur(null, 1.75)).toBeNull()
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
    const c = co2Comparison(2931, 17.9)
    expect(c.petrolKg).toBeCloseTo(521.7, 0)
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
const range = { consumption: 21, km: 229, windows: [{ socMax: 90, socMin: 10, recommended: true, km: 229 }], summer: null, winter: { consumption: 21, km: 229 } }

describe('buildStoryTiles v2: Energiefluss und Reichweite', () => {
  it('ordnet sechs Kacheln fest wie der Dummy: Kosten, Hersteller, Strom, Reichweite, andere, Akku', () => {
    const tiles = buildStoryTiles(input({ flow, range }))
    expect(tiles.map(t => t.id)).toEqual(['cost', 'wltp', 'flow', 'range', 'peer', 'battery'])
  })

  it('Vergleich mit anderen erst ab 5 Fahrern: Kachel und Preis-Satz entfallen darunter', () => {
    const few = stats({ peerBenchmark: { ...stats().peerBenchmark!, uniquePeerUsers: 4 } })
    expect(buildStoryTiles(input({ month: few })).map(t => t.id)).not.toContain('peer')
    expect(buildStoryFeed(input({ month: few })).map(f => f.id)).not.toContain('peer_position')
    const five = stats({ peerBenchmark: { ...stats().peerBenchmark!, uniquePeerUsers: 5 } })
    expect(buildStoryTiles(input({ month: five })).map(t => t.id)).toContain('peer')
  })

  it('Akku-Kachel trägt, ob der Wert geschätzt ist', () => {
    expect(buildStoryTiles(input({ sohEstimated: true })).find(t => t.id === 'battery')!.estimated).toBe(true)
    expect(buildStoryTiles(input({ sohEstimated: false })).find(t => t.id === 'battery')!.estimated).toBe(false)
  })
})

describe('buildStoryFeed: Testansicht ohne Grenze', () => {
  it('max hebt die fünf Plätze auf, der gesperrte Satz bleibt zuletzt', () => {
    const neg = (id: string, relevance: number) => ({ id: 'moment', question: 1, relevance, sentiment: 'warn', params: {}, moment: { key: id } }) as never
    const extra = [neg('a', 99), neg('b', 98), neg('c', 97), neg('d', 96), neg('e', 95), neg('f', 94)]
    const feed = buildStoryFeed(input({ energySplitLocked: true, savingsEur: 120 }), { extra, max: Infinity })
    expect(feed.length).toBeGreaterThan(7)
    expect(feed[feed.length - 1].locked).toBe(true)
  })
})

describe('buildStoryFeed: mindestens ein positiver Satz', () => {
  it('holt den besten positiven Satz in die fünf Plätze, wenn keiner darin ist', () => {
    const neg = (id: string, relevance: number) => ({ id: 'moment', question: 1, relevance, sentiment: 'warn', params: {}, moment: { key: id } }) as never
    const feed = buildStoryFeed(input({ energySplitLocked: false, savingsEur: 120 }), {
      extra: [neg('a', 99), neg('b', 98), neg('c', 97), neg('d', 96), neg('e', 95)],
    })
    expect(feed).toHaveLength(5)
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

describe('buildStoryFeed v2: neue Quellen konkurrieren um fuenf Plaetze', () => {
  const peers = (deltaPercent: number, reason: 'price' | 'consumption', cars = 50) => ({
    cars, deltaPercent, reason,
    theirs: { costPerKwh: 0.24, consumption: 17.9, costPer100km: 4.3 },
    mine: { costPerKwh: 0.31, consumption: 18.8, costPer100km: 5.83 },
    variants: [],
  })
  const modelItem = (modelPeers: ReturnType<typeof peers>) =>
    buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, modelPeers })).find(i => i.id === 'model_peers')

  it('Modellvergleich: alle Fahrer des Modells, Grund Preis oder Verbrauch, neutral wenn sie günstiger sind', () => {
    const cheaper = modelItem(peers(-26, 'price'))!
    expect(cheaper.question).toBe(3)
    expect(cheaper.sentiment).toBe('neutral')
    expect(cheaper.params).toMatchObject({ deltaPercent: -26, cars: 50, same: 0, reasonPrice: 1, theirsPrice: 0.24, minePrice: 0.31, theirsCons: 17.9, mineCons: 18.8 })
    expect(modelItem(peers(-26, 'consumption'))!.params.reasonPrice).toBe(0)
  })

  it('Modellvergleich: gut, wenn du günstiger fährst; unter 5 % Unterschied "so günstig wie"', () => {
    expect(modelItem(peers(12, 'price'))!.sentiment).toBe('good')
    const same = modelItem(peers(-4.9, 'price'))!
    expect(same.params.same).toBe(1)
    expect(same.sentiment).toBe('neutral')
  })

  it('Modellvergleich erst ab 5 Autos aller Varianten', () => {
    expect(modelItem(peers(-26, 'price', 4))).toBeUndefined()
    expect(modelItem(peers(-26, 'price', 5))).toBeDefined()
  })

  it('Community-Rang: je besser der Platz, desto relevanter', () => {
    const first = buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, rank: { category: 'MONTHLY_NIGHT_OWL', rank: 1, value: 9, unit: '' } })).find(i => i.id === 'community_rank')!
    const tenth = buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, rank: { category: 'MONTHLY_KWH', rank: 10, value: 300, unit: 'kWh' } })).find(i => i.id === 'community_rank')!
    expect(first.question).toBe(3)
    expect(first.relevance).toBeGreaterThan(tenth.relevance)
    expect(first.params.rank).toBe(1)
  })

  it('Datenqualitaet: nur bei warn oder bad, bad ist relevanter', () => {
    const ok = buildStoryFeed(input({ quality: { estimated: 0, total: 14, estimatedShare: 0, priceless: 0, pricelessOpen: 0, severity: 'ok' } }))
    expect(ok.find(i => i.id === 'data_quality')).toBeUndefined()
    const warn = buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, quality: { estimated: 4, total: 14, estimatedShare: 0.29, priceless: 0, pricelessOpen: 0, severity: 'warn' } })).find(i => i.id === 'data_quality')!
    const bad = buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, quality: { estimated: 8, total: 14, estimatedShare: 0.57, priceless: 2, pricelessOpen: 2, severity: 'bad' } })).find(i => i.id === 'data_quality')!
    expect(warn.question).toBe(1)
    expect(bad.relevance).toBeGreaterThan(warn.relevance)
    expect(bad.sentiment).toBe('bad')
  })

  it('Temperatur: Satz nur mit Delta, gesperrt nur wenn Phantom nicht schon sperrt', () => {
    const withDelta = buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, temperature: { points: [], bins: [], coldKwh100: 23, mildKwh100: 16, deltaPercent: 43.75 } })).find(i => i.id === 'temperature_effect')!
    expect(withDelta.question).toBe(2)
    expect(withDelta.params.deltaPercent).toBeCloseTo(43.75)
    const noDelta = buildStoryFeed(input({ temperature: { points: [], bins: [], coldKwh100: null, mildKwh100: null, deltaPercent: null } }))
    expect(noDelta.find(i => i.id === 'temperature_effect')).toBeUndefined()

    const locked = buildStoryFeed(input({ energySplitLocked: false, temperatureLocked: true }))
    expect(locked[locked.length - 1].id).toBe('temperature_locked')
    expect(locked[locked.length - 1].locked).toBe(true)
    const bothLocked = buildStoryFeed(input({ energySplitLocked: true, temperatureLocked: true }))
    expect(bothLocked.filter(i => i.locked)).toHaveLength(1)
    expect(bothLocked[bothLocked.length - 1].id).toBe('phantom_locked')
  })

  it('bleibt bei fuenf Saetzen, auch wenn alle Quellen liefern', () => {
    const all = buildStoryFeed(input({
      energySplitLocked: false,
      modelPeers: { cars: 40, deltaPercent: -18, reason: 'price', theirs: { costPerKwh: 0.24, consumption: 17.9, costPer100km: 4.3 }, mine: { costPerKwh: 0.31, consumption: 18.8, costPer100km: 5.83 }, variants: [] },
      rank: { category: 'MONTHLY_KWH', rank: 3, value: 300, unit: 'kWh' },
      quality: { estimated: 8, total: 14, estimatedShare: 0.57, priceless: 2, pricelessOpen: 2, severity: 'bad' },
      temperature: { points: [], bins: [], coldKwh100: 23, mildKwh100: 16, deltaPercent: 43.75 },
    }))
    expect(all).toHaveLength(5)
    expect(all.every(i => !i.locked)).toBe(true)
  })
})

describe('buildStoryFeed mit Momenten', () => {
  it('nimmt Momente nach Relevanz auf und lässt ausgelassene Einträge weg', () => {
    const extra: StoryFeedItem = {
      id: 'moment', question: 4, relevance: 99, sentiment: 'neutral', params: {},
      moment: { kind: 'battery', key: 'battery:s', at: 0, lifetimeMs: 1, actionable: false, dismissible: false, sohPercent: 92, estimated: true, previousSohPercent: null, previousAt: null },
    }
    const feed = buildStoryFeed(input({ rank: { category: 'MONTHLY_KWH', rank: 2, value: 1, unit: 'kWh' } }), { extra: [extra], omit: ['community_rank'] })
    expect(feed[0].id).toBe('moment')
    expect(feed.some(f => f.id === 'community_rank')).toBe(false)
  })
})

describe('visibleGap: welche Lücke der Satz nennt', () => {
  const days = (withData: number[], gap: [number, number] | null, today = 31) => Array.from({ length: 31 }, (_, i) => {
    const day = i + 1
    return { day, hasData: withData.includes(day), inLongestGap: !!gap && day >= gap[0] && day <= gap[1], future: day > today, charges: 0, kwh: 0, trips: 0, tripKm: 0 }
  })

  it('abgeschlossener Monat: jede Lücke ab 3 Tagen', () => {
    expect(visibleGap(days([1, 5], [2, 4]))).toEqual({ from: 2, to: 4 })
    expect(visibleGap(days([1, 4], [2, 3]))).toBeNull()
  })

  it('laufender Monat: erst über 7 Tage, Tage nach heute zählen nicht', () => {
    expect(visibleGap(days([1, 9], [2, 8], 14))).toBeNull()
    expect(visibleGap(days([1, 10], [2, 9], 14))).toEqual({ from: 2, to: 9 })
    expect(visibleGap(days([1], [2, 31], 5))).toBeNull()
  })
})

describe('Fahrten-Kachel und Abgleich mit den Ladungen', () => {
  const tripMonth = { trips: 14, km: 151, avgKm: 10.8, lengthShares: [], driveKwhPer100km: 18.2, routeShares: [{ key: 'CITY' as const, pct: 70 }, { key: 'HIGHWAY' as const, pct: 30 }], mainRoute: 'CITY' as const, longestKm: 42, tempMin: 6, tempMax: 14, standbyKwh: null, standbyLocked: true }

  it('steht nach "Stimmt, was der Hersteller verspricht?", Zahl ist der Antriebsverbrauch', () => {
    const tiles = buildStoryTiles(input({ flow, range, tripMonth }))
    expect(tiles.map(t => t.id)).toEqual(['cost', 'wltp', 'trips', 'flow', 'range', 'peer', 'battery'])
    const t = tiles.find(x => x.id === 'trips')!
    expect(t.value).toBe(18.2)
    expect(t.tripMonth).toBe(tripMonth)
  })

  it('ohne Antriebsverbrauch keine Kachel', () => {
    expect(buildStoryTiles(input({ tripMonth: { ...tripMonth, driveKwhPer100km: null } })).map(t => t.id)).not.toContain('trips')
  })

  it('Feed-Satz, wenn die Ladungen deutlich mehr zeigen als das Fahren', () => {
    const month = stats({ avgConsumptionKwhPer100km: 26.9 })
    const item = buildStoryFeed(input({ month, tripMonth })).find(f => f.id === 'drive_vs_charge')!
    expect(item.params).toEqual({ drive: 18.2, charged: 26.9 })
    expect(buildStoryFeed(input({ month: stats({ avgConsumptionKwhPer100km: 19.5 }), tripMonth })).map(f => f.id)).not.toContain('drive_vs_charge')
  })
})
