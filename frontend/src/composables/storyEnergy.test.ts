import { describe, it, expect } from 'vitest'
import { buildEnergyFlow, buildRange, costLayers, monthCosts, costStack, flowShares, RANGE_SOC_WINDOWS } from './storyEnergy'
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
