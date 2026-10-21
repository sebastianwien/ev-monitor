import { describe, it, expect } from 'vitest'
import { buildMonthlyCostSeries, summarizeCosts, groupFixedCostsByCategory, monthsCovered, fixedNetFromSeries, centLabel } from '../carCostSummary'
import type { FixedCost, FixedCostCategory, FixedCostRecurrence } from '../../api/fixedCostService'
import type { ChargeDataPoint } from '../../composables/useDashboardStats'

let seq = 0

function fc(partial: {
  amount: number
  category?: FixedCostCategory
  recurrence: FixedCostRecurrence
  date?: string | null
  startDate?: string | null
  endDate?: string | null
}): FixedCost {
  return {
    id: `id-${seq++}`,
    carId: 'car-1',
    description: 'test',
    category: partial.category ?? 'OTHER',
    amount: partial.amount,
    recurrence: partial.recurrence,
    date: partial.date ?? null,
    startDate: partial.startDate ?? null,
    endDate: partial.endDate ?? null,
    createdAt: '2024-01-01T00:00:00Z',
  }
}

function point(timestamp: string, costEur: number): ChargeDataPoint {
  return { timestamp, costEur, kwhCharged: 10, distanceKm: null, consumptionKwhPer100km: null }
}

const NOW = new Date(2026, 9, 21) // 21.10.2026

describe('buildMonthlyCostSeries', () => {
  it('liefert immer 12 Monate bis zum aktuellen, auch ohne Daten', () => {
    const series = buildMonthlyCostSeries([], [], 'pro_rata', NOW)
    expect(series).toHaveLength(12)
    expect(series[0]).toMatchObject({ year: 2025, month: 11, energyEur: 0, fixedEur: 0 })
    expect(series[11]).toMatchObject({ year: 2026, month: 10 })
  })

  it('summiert Energiekosten je Monat und ignoriert Punkte vor dem Fenster', () => {
    const series = buildMonthlyCostSeries(
      [point('2026-10-03T10:00:00', 20), point('2026-10-19T10:00:00', 15.5), point('2025-10-01T10:00:00', 99)],
      [], 'pro_rata', NOW,
    )
    expect(series[11].energyEur).toBe(35.5)
    expect(series.reduce((s, p) => s + p.energyEur, 0)).toBe(35.5)
  })

  it('verteilt eine Einmalzahlung im pro_rata-Modus auf das Kaufjahr, nicht auf das Vorjahr', () => {
    const series = buildMonthlyCostSeries([], [fc({ amount: 1200, recurrence: 'ONE_TIME', date: '2026-03-15' })], 'pro_rata', NOW)
    const dec2025 = series.find(p => p.year === 2025 && p.month === 12)!
    const jan2026 = series.find(p => p.year === 2026 && p.month === 1)!
    expect(dec2025.fixedEur).toBe(0)
    expect(jan2026.fixedEur).toBe(100)
  })

  it('zeigt eine Einmalzahlung im due_month-Modus nur im Fälligkeitsmonat', () => {
    const series = buildMonthlyCostSeries([], [fc({ amount: 1200, recurrence: 'ONE_TIME', date: '2026-03-15' })], 'due_month', NOW)
    expect(series.filter(p => p.fixedEur > 0)).toHaveLength(1)
    expect(series.find(p => p.month === 3)!.fixedEur).toBe(1200)
  })
})

describe('summarizeCosts (dieselbe Rechnung wie das Dashboard: Preis je kWh mal Verbrauch plus Fixkosten je km)', () => {
  const stats = { avgCostPerKwh: 0.3, avgConsumptionKwhPer100km: 20, energyCostEur: 936, totalDistanceKm: 14950 }

  it('rechnet ct/km aus Energiekosten je 100 km plus Fixkosten je km', () => {
    const s = summarizeCosts(stats, 3900, 12)
    expect(s.ctPerKm).toBeCloseTo(6 + 26.09, 1)
    expect(s.perMonthEur).toBe(403)
    expect(s.energyPerMonthEur).toBe(78)
    expect(s.fixedPerMonthEur).toBe(325)
    expect(s.energyShare).toBeCloseTo(0.1935, 3)
  })

  it('hat ohne Kilometer oder ohne Verbrauch kein ct/km, aber Monatswerte', () => {
    expect(summarizeCosts({ ...stats, totalDistanceKm: null }, 0, 12).ctPerKm).toBeNull()
    expect(summarizeCosts({ ...stats, avgConsumptionKwhPer100km: null }, 0, 12).ctPerKm).toBeNull()
    expect(summarizeCosts({ ...stats, totalDistanceKm: null }, 0, 12).perMonthEur).toBe(78)
  })

  it('teilt durch die tatsächlich abgedeckten Monate', () => {
    expect(summarizeCosts({ ...stats, energyCostEur: 300 }, 0, 3).energyPerMonthEur).toBe(100)
  })

  it('lässt Einnahmen über den Kosten als negative Werte stehen, ohne Energieanteil', () => {
    const s = summarizeCosts({ ...stats, energyCostEur: 100 }, -400, 12)
    expect(s.perMonthEur).toBe(-25)
    expect(s.fixedPerMonthEur).toBe(-33)
    expect(s.energyShare).toBeNull()
  })

  it('hat ohne jede Kosten keinen Energieanteil', () => {
    const s = summarizeCosts({ avgCostPerKwh: null, avgConsumptionKwhPer100km: null, energyCostEur: 0, totalDistanceKm: null }, 0, 12)
    expect(s.energyShare).toBeNull()
    expect(s.perMonthEur).toBe(0)
  })
})

describe('monthsCovered', () => {
  const blank = buildMonthlyCostSeries([], [], 'pro_rata', NOW)
  it('zählt ab dem ersten Monat mit Stromkosten', () => {
    const series = blank.map((p, i) => ({ ...p, energyEur: i >= 9 ? 30 : 0 }))
    expect(monthsCovered(series, '2019-05-01', NOW)).toBe(3)
  })
  it('nimmt ohne Stromkosten die Monate seit Erstzulassung, mindestens 1, höchstens 12', () => {
    expect(monthsCovered(blank, '2026-08-01', NOW)).toBe(3)
    expect(monthsCovered(blank, '2026-10-15', NOW)).toBe(1)
    expect(monthsCovered(blank, '2019-05-01', NOW)).toBe(12)
    expect(monthsCovered(blank, null, NOW)).toBe(12)
  })
})

describe('fixedNetFromSeries und centLabel', () => {
  it('summiert die Fixkosten der Monatsreihe, Einnahmen negativ', () => {
    const series = buildMonthlyCostSeries([], [
      fc({ amount: 1200, recurrence: 'ONE_TIME', date: '2026-03-15' }),
      fc({ amount: -250, category: 'INCOME', recurrence: 'YEARLY', startDate: '2025-05-01' }),
    ], 'due_month', NOW)
    expect(fixedNetFromSeries(series)).toBe(950)
  })
  it('zeigt unter 10 ct eine Nachkommastelle, darüber ganze Cent', () => {
    const n = (v: number, o?: { maximumFractionDigits?: number }) => v.toLocaleString('de-DE', o)
    expect(centLabel(7.84, n)).toBe('7,8 ct')
    expect(centLabel(32.09, n)).toBe('32 ct')
  })
})

describe('groupFixedCostsByCategory', () => {
  it('gruppiert in fester Reihenfolge, Einnahmen zuletzt, leere Kategorien fehlen', () => {
    const groups = groupFixedCostsByCategory([
      fc({ amount: 300, category: 'MAINTENANCE', recurrence: 'ONE_TIME', date: '2026-03-14' }),
      fc({ amount: -250, category: 'INCOME', recurrence: 'YEARLY', startDate: '2025-05-01' }),
      fc({ amount: 1600, category: 'INSURANCE', recurrence: 'YEARLY', startDate: '2026-01-02' }),
      fc({ amount: 2000, category: 'MAINTENANCE', recurrence: 'ONE_TIME', date: '2026-08-07' }),
    ], 2026)
    expect(groups.map(g => g.category)).toEqual(['INSURANCE', 'MAINTENANCE', 'INCOME'])
    expect(groups[1].items).toHaveLength(2)
    expect(groups[1].yearTotalEur).toBe(2300)
    expect(groups[0].yearTotalEur).toBe(1600)
    expect(groups[2].yearTotalEur).toBe(-250)
  })

  it('listet Einträge eines anderen Jahres nicht', () => {
    const groups = groupFixedCostsByCategory([
      fc({ amount: 900, category: 'TIRES', recurrence: 'ONE_TIME', date: '2025-10-02' }),
    ], 2026)
    expect(groups).toHaveLength(0)
  })

  it('listet laufende wiederkehrende Kosten auch dann, wenn sie vor dem Jahr begannen', () => {
    const groups = groupFixedCostsByCategory([
      fc({ amount: 100, category: 'LEASING', recurrence: 'MONTHLY', startDate: '2024-01-01' }),
    ], 2026)
    expect(groups).toHaveLength(1)
    expect(groups[0].yearTotalEur).toBe(1200)
  })
})

describe('Monatsletzter (Regression: ISO-Datum wurde als UTC geparst und fiel aus dem lokalen Monat)', () => {
  it('zählt eine Einmalzahlung am 31.03. im März', () => {
    const series = buildMonthlyCostSeries([], [fc({ amount: 500, recurrence: 'ONE_TIME', date: '2026-03-31' })], 'due_month', NOW)
    expect(series.find(p => p.month === 3)!.fixedEur).toBe(500)
  })

  it('listet Einträge am Monatsletzten in den Kategoriegruppen mit voller Jahressumme', () => {
    const groups = groupFixedCostsByCategory([
      fc({ amount: 500, category: 'MAINTENANCE', recurrence: 'ONE_TIME', date: '2026-03-31' }),
      fc({ amount: 100, category: 'LEASING', recurrence: 'MONTHLY', startDate: '2026-01-31' }),
      fc({ amount: 1600, category: 'INSURANCE', recurrence: 'YEARLY', startDate: '2026-02-28' }),
    ], 2026)
    expect(groups.map(g => [g.category, g.yearTotalEur])).toEqual([['INSURANCE', 1600], ['LEASING', 1200], ['MAINTENANCE', 500]])
  })

  it('endet eine Laufzeit am Monatsletzten einschließlich', () => {
    const series = buildMonthlyCostSeries([], [fc({ amount: 100, recurrence: 'MONTHLY', startDate: '2025-01-01', endDate: '2026-01-31' })], 'due_month', NOW)
    expect(series.find(p => p.year === 2026 && p.month === 1)!.fixedEur).toBe(100)
    expect(series.find(p => p.year === 2026 && p.month === 2)!.fixedEur).toBe(0)
  })
})
