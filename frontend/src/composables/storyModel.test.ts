import { describe, it, expect } from 'vitest'
import {
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
    whatIf: [],
    rank: null,
    quality: null,
    temperature: null,
    temperatureLocked: false,
    pricelessPrevious: 0,
    ...over,
  }
}

describe('buildStoryTiles', () => {
  it('liefert fünf Kacheln, wenn alle Daten da sind', () => {
    const tiles = buildStoryTiles(input())
    expect(tiles.map(t => t.id)).toEqual(['cost', 'wltp', 'peer', 'battery', 'co2'])
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

  it('CO2-Kachel: Ersparnis mit Strommix und mit Ökostrom', () => {
    const co2 = buildStoryTiles(input()).find(t => t.id === 'co2')!
    // Benziner 178 g/km * 2931 km = 521,7 kg; Strommix 17,9/100*380 = 68 g/km -> 199,4 kg
    expect(co2.value).toBeCloseTo(322, 0)
    expect(co2.valueGreen).toBeCloseTo(506, 0)
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
    expect(tiles.map(t => t.id)).toEqual(['cost', 'co2'])
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
  it('ordnet sieben Kacheln fest nach Fahrerfrage', () => {
    const tiles = buildStoryTiles(input({ flow, range }))
    expect(tiles.map(t => t.id)).toEqual(['cost', 'flow', 'range', 'wltp', 'peer', 'battery', 'co2'])
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
  it('Was-waere-wenn: guenstigstes Fremdmodell ab 5 Prozent Ersparnis, sonst Lob', () => {
    const cheaper = buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, whatIf: [
      { name: 'Modell B', costPer100km: 3.6, deltaPercent: -18, cars: 40, isUser: false },
      { name: 'Meins', costPer100km: 4.4, deltaPercent: 0, cars: 300, isUser: true },
    ] })).find(i => i.id === 'what_if_model')!
    expect(cheaper.question).toBe(3)
    expect(cheaper.sentiment).toBe('neutral')
    expect(cheaper.params.deltaPercent).toBe(-18)
    expect(cheaper.params.modelIndex).toBe(0)

    const best = buildStoryFeed(input({ energySplitLocked: false, savingsEur: null, whatIf: [
      { name: 'Meins', costPer100km: 4.4, deltaPercent: 0, cars: 300, isUser: true },
      { name: 'Modell C', costPer100km: 4.5, deltaPercent: 2, cars: 20, isUser: false },
    ] })).find(i => i.id === 'what_if_model')!
    expect(best.sentiment).toBe('good')
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
      whatIf: [{ name: 'B', costPer100km: 3.6, deltaPercent: -18, cars: 40, isUser: false }],
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
