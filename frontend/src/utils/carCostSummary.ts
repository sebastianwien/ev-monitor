import type { FixedCost, FixedCostCategory } from '../api/fixedCostService'
import type { ChargeDataPoint } from '../composables/useDashboardStats'
import { fixedCostAmountForMonth, type FixedCostMode } from '../composables/useFixedCostCalc'

/**
 * Reine Rechenlogik der Kostenkarte in der Fahrzeugverwaltung, je Kalenderjahr. Energiekosten
 * kommen vom Backend (`/logs/statistics`), Fixkosten werden mit derselben Monatsregel wie im
 * Kostenverlauf des Dashboards verteilt (`fixedCostAmountForMonth`).
 */

export interface MonthlyCostPoint {
  year: number
  month: number
  energyEur: number
  fixedEur: number
  /** Fixkosten je Kategorie, nur Kategorien mit Betrag im Monat; Einnahmen negativ. */
  fixedByCategory: Partial<Record<FixedCostCategory, number>>
}

function round2(v: number): number {
  return Math.round(v * 100) / 100
}

/**
 * Monatspunkte eines Kalenderjahres: Vorjahre mit allen 12 Monaten, das laufende Jahr bis
 * einschließlich des aktuellen Monats, Folgejahre leer.
 */
export function buildYearCostSeries(
  points: ChargeDataPoint[],
  fixedCosts: FixedCost[],
  mode: FixedCostMode,
  year: number,
  now: Date = new Date(),
): MonthlyCostPoint[] {
  const lastMonth = year < now.getFullYear() ? 12 : year === now.getFullYear() ? now.getMonth() + 1 : 0
  const energyByMonth = new Map<number, number>()
  for (const p of points) {
    const d = new Date(p.timestamp)
    if (d.getFullYear() !== year) continue
    energyByMonth.set(d.getMonth() + 1, (energyByMonth.get(d.getMonth() + 1) ?? 0) + (p.costEur ?? 0))
  }
  const series: MonthlyCostPoint[] = []
  for (let month = 1; month <= lastMonth; month++) {
    const from = new Date(year, month - 1, 1)
    const to = new Date(year, month, 0)
    const fixedByCategory: Partial<Record<FixedCostCategory, number>> = {}
    for (const fc of fixedCosts) {
      const amount = fixedCostAmountForMonth(fc, from, to, mode)
      if (amount !== 0) fixedByCategory[fc.category] = (fixedByCategory[fc.category] ?? 0) + amount
    }
    let fixed = 0
    for (const c of Object.keys(fixedByCategory) as FixedCostCategory[]) {
      fixedByCategory[c] = round2(fixedByCategory[c]!)
      fixed += fixedByCategory[c]!
    }
    series.push({ year, month, energyEur: round2(energyByMonth.get(month) ?? 0), fixedEur: round2(fixed), fixedByCategory })
  }
  return series
}

/** Erster Monat mit Strom- oder Fixkosten, -1 ohne Kosten. Davor gab es das Auto noch nicht. */
export function firstCostIndex(series: MonthlyCostPoint[]): number {
  return series.findIndex(p => p.energyEur !== 0 || p.fixedEur !== 0)
}

export interface CostSummary {
  perMonthEur: number
  energyPerMonthEur: number
  /** Fixkosten netto (Einnahmen abgezogen). */
  fixedPerMonthEur: number
}

/** Monatsdurchschnitt über die Reihe. Die Karte übergibt sie mit umgelegten Fixkosten. */
export function summarizeCosts(series: MonthlyCostPoint[]): CostSummary {
  const energy = series.reduce((s, p) => s + p.energyEur, 0)
  const fixed = series.reduce((s, p) => s + p.fixedEur, 0)
  const total = energy + fixed
  const m = Math.max(1, series.length)
  return {
    perMonthEur: Math.round(total / m),
    energyPerMonthEur: Math.round(energy / m),
    fixedPerMonthEur: Math.round(fixed / m),
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

/**
 * Obergrenze der Balkenskala. Einzelne Jahreszahlungen im Fälligkeitsmonat würden die Skala
 * sonst so strecken, dass die Stromkosten der übrigen Monate unsichtbar werden. Monate über
 * der Grenze zeichnet die Karte mit Achsenbruch, der Betrag darüber bleibt der echte.
 * Grenze: dreifacher Median der Monate mit Kosten, ab drei solchen Monaten.
 */
export function chartCap(totals: number[]): number {
  const positive = totals.filter(v => v > 0).sort((a, b) => a - b)
  const max = Math.max(1, ...positive)
  if (positive.length < 3) return max
  const mid = Math.floor(positive.length / 2)
  const median = positive.length % 2 ? positive[mid] : (positive[mid - 1] + positive[mid]) / 2
  return Math.min(max, 3 * median)
}

/** Mindestanteil des gekürzten Teilstücks an der Skala: es bleibt sichtbar der größte Teil. */
const MIN_CUT_SHARE = 0.6

/**
 * Skala für gestapelte Monatsbalken (`months`: positive Teilbeträge je Monat). Ausgangspunkt
 * ist `chartCap`. Wird ein Balken gebrochen, weitet sich die Skala so weit, dass seine
 * ungekürzten Teile höchstens 40 Prozent belegen, sonst wirkte der gekürzte große Posten
 * kleiner als ein kleiner daneben.
 */
export function barScale(months: number[][]): number {
  const totals = months.map(m => m.reduce((s, v) => s + v, 0))
  const max = Math.max(1, ...totals)
  let cap = chartCap(totals)
  months.forEach((m, i) => {
    if (totals[i] <= cap) return
    cap = Math.max(cap, (totals[i] - Math.max(...m)) / (1 - MIN_CUT_SHARE))
  })
  return Math.min(max, cap)
}

/**
 * Teilstücke eines Balkens auf die Skala bringen. Passt die Summe nicht, wird nur das größte
 * Teilstück gekürzt (dort sitzt der Achsenbruch), die übrigen behalten ihre echte Länge.
 * Reicht das nicht, schrumpfen die übrigen anteilig.
 */
export function fitSegments(values: number[], cap: number): { widths: number[]; cut: number | null } {
  const total = values.reduce((s, v) => s + v, 0)
  if (total <= cap) return { widths: [...values], cut: null }
  const cut = values.indexOf(Math.max(...values))
  const cutWidth = Math.max(cap * MIN_CUT_SHARE, values[cut] - (total - cap))
  const others = total - values[cut]
  const scale = others > cap - cutWidth ? (cap - cutWidth) / others : 1
  return { widths: values.map((v, i) => (i === cut ? cutWidth : v * scale)), cut }
}

/** Kostenkategorien der Reihe nach Summe absteigend, ohne Einnahmen. Reihenfolge der Farbstufen. */
export function rankCategories(series: MonthlyCostPoint[]): FixedCostCategory[] {
  const sums = new Map<FixedCostCategory, number>()
  for (const p of series) {
    for (const [c, v] of Object.entries(p.fixedByCategory) as [FixedCostCategory, number][]) sums.set(c, (sums.get(c) ?? 0) + v)
  }
  return [...sums.entries()].filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1]).map(([c]) => c)
}

/**
 * Chips im Kostenverlauf: Strom (falls geladen), die Kategorien mit Kosten in Rangfolge, dazu
 * ausgeblendete Kategorien dieses Autos ohne Betrag im Zeitraum, damit sie einschaltbar bleiben.
 * Ausgeblendete Kategorien, die das Auto gar nicht hat (Schalter gilt für alle Autos), fehlen.
 */
export function legendKeys(o: { hasEnergy: boolean; ranked: readonly string[]; hidden: ReadonlySet<string>; carCategories: readonly string[] }): string[] {
  const keys = [...(o.hasEnergy ? ['ENERGY'] : []), ...o.ranked]
  for (const c of new Set(o.carCategories)) if (o.hidden.has(c) && !keys.includes(c)) keys.push(c)
  return keys
}
