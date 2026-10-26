import { describe, it, expect } from 'vitest'
import { buildEnergyFlow, buildRange, costLayers, monthCosts, costStack, flowShares, energyUse, RANGE_SOC_WINDOWS, type UseTrips } from './storyEnergy'
import type { StatisticsData } from './useDashboardStats'

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

describe('buildRange', () => {
  it('rechnet die Reichweite je SoC-Fenster mit dem Verbrauch der letzten 4 Wochen', () => {
    const r = buildRange(stats(), 60)!
    expect(r.consumption).toBe(18)
    expect(r.windows).toHaveLength(RANGE_SOC_WINDOWS.length)
    // 90 -> 10 %: 60 kWh * 0.8 / 18 * 100 = 266,7 km
    expect(r.km).toBe(267)
    expect(r.windows.find(w => w.socMax === 100)!.km).toBe(333)
  })

  it('traegt Sommer und Winter als eigene Zeilen, fehlende Saison bleibt null', () => {
    const r = buildRange(stats({ summerConsumptionKwhPer100km: 16 }), 60)!
    // Saisonwerte kommen fertig aggregiert aus dem Backend: 60 * 0.8 / 21 * 100 = 229, / 16 = 300
    expect(r.winter).toEqual({ consumption: 21, km: 229 })
    expect(r.summer).toEqual({ consumption: 16, km: 300 })
    expect(buildRange(stats({ winterConsumptionKwhPer100km: null }), 60)!.winter).toBeNull()
  })

  it('rechnet die Reserve unter 10 % mit dem Verbrauch der letzten 4 Wochen', () => {
    // 60 kWh * 0.1 / 18 * 100 = 33,3 km
    expect(buildRange(stats(), 60)!.reserveKm).toBe(33)
  })

  it('rechnet den Winterwert anderer Fahrer mit der eigenen Kapazitaet, nur ohne eigenen Winter', () => {
    // 60 * 0.8 / 24 * 100 = 200
    expect(buildRange(stats({ winterConsumptionKwhPer100km: null }), 60, 24)!.peerWinter).toEqual({ consumption: 24, km: 200 })
    expect(buildRange(stats({ winterConsumptionKwhPer100km: 21 }), 60, 24)!.peerWinter).toBeNull()
    expect(buildRange(stats({ winterConsumptionKwhPer100km: null }), 60)!.peerWinter).toBeNull()
  })

  it('liefert null ohne Kapazitaet oder ohne Verbrauch der letzten 4 Wochen', () => {
    expect(buildRange(stats(), null)).toBeNull()
    expect(buildRange(stats({ avgConsumptionKwhPer100km: null }), 60)).toBeNull()
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
