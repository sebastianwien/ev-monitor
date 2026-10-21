import type { FixedCost, FixedCostCategory } from '../api/fixedCostService'
import type { ChargeDataPoint, StatisticsData } from '../composables/useDashboardStats'
import { fixedCostAmountForMonth, type FixedCostMode } from '../composables/useFixedCostCalc'

/**
 * Reine Rechenlogik der Kostenkarte in der Fahrzeugverwaltung. Energiekosten kommen vom
 * Backend (`/logs/statistics`), Fixkosten werden mit derselben Monatsregel wie im
 * Kostenverlauf des Dashboards verteilt (`fixedCostAmountForMonth`).
 */

export interface MonthlyCostPoint {
  year: number
  month: number
  energyEur: number
  fixedEur: number
}

function round2(v: number): number {
  return Math.round(v * 100) / 100
}

function lastTwelveMonths(now: Date): { year: number; month: number }[] {
  const months: { year: number; month: number }[] = []
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    months.push({ year: d.getFullYear(), month: d.getMonth() + 1 })
  }
  return months
}

/** 12 Monatspunkte bis einschließlich des aktuellen Monats, jeder Monat vorhanden. */
export function buildMonthlyCostSeries(
  points: ChargeDataPoint[],
  fixedCosts: FixedCost[],
  mode: FixedCostMode,
  now: Date = new Date(),
): MonthlyCostPoint[] {
  const energyByMonth = new Map<string, number>()
  for (const p of points) {
    const d = new Date(p.timestamp)
    const key = `${d.getFullYear()}-${d.getMonth() + 1}`
    energyByMonth.set(key, (energyByMonth.get(key) ?? 0) + (p.costEur ?? 0))
  }
  return lastTwelveMonths(now).map(({ year, month }) => {
    const from = new Date(year, month - 1, 1)
    const to = new Date(year, month, 0)
    const fixed = fixedCosts.reduce((sum, fc) => sum + fixedCostAmountForMonth(fc, from, to, mode), 0)
    return {
      year,
      month,
      energyEur: round2(energyByMonth.get(`${year}-${month}`) ?? 0),
      fixedEur: round2(fixed),
    }
  })
}

export interface CostSummary {
  /** Gesamtkosten je Kilometer in Cent, null ohne Kilometerstand. */
  ctPerKm: number | null
  perMonthEur: number
  energyPerMonthEur: number
  /** Fixkosten netto (Einnahmen abgezogen). */
  fixedPerMonthEur: number
  /** Anteil der Energiekosten an den Gesamtkosten, null ohne Kosten. */
  energyShare: number | null
}

type CostStats = Pick<StatisticsData, 'energyCostEur' | 'fixedCostEur' | 'fixedIncomeEur' | 'totalDistanceKm'> & { totalCostEur?: number | null }

export function summarizeCosts(stats: CostStats, months = 12): CostSummary {
  const energy = stats.energyCostEur ?? 0
  const fixedNet = (stats.fixedCostEur ?? 0) - (stats.fixedIncomeEur ?? 0)
  const total = energy + fixedNet
  const km = stats.totalDistanceKm
  return {
    ctPerKm: km && km > 0 ? round2((total / km) * 100) : null,
    perMonthEur: Math.round(total / months),
    energyPerMonthEur: Math.round(energy / months),
    fixedPerMonthEur: Math.round(fixedNet / months),
    energyShare: total > 0 ? energy / total : null,
  }
}

/** Reihenfolge der Kategorien in der Kostenkarte: Pflichtkosten zuerst, Einnahmen zuletzt. */
export const FIXED_COST_CATEGORY_ORDER: FixedCostCategory[] = [
  'INSURANCE', 'TAX', 'LEASING', 'FINANCING', 'MAINTENANCE', 'TIRES', 'TUNING',
  'TOLL', 'CLEANING', 'OTHER', 'INCOME', 'COMPENSATION',
]

export interface FixedCostGroup {
  category: FixedCostCategory
  /** Einträge, die im Jahr etwas beitragen, neueste zuerst. */
  items: FixedCost[]
  yearTotalEur: number
}

/**
 * Was im Jahr tatsächlich gezahlt wurde: Fälligkeiten gezählt, nicht Monatsanteile summiert.
 * Monatsanteile würden durch Rundung von der Zahlungssumme abweichen.
 */
function yearAmount(fc: FixedCost, year: number): number {
  let sum = 0
  for (let month = 1; month <= 12; month++) {
    sum += fixedCostAmountForMonth(fc, new Date(year, month - 1, 1), new Date(year, month, 0), 'due_month')
  }
  return sum
}

function sortKey(fc: FixedCost): string {
  return fc.date ?? fc.startDate ?? fc.createdAt
}

/** Gruppen je Kategorie mit Jahressumme der Zahlungen; Einträge ohne Zahlung im Jahr fehlen, leere Gruppen auch. */
export function groupFixedCostsByCategory(items: FixedCost[], year: number): FixedCostGroup[] {
  const groups: FixedCostGroup[] = []
  for (const category of FIXED_COST_CATEGORY_ORDER) {
    const inYear = items
      .filter(fc => fc.category === category)
      .map(fc => ({ fc, amount: yearAmount(fc, year) }))
      .filter(x => x.amount !== 0)
      .sort((a, b) => sortKey(b.fc).localeCompare(sortKey(a.fc)))
    if (inYear.length === 0) continue
    groups.push({
      category,
      items: inYear.map(x => x.fc),
      yearTotalEur: round2(inYear.reduce((s, x) => s + x.amount, 0)),
    })
  }
  return groups
}
