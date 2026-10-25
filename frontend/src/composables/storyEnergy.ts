import type { StatisticsData } from './useDashboardStats'

/**
 * Energie-Ableitungen fuer das Story-Dashboard: Energiefluss (Netz, Akku, Strasse),
 * saisonale Reichweite und die Kostenschichten im Hero. Reine Funktionen ohne Vue.
 */

export interface EnergyFlow {
  gridKwh: number
  vehicleKwh: number
  lossKwh: number
  /** Ganzzahlig, nie negativ. */
  lossPercent: number
  acKwh: number
  dcKwh: number
  privateKwh: number
  publicKwh: number
  distanceKm: number | null
  coveredLogCount: number
  totalLogCount: number
  /** True, wenn nicht alle Ladungen des Monats Netz- und Fahrzeugwert haben. */
  partial: boolean
}

/** Null, wenn der Monat keine Ladung mit Netz- und Fahrzeugwert hat. */
export interface FlowShare { key: 'home' | 'public' | 'vehicle' | 'loss'; pct: number }

/**
 * Anteile für den Strom-Balken in ganzen Prozent, Summe 100. Der Rest nach dem Verlust teilt sich
 * nach Ladeort: die Ortsmengen zählen alle Ladungen, Netz und Verlust nur die gemessenen, deshalb
 * nur ihr Verhältnis. Ein Ort ohne Strom ist kein Stück; ohne Aufteilung ein Stück "im Akku".
 */
export function flowShares(f: EnergyFlow): FlowShare[] {
  const loss = Math.min(100, Math.max(0, f.lossPercent))
  const split = f.privateKwh + f.publicKwh
  if (split <= 0) return [{ key: 'vehicle', pct: 100 - loss }, { key: 'loss', pct: loss }]
  const home = Math.round((100 - loss) * (f.privateKwh / split))
  const list: FlowShare[] = [{ key: 'home', pct: home }, { key: 'public', pct: 100 - loss - home }]
  return [...list.filter(x => x.pct > 0), { key: 'loss', pct: loss }]
}

export function buildEnergyFlow(stats: StatisticsData): EnergyFlow | null {
  const e = stats.chargingEfficiencySplit
  if (!e || e.coveredLogCount <= 0 || e.gridKwh <= 0) return null
  const lossKwh = Math.max(0, e.gridKwh - e.vehicleKwh)
  return {
    gridKwh: e.gridKwh,
    vehicleKwh: e.vehicleKwh,
    lossKwh,
    lossPercent: Math.round((lossKwh / e.gridKwh) * 100),
    acKwh: stats.chargingTypeSplit?.acKwh ?? 0,
    dcKwh: stats.chargingTypeSplit?.dcKwh ?? 0,
    privateKwh: stats.locationSplit?.privateKwh ?? 0,
    publicKwh: stats.locationSplit?.publicKwh ?? 0,
    distanceKm: stats.totalDistanceKm,
    coveredLogCount: e.coveredLogCount,
    totalLogCount: e.totalLogCount,
    partial: e.coveredLogCount < e.totalLogCount,
  }
}

/** Gleiche Fenster wie die RangeCard des alten Dashboards. */
export const RANGE_SOC_WINDOWS = [
  { socMax: 100, socMin: 0, recommended: false },
  { socMax: 90, socMin: 10, recommended: true },
  { socMax: 80, socMin: 20, recommended: false },
] as const

export interface RangeWindow {
  socMax: number
  socMin: number
  recommended: boolean
  km: number
}

/** Reichweite 90 -> 10 % mit einem Saisonverbrauch, den das Backend ueber alle Jahre aggregiert. */
export interface RangeSeason {
  consumption: number
  km: number
}

export interface RangeEstimate {
  /** Verbrauch der letzten 4 Wochen in kWh/100 km, Basis der Kachel. */
  consumption: number
  windows: RangeWindow[]
  /** Reichweite im empfohlenen Fenster. */
  km: number
  /** Mai bis August bzw. November bis Februar, alle Jahre. Null, solange das Backend keinen Wert hat. */
  summer: RangeSeason | null
  winter: RangeSeason | null
}

function kmFor(effectiveCapacityKwh: number, consumption: number, socMax: number, socMin: number): number {
  return Math.round((effectiveCapacityKwh * (socMax - socMin) / 100) / consumption * 100)
}

function seasonRow(effectiveCapacityKwh: number, consumption: number | null): RangeSeason | null {
  if (consumption == null || consumption <= 0) return null
  const w = RANGE_SOC_WINDOWS.find(x => x.recommended)!
  return { consumption, km: kmFor(effectiveCapacityKwh, consumption, w.socMax, w.socMin) }
}

/**
 * Kachelwert aus dem Verbrauch der uebergebenen Statistik (letzte 4 Wochen), Sommer und Winter
 * als eigene Zeilen fuer das Sheet. Kein Jahresmittel, keine Saisonzuordnung des Monats: ein
 * Monat mit zwei Ladungen hatte sonst 450 km ergeben. Null ohne Kapazitaet oder ohne Verbrauch.
 */
export function buildRange(stats: StatisticsData, effectiveCapacityKwh: number | null): RangeEstimate | null {
  if (effectiveCapacityKwh == null || effectiveCapacityKwh <= 0) return null
  const c = stats.avgConsumptionKwhPer100km
  if (c == null || c <= 0) return null
  const windows = RANGE_SOC_WINDOWS.map(w => ({ ...w, km: kmFor(effectiveCapacityKwh, c, w.socMax, w.socMin) }))
  return {
    consumption: c,
    windows,
    km: windows.find(w => w.recommended)!.km,
    summer: seasonRow(effectiveCapacityKwh, stats.summerConsumptionKwhPer100km),
    winter: seasonRow(effectiveCapacityKwh, stats.winterConsumptionKwhPer100km),
  }
}

export type CostLayerKind = 'energy' | 'fixed' | 'income' | 'net'

export interface CostLayer {
  kind: CostLayerKind
  /** Immer positiv; Einnahmen stellt die Komponente mit Minus dar. */
  eur: number
}

/** Null, wenn der Monat nur Stromkosten kennt: dann bleibt der Hero eine einzige Zahl. */
export function costLayers(stats: StatisticsData): CostLayer[] | null {
  if (!(stats.fixedCostEur > 0) && !(stats.fixedIncomeEur > 0)) return null
  const layers: CostLayer[] = [{ kind: 'energy', eur: stats.energyCostEur }]
  if (stats.fixedCostEur > 0) layers.push({ kind: 'fixed', eur: stats.fixedCostEur })
  if (stats.fixedIncomeEur > 0) layers.push({ kind: 'income', eur: stats.fixedIncomeEur })
  layers.push({ kind: 'net', eur: stats.totalCostEur })
  return layers
}
