import { describe, it, expect } from 'vitest'
import { buildYearCostSeries, firstCostIndex, summarizeCosts, groupFixedCostsByCategory, chartCap, rankCategories, fitSegments, barScale } from '../carCostSummary'
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

describe('buildYearCostSeries', () => {
  it('liefert im laufenden Jahr Januar bis heute, in Vorjahren alle 12 Monate, in der Zukunft nichts', () => {
    expect(buildYearCostSeries([], [], 'pro_rata', 2026, NOW).map(p => p.month)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    expect(buildYearCostSeries([], [], 'pro_rata', 2025, NOW)).toHaveLength(12)
    expect(buildYearCostSeries([], [], 'pro_rata', 2027, NOW)).toEqual([])
  })

  it('summiert Energiekosten je Monat und ignoriert Punkte anderer Jahre', () => {
    const series = buildYearCostSeries(
      [point('2026-10-03T10:00:00', 20), point('2026-10-19T10:00:00', 15.5), point('2025-10-01T10:00:00', 99)],
      [], 'pro_rata', 2026, NOW,
    )
    expect(series[9].energyEur).toBe(35.5)
    expect(series.reduce((s, p) => s + p.energyEur, 0)).toBe(35.5)
  })

  it('verteilt eine Einmalzahlung im pro_rata-Modus auf das Kaufjahr', () => {
    const series = buildYearCostSeries([], [fc({ amount: 1200, recurrence: 'ONE_TIME', date: '2026-03-15' })], 'pro_rata', 2026, NOW)
    expect(series[0].fixedEur).toBe(100)
  })

  it('schlüsselt Fixkosten je Monat nach Kategorie auf, Summe gleich fixedEur', () => {
    const series = buildYearCostSeries([], [
      fc({ amount: 300, category: 'TAX', recurrence: 'ONE_TIME', date: '2026-04-02' }),
      fc({ amount: 100, category: 'INSURANCE', recurrence: 'MONTHLY', startDate: '2026-01-01' }),
      fc({ amount: -40, category: 'INCOME', recurrence: 'ONE_TIME', date: '2026-04-20' }),
    ], 'due_month', 2026, NOW)
    expect(series[3].fixedByCategory).toEqual({ TAX: 300, INSURANCE: 100, INCOME: -40 })
    expect(series[3].fixedEur).toBe(360)
    expect(series[0].fixedByCategory).toEqual({ INSURANCE: 100 })
  })

  it('zeigt eine Einmalzahlung im due_month-Modus nur im Fälligkeitsmonat', () => {
    const series = buildYearCostSeries([], [fc({ amount: 1200, recurrence: 'ONE_TIME', date: '2026-03-15' })], 'due_month', 2026, NOW)
    expect(series.filter(p => p.fixedEur > 0)).toHaveLength(1)
    expect(series.find(p => p.month === 3)!.fixedEur).toBe(1200)
  })
})

describe('firstCostIndex', () => {
  it('findet den ersten Monat mit Strom oder Fixkosten, sonst -1', () => {
    const blank = buildYearCostSeries([], [], 'pro_rata', 2026, NOW)
    expect(firstCostIndex(blank)).toBe(-1)
    expect(firstCostIndex(blank.map((p, i) => ({ ...p, fixedEur: i >= 2 ? 50 : 0 })))).toBe(2)
    expect(firstCostIndex(blank.map((p, i) => ({ ...p, energyEur: i >= 4 ? 30 : 0 })))).toBe(4)
  })
})

describe('summarizeCosts', () => {
  const month = (energyEur: number, fixedEur: number) => ({ year: 2026, month: 1, energyEur, fixedEur, fixedByCategory: {} })

  it('teilt Strom und Fixkosten durch die Monate der Reihe', () => {
    const s = summarizeCosts([month(30, 300), month(50, 300), month(10, 300)])
    expect(s.perMonthEur).toBe(330)
    expect(s.energyPerMonthEur).toBe(30)
    expect(s.fixedPerMonthEur).toBe(300)
  })

  it('lässt Einnahmen über den Kosten als negative Werte stehen', () => {
    const s = summarizeCosts([month(10, -50), month(10, -50)])
    expect(s.perMonthEur).toBe(-40)
    expect(s.fixedPerMonthEur).toBe(-50)
  })

  it('liefert ohne Monate 0', () => {
    expect(summarizeCosts([]).perMonthEur).toBe(0)
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
    const series = buildYearCostSeries([], [fc({ amount: 500, recurrence: 'ONE_TIME', date: '2026-03-31' })], 'due_month', 2026, NOW)
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
    const series = buildYearCostSeries([], [fc({ amount: 100, recurrence: 'MONTHLY', startDate: '2025-01-01', endDate: '2026-01-31' })], 'due_month', 2026, NOW)
    expect(series.find(p => p.year === 2026 && p.month === 1)!.fixedEur).toBe(100)
    expect(series.find(p => p.year === 2026 && p.month === 2)!.fixedEur).toBe(0)
  })
})

describe('chartCap (Achsenbruch: einzelne Jahreszahlungen drücken die Stromkosten nicht auf 1 px)', () => {
  it('kappt Ausreißer auf das Dreifache des Medians der Monate mit Kosten', () => {
    // Fälligkeitsmonat: Versicherung 1.600 und 2.100, sonst Strom zwischen 18 und 50 Euro
    const cap = chartCap([0, 1600, 0, 26, 339, 27, 19, 50, 2100, 18])
    expect(cap).toBeGreaterThanOrEqual(50)
    expect(cap).toBeLessThan(339)
  })

  it('kappt nicht, wenn die Monate ähnlich hoch sind (umgelegte Fixkosten)', () => {
    expect(chartCap([480, 510, 530, 495, 620])).toBe(620)
  })

  it('kappt nicht bei weniger als drei Monaten mit Kosten', () => {
    expect(chartCap([0, 2100, 0, 40])).toBe(2100)
  })

  it('liefert mindestens 1, damit leere Reihen nicht durch 0 teilen', () => {
    expect(chartCap([0, 0, 0])).toBe(1)
  })
})

describe('rankCategories (Farbstufe: größte Kostenkategorie am dunkelsten)', () => {
  it('sortiert Kostenkategorien nach Jahressumme absteigend, Einnahmen fallen weg', () => {
    const series = [
      { year: 2026, month: 1, energyEur: 0, fixedEur: 0, fixedByCategory: { TAX: 25, INSURANCE: 100, INCOME: -50 } },
      { year: 2026, month: 2, energyEur: 0, fixedEur: 0, fixedByCategory: { TAX: 25, MAINTENANCE: 2054 } },
    ]
    expect(rankCategories(series)).toEqual(['MAINTENANCE', 'INSURANCE', 'TAX'])
  })
})

describe('barScale (gekürzter Teil bleibt sichtbar der größte)', () => {
  it('weitet die Skala, bis die ungekürzten Teile eines gebrochenen Balkens höchstens 40 Prozent belegen (Regression August)', () => {
    // Strom 147 neben Wartung 2.000: vorher belegte Strom drei Viertel, Wartung wirkte kleiner
    const cap = barScale([[26], [39, 300], [27], [19], [50], [147, 2000], [18]])
    expect(cap).toBeCloseTo(367.5)
  })

  it('bleibt beim dreifachen Median, wenn kein gebrochener Balken mehr als 40 Prozent Nebenteile hat', () => {
    expect(barScale([[20], [30], [25], [10, 2000]])).toBeCloseTo(82.5)
  })

  it('braucht ohne Ausreißer keinen Bruch', () => {
    expect(barScale([[100, 380], [120, 390], [90, 400]])).toBe(510)
  })
})

describe('fitSegments (Achsenbruch im größten Teilstück)', () => {
  it('lässt Teilstücke unverändert, wenn die Summe passt', () => {
    expect(fitSegments([30, 50], 100)).toEqual({ widths: [30, 50], cut: null })
  })

  it('kürzt nur das größte Teilstück und lässt die übrigen in echter Länge (Regression August)', () => {
    const { widths, cut } = fitSegments([147, 2000], 367.5)
    expect(cut).toBe(1)
    expect(widths[0]).toBe(147)
    expect(widths[1]).toBeCloseTo(220.5)
  })

  it('verkleinert die übrigen Teilstücke anteilig, wenn das gekürzte größte allein nicht reicht', () => {
    const { widths, cut } = fitSegments([50, 300, 2000], 200)
    expect(cut).toBe(2)
    expect(widths[2]).toBeCloseTo(120)
    expect(widths[0] + widths[1]).toBeCloseTo(80)
    expect(widths[1] / widths[0]).toBeCloseTo(6)
  })
})
