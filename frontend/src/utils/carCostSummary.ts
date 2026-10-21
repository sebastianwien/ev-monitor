import type { FixedCost, FixedCostCategory } from '../api/fixedCostService'
import type { ChargeDataPoint } from '../composables/useDashboardStats'
import { fixedCostAmountForMonth, parseLocalDate, type FixedCostMode } from '../composables/useFixedCostCalc'
import { calcCostPer100km, calcFullCostPer100km } from '../composables/useDashboardStats'

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
  /** Vollkosten je Kilometer in Cent, null ohne Kilometerstand oder ohne Verbrauch. */
  ctPerKm: number | null
  perMonthEur: number
  energyPerMonthEur: number
  /** Fixkosten netto (Einnahmen abgezogen). */
  fixedPerMonthEur: number
  /** Anteil der Energiekosten an den Gesamtkosten, null ohne positive Kosten. */
  energyShare: number | null
}

export interface CostInput {
  avgCostPerKwh: number | null
  avgConsumptionKwhPer100km: number | null
  energyCostEur: number
  totalDistanceKm: number | null
}

/**
 * Dieselbe Vollkosten-Rechnung wie das Dashboard (`calcFullCostPer100km`): Energiekosten als
 * Preis je kWh mal Verbrauch, Fixkosten auf die gefahrene Strecke. Euro je 100 km entspricht
 * Cent je km. `fixedNetEur` kommt aus der Monatsreihe, damit Kopfzahl und Balken denselben
 * Umlagemodus nutzen.
 */
export function summarizeCosts(stats: CostInput, fixedNetEur: number, months: number): CostSummary {
  const energy = stats.energyCostEur ?? 0
  const total = energy + fixedNetEur
  const m = Math.max(1, months)
  const per100km = calcFullCostPer100km(
    calcCostPer100km(stats.avgCostPerKwh ?? null, stats.avgConsumptionKwhPer100km ?? null),
    fixedNetEur, 0, stats.totalDistanceKm,
  )
  return {
    ctPerKm: per100km === null ? null : round2(per100km),
    perMonthEur: Math.round(total / m),
    energyPerMonthEur: Math.round(energy / m),
    fixedPerMonthEur: Math.round(fixedNetEur / m),
    energyShare: total > 0 && energy > 0 ? energy / total : null,
  }
}

/** Summe der Fixkosten (netto) über die Monatsreihe, im gewählten Umlagemodus. */
export function fixedNetFromSeries(series: MonthlyCostPoint[]): number {
  return round2(series.reduce((s, p) => s + p.fixedEur, 0))
}

/**
 * Wie viele der 12 Monate das Auto wirklich abdeckt: ab dem ersten Monat mit Stromkosten,
 * sonst ab Erstzulassung. Damit zeigt ein Auto, das erst drei Monate dabei ist, keinen
 * Zwölftel-Monatswert.
 */
export function monthsCovered(series: MonthlyCostPoint[], registrationDate: string | null, now: Date = new Date()): number {
  const first = series.findIndex(p => p.energyEur > 0)
  if (first >= 0) return series.length - first
  if (!registrationDate) return series.length
  const reg = parseLocalDate(registrationDate)
  const diff = (now.getFullYear() - reg.getFullYear()) * 12 + (now.getMonth() - reg.getMonth()) + 1
  return Math.min(series.length, Math.max(1, diff))
}

/** Cent-Anzeige der Kopfzahl: unter 10 ct eine Nachkommastelle, darüber ganze Cent. */
export function centLabel(ct: number, n: (v: number, o?: { maximumFractionDigits?: number }) => string): string {
  return `${n(ct, { maximumFractionDigits: Math.abs(ct) < 10 ? 1 : 0 })} ct`
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
