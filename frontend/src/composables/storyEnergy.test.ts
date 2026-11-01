import { describe, it, expect } from 'vitest'
import { buildEnergyFlow, buildRange, costLayers, monthCosts, costStack, flowShares, energyUse, rangeConsumption, currentRangeKm, rangeAtSoc, rangeOriginKey, type UseTrips } from './storyEnergy'
import type { RangeBasisSource, RangeScope, StatisticsData } from './useDashboardStats'

function stats(over: Partial<StatisticsData> = {}): StatisticsData {
  return {
    totalKwhCharged: 500,
    energyCostEur: 120,
    fixedCostEur: 0,
    fixedIncomeEur: 0,
    totalCostEur: 120,
    avgCostPerKwh: 0.24,
    cheapestChargeEur: 3,
    mostExpensiveChargeEur: 20,
    avgChargeDurationMinutes: 100,
    totalCharges: 12,
    totalDistanceKm: 2500,
    avgConsumptionKwhPer100km: 18,
    estimatedConsumptionCount: 0,
    summerConsumptionKwhPer100km: 15,
    winterConsumptionKwhPer100km: 21,
    chargesOverTime: [],
    chargingTypeSplit: { acKwh: 400, dcKwh: 90, unknownKwh: 10 },
    locationSplit: { publicKwh: 100, privateKwh: 390, unknownKwh: 10 },
    peerBenchmark: null,
    chargingEfficiencySplit: { gridKwh: 500, vehicleKwh: 450, coveredLogCount: 10, totalLogCount: 12 },
    ...over,
  }
}

describe('buildEnergyFlow', () => {
  it('leitet Verlust, Splits und Abdeckung ab', () => {
    const f = buildEnergyFlow(stats())!
    expect(f.lossKwh).toBeCloseTo(50)
    expect(f.lossPercent).toBe(10)
    expect(f.acKwh).toBe(400)
    expect(f.dcKwh).toBe(90)
    expect(f.privateKwh).toBe(390)
    expect(f.publicKwh).toBe(100)
    expect(f.distanceKm).toBe(2500)
    expect(f.partial).toBe(true)
    expect(f.coveredLogCount).toBe(10)
  })

  it('liefert null ohne Verlustdaten', () => {
    expect(buildEnergyFlow(stats({ chargingEfficiencySplit: null }))).toBeNull()
    expect(buildEnergyFlow(stats({ chargingEfficiencySplit: { gridKwh: 0, vehicleKwh: 0, coveredLogCount: 0, totalLogCount: 3 } }))).toBeNull()
  })

  it('klemmt negativen Verlust auf 0 (Fahrzeug meldet mehr als das Netz)', () => {
    const f = buildEnergyFlow(stats({ chargingEfficiencySplit: { gridKwh: 100, vehicleKwh: 104, coveredLogCount: 2, totalLogCount: 2 } }))!
    expect(f.lossKwh).toBe(0)
    expect(f.lossPercent).toBe(0)
    expect(f.partial).toBe(false)
  })

  it('kommt ohne AC/DC- und Ortsdaten aus', () => {
    const f = buildEnergyFlow(stats({ chargingTypeSplit: null, locationSplit: null }))!
    expect(f.acKwh).toBe(0)
    expect(f.publicKwh).toBe(0)
  })
})

const dto = (rangeKm: number | null, consumptionKwhPer100km: number, source: RangeBasisSource = 'OWN_DRIVE', scope: RangeScope | null = null) =>
  ({ rangeKm, consumptionKwhPer100km, source, scope })

describe('buildRange', () => {
  it('nimmt km, Verbrauch und Herkunft der letzten 4 Wochen vom Backend, keine eigene Formel', () => {
    const r = buildRange(stats({ range: { current: dto(300, 18), summer: null, winter: null } }))!
    expect(r).toEqual({ km: 300, consumption: 18, source: 'OWN_DRIVE', scope: null, summer: null, winter: null })
  })

  it('Sommer und Winter als eigene Zeilen mit eigener Herkunft, fehlende Saison bleibt null', () => {
    const r = buildRange(stats({ range: { current: dto(300, 18), summer: dto(338, 16), winter: dto(225, 24, 'PEER_DRIVE', 'MODEL') } }))!
    expect(r.summer).toEqual({ km: 338, consumption: 16, source: 'OWN_DRIVE', scope: null })
    expect(r.winter).toEqual({ km: 225, consumption: 24, source: 'PEER_DRIVE', scope: 'MODEL' })
    expect(buildRange(stats({ range: { current: dto(300, 18), summer: null, winter: null } }))!.winter).toBeNull()
  })

  it('reicht Variante oder Modell bei anderen Fahrern durch', () => {
    expect(buildRange(stats({ range: { current: dto(338, 16, 'PEER_DRIVE', 'VARIANT'), summer: null, winter: null } }))!.scope).toBe('VARIANT')
  })

  it('liefert null ohne Reichweite der letzten 4 Wochen oder ohne km (keine Kapazität)', () => {
    expect(buildRange(stats())).toBeNull()
    expect(buildRange(stats({ range: null }))).toBeNull()
    expect(buildRange(stats({ range: { current: null, summer: dto(338, 16), winter: null } }))).toBeNull()
    expect(buildRange(stats({ range: { current: dto(null, 18), summer: null, winter: null } }))).toBeNull()
  })
})

describe('rangeConsumption und currentRangeKm', () => {
  it('lesen die 4-Wochen-Zeile, null ohne Backend-Wert', () => {
    const s = stats({ range: { current: dto(300, 18), summer: null, winter: null } })
    expect(rangeConsumption(s)).toBe(18)
    expect(currentRangeKm(s)).toBe(300)
    expect(rangeConsumption(stats())).toBeNull()
    expect(currentRangeKm(stats())).toBeNull()
  })
})

describe('rangeAtSoc: Reichweite nach der Ladung als Anteil des Fensters 100 → 10 %', () => {
  it('skaliert die Backend-km auf den Ladestand, auf 10 km gerundet', () => {
    // 409 km für 90 Punkte, bis 80 % sind 70 Punkte: 318 → 320
    expect(rangeAtSoc(409, 80)).toBe(320)
    expect(rangeAtSoc(409, 100)).toBe(410)
    expect(rangeAtSoc(409, 10)).toBeNull()
    expect(rangeAtSoc(null, 80)).toBeNull()
  })
})

describe('rangeOriginKey', () => {
  it('ein Schlüssel je Stufe, bei anderen Fahrern je Variante oder Modell', () => {
    expect(rangeOriginKey({ source: 'OWN_DRIVE', scope: null })).toBe('story.range_origin_drive')
    expect(rangeOriginKey({ source: 'PEER_DRIVE', scope: 'VARIANT' })).toBe('story.range_origin_peer_variant')
    expect(rangeOriginKey({ source: 'PEER_DRIVE', scope: 'MODEL' })).toBe('story.range_origin_peer_model')
    expect(rangeOriginKey({ source: 'OWN_CHARGES', scope: null })).toBe('story.range_origin_charges')
    expect(rangeOriginKey({ source: 'PEER_CHARGES', scope: null })).toBe('story.range_origin_peer_charges')
    expect(rangeOriginKey({ source: 'WLTP_ESTIMATE', scope: null })).toBe('story.range_origin_wltp')
  })
})

describe('costLayers', () => {
  it('liefert null, wenn es nur Stromkosten gibt', () => {
    expect(costLayers(stats())).toBeNull()
  })

  it('baut Strom, Fixkosten, Einnahmen und Netto', () => {
    const layers = costLayers(stats({ fixedCostEur: 80, fixedIncomeEur: 25, totalCostEur: 175 }))!
    expect(layers.map(l => l.kind)).toEqual(['energy', 'fixed', 'income', 'net'])
    expect(layers.find(l => l.kind === 'income')!.eur).toBe(25)
    expect(layers.find(l => l.kind === 'net')!.eur).toBe(175)
  })

  it('laesst Schichten mit 0 weg', () => {
    const layers = costLayers(stats({ fixedCostEur: 80, totalCostEur: 200 }))!
    expect(layers.map(l => l.kind)).toEqual(['energy', 'fixed', 'net'])
  })
})

describe('flowShares: Anteile des Netzstroms für den Balken', () => {
  const f = { gridKwh: 500, vehicleKwh: 450, lossKwh: 50, lossPercent: 10, acKwh: 400, dcKwh: 100, privateKwh: 350, publicKwh: 100, distanceKm: 2500, coveredLogCount: 10, totalLogCount: 12, partial: true }

  it('Rest nach Verlust nach Ladeort geteilt, Summe 100', () => {
    expect(flowShares(f)).toEqual([{ key: 'home', pct: 70 }, { key: 'public', pct: 20 }, { key: 'loss', pct: 10 }])
  })

  it('Ort ohne Strom entfällt, ohne Aufteilung ein Stück im Akku', () => {
    expect(flowShares({ ...f, privateKwh: 0, publicKwh: 50, lossPercent: 5 })).toEqual([{ key: 'public', pct: 95 }, { key: 'loss', pct: 5 }])
    expect(flowShares({ ...f, privateKwh: 0, publicKwh: 0 })).toEqual([{ key: 'vehicle', pct: 90 }, { key: 'loss', pct: 10 }])
  })
})

describe('monthCosts und costStack', () => {
  it('Strom und Fixkosten je Monat, fehlende Statistik zählt als 0', () => {
    const months = [stats({ energyCostEur: 15, fixedCostEur: 45, fixedIncomeEur: 20 }), null, stats({ energyCostEur: 20 })].map(monthCosts)
    expect(months).toEqual([
      { energyEur: 15, fixedEur: 45, incomeEur: 20 },
      { energyEur: 0, fixedEur: 0, incomeEur: 0 },
      { energyEur: 20, fixedEur: 0, incomeEur: 0 },
    ])
  })

  it('Stapel: Strom unten, Fixkosten oben, Einnahmen nicht im Balken', () => {
    expect(costStack([{ energyEur: 15, fixedEur: 45, incomeEur: 20 }, { energyEur: 20, fixedEur: 0, incomeEur: 0 }]))
      .toEqual({ energy: [15, 20], fixed: [45, 0] })
  })
})

describe('energyUse: wo der Strom des Monats bleibt', () => {
  // Ihle Oktober: 46 Fahrten, Stand 34 kWh, eine von zwei Ladungen gemessen, alles öffentlich
  const trips = (over: Partial<UseTrips> = {}): UseTrips => ({ trips: 46, driveKwh: 33.5, standbyKwh: 34, standbyLocked: false, standbyEur: 9.86, ...over })
  const month = (over: Partial<StatisticsData> = {}) => stats({
    avgCostPerKwh: 0.29,
    locationSplit: { publicKwh: 41, privateKwh: 0, unknownKwh: 0 },
    chargingEfficiencySplit: { gridKwh: 20, vehicleKwh: 17, coveredLogCount: 1, totalLogCount: 2, lossKwh: 3, estimatedLossLogCount: 1 },
    ...over,
  })

  it('Posten, Anteile mit Summe 100, Euro für Stand und Laden, Ladeort', () => {
    const u = energyUse(month(), trips())!
    expect([u.driveKwh, u.standbyKwh, u.chargeLossKwh]).toEqual([33.5, 34, 3])
    expect(u.shares).toEqual([{ key: 'drive', pct: 48 }, { key: 'standby', pct: 48 }, { key: 'charge', pct: 4 }])
    expect(u.standbyEur).toBeCloseTo(9.86)
    expect(u.chargeEur).toBeCloseTo(0.87)
    expect(u.lossEur).toBeCloseTo(10.73)
    expect([u.trips, u.charges, u.estimatedCharges]).toEqual([46, 2, 1])
    expect(u.homePct).toBe(0)
  })

  it('Anteile ergeben immer 100, auch wenn zwei Stücke aufgerundet werden müssen', () => {
    const lossOf = (kwh: number) => month({ chargingEfficiencySplit: { gridKwh: 20, vehicleKwh: 17, coveredLogCount: 1, totalLogCount: 2, lossKwh: kwh, estimatedLossLogCount: 1 } })
    const u = energyUse(lossOf(32.6), trips({ driveKwh: 33.7, standbyKwh: 33.7 }))!
    expect(u.shares.reduce((a, s) => a + (s.pct ?? 0), 0)).toBe(100)
    expect(u.shares.map(s => s.pct)).toEqual([34, 34, 32])
  })

  it('ohne Fahrten mit Energiedaten kein Ergebnis (Kachel bleibt wie bisher)', () => {
    expect(energyUse(month(), null)).toBeNull()
    expect(energyUse(month(), trips({ driveKwh: null }))).toBeNull()
  })

  it('Stand gesperrt: Stück ohne Prozent, Anteile nur aus Fahren und Laden, Euro nur Laden', () => {
    const u = energyUse(month(), trips({ standbyKwh: null, standbyLocked: true, standbyEur: null }))!
    expect(u.standbyKwh).toBeNull()
    expect(u.shares).toEqual([{ key: 'drive', pct: 92 }, { key: 'standby', pct: null }, { key: 'charge', pct: 8 }])
    expect(u.lossEur).toBeCloseTo(0.87)
  })

  it('ohne Preis keine Euro', () => {
    const u = energyUse(month({ avgCostPerKwh: 0 }), trips({ standbyEur: null }))!
    expect([u.lossEur, u.standbyEur, u.chargeEur]).toEqual([null, null, null])
  })

  it('Stand ohne eigenen Euro-Wert rechnet mit dem Monatsschnitt', () => {
    expect(energyUse(month(), trips({ standbyEur: null }))!.standbyEur).toBeCloseTo(9.86)
  })

  it('älteres Backend ohne Hochrechnung: gemessener Verlust aus den Ladungen mit beiden Werten', () => {
    const u = energyUse(month({ chargingEfficiencySplit: { gridKwh: 20, vehicleKwh: 17, coveredLogCount: 1, totalLogCount: 2 } }), trips())!
    expect(u.chargeLossKwh).toBe(3)
    expect([u.charges, u.estimatedCharges]).toEqual([1, 0])
  })

  it('ohne Ladung im Monat Ladeverlust 0, ohne Ortsangabe kein Ladeort', () => {
    const u = energyUse(month({ chargingEfficiencySplit: null, locationSplit: null }), trips())!
    expect(u.chargeLossKwh).toBe(0)
    expect(u.shares.map(s => s.key)).toEqual(['drive', 'standby'])
    expect(u.homePct).toBeNull()
  })
})
