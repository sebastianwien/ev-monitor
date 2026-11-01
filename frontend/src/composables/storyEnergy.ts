import type { RangeBasisSource, RangeEstimateDto, RangeScope, StatisticsData } from './useDashboardStats'
import type { TripMonth } from './storyInsights'

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
export type UseKey = 'drive' | 'standby' | 'charge'
/** Ein Stück im Verwendungs-Balken; pct null heißt gesperrt (Stand ohne Analytics). */
export interface UseShare { key: UseKey; pct: number | null }

/** Was energyUse von den Fahrten des Monats braucht. */
export type UseTrips = Pick<TripMonth, 'trips' | 'driveKwh' | 'standbyKwh' | 'standbyLocked' | 'standbyEur'>

/** Wo der Strom des Monats bleibt: Fahren, Stand, Laden. */
export interface EnergyUse {
  driveKwh: number
  /** Null, wenn der Stand gesperrt ist. */
  standbyKwh: number | null
  /** Ladeverlust aller Ladungen, gemessen oder hochgerechnet. */
  chargeLossKwh: number
  trips: number
  charges: number
  estimatedCharges: number
  /** Stand und Laden zusammen in Euro; null ohne Preis. */
  lossEur: number | null
  standbyEur: number | null
  chargeEur: number | null
  /** Zuhause geladen in ganzen Prozent; null ohne Ortsangabe. */
  homePct: number | null
  shares: UseShare[]
}

/** Ganze Prozent mit Summe 100, der Rundungsrest geht an die größten Reste. */
function percents(values: number[]): number[] {
  const total = values.reduce((a, b) => a + b, 0)
  if (total <= 0) return values.map(() => 0)
  const raw = values.map(v => (v / total) * 100)
  const out = raw.map(Math.floor)
  const order = raw.map((r, i) => ({ i, rest: r - out[i] })).sort((a, b) => b.rest - a.rest)
  const missing = 100 - out.reduce((a, b) => a + b, 0)
  for (let k = 0; k < missing; k++) out[order[k].i]++
  return out
}

/**
 * Fahren (Antrieb laut Fahrzeug), Stand (Energieabfall zwischen Fahrten) und Laden (Verlust aller
 * Ladungen) eines Monats. Null ohne Fahrten mit Energiedaten, dann bleibt der Strom-Balken wie bisher.
 * Euro: Stand wie in der Stand-Kachel, Laden zum Monatsschnitt je Netz-kWh. Gesperrter Stand zählt
 * weder in Anteile noch in Euro, damit er sich nicht zurückrechnen lässt.
 */
export function energyUse(stats: StatisticsData, tm: UseTrips | null): EnergyUse | null {
  if (!tm || tm.driveKwh == null || tm.driveKwh <= 0) return null
  const e = stats.chargingEfficiencySplit
  // Älteres Backend ohne Hochrechnung (kurz im Blue/Green-Deploy): nur die gemessenen Ladungen
  const projected = e?.lossKwh != null
  const chargeLossKwh = !e ? 0 : projected ? e.lossKwh! : Math.max(0, e.gridKwh - e.vehicleKwh)
  const price = stats.avgCostPerKwh != null && stats.avgCostPerKwh > 0 ? stats.avgCostPerKwh : null
  const standbyKwh = tm.standbyLocked ? null : tm.standbyKwh ?? 0
  const standbyEur = standbyKwh == null ? null : tm.standbyEur ?? (price == null ? null : standbyKwh * price)
  const chargeEur = price == null ? null : chargeLossKwh * price
  const lossEur = standbyEur == null && chargeEur == null ? null : (standbyEur ?? 0) + (chargeEur ?? 0)

  let drive: number, standby: number | null, charge: number
  if (standbyKwh == null) {
    [drive, charge] = percents([tm.driveKwh, chargeLossKwh])
    standby = null
  } else {
    [drive, standby, charge] = percents([tm.driveKwh, standbyKwh, chargeLossKwh])
  }
  const shares: UseShare[] = ([['drive', drive], ['standby', standby], ['charge', charge]] as [UseKey, number | null][])
    .filter(([, pct]) => pct !== 0)
    .map(([key, pct]) => ({ key, pct }))

  const loc = stats.locationSplit
  const located = loc ? loc.privateKwh + loc.publicKwh : 0
  return {
    driveKwh: tm.driveKwh,
    standbyKwh,
    chargeLossKwh,
    trips: tm.trips,
    charges: (projected ? e?.totalLogCount : e?.coveredLogCount) ?? 0,
    estimatedCharges: e?.estimatedLossLogCount ?? 0,
    lossEur,
    standbyEur,
    chargeEur,
    homePct: located > 0 ? Math.round((loc!.privateKwh / located) * 100) : null,
    shares,
  }
}

/** Fenster der Reichweite überall: pro Ladung von 100 auf 10 %. Die km rechnet das Backend (RangeMath). */
export const RANGE_SOC_MAX = 100
export const RANGE_SOC_MIN = 10

/** Eine Zeile der Reichweite (4 Wochen, Sommer, Winter): km, Verbrauch und woher er kommt. */
export interface RangeSeason {
  km: number
  consumption: number
  source: RangeBasisSource
  scope: RangeScope | null
}

export interface RangeEstimate extends RangeSeason {
  /** Mai bis August bzw. November bis Februar, alle Jahre. Null, solange das Backend keinen Wert hat. */
  summer: RangeSeason | null
  winter: RangeSeason | null
}

function seasonRow(dto: RangeEstimateDto | null | undefined): RangeSeason | null {
  if (dto == null || dto.rangeKm == null || dto.consumptionKwhPer100km <= 0) return null
  return { km: dto.rangeKm, consumption: dto.consumptionKwhPer100km, source: dto.source, scope: dto.scope }
}

/** Verbrauch, mit dem die Reichweite der letzten 4 Wochen rechnet. Null ohne Wert. */
export function rangeConsumption(stats: StatisticsData): number | null {
  return stats.range?.current?.consumptionKwhPer100km ?? null
}

/** Reichweite der letzten 4 Wochen in km, Basis der Zeile "Heute geladen". Null ohne Wert. */
export function currentRangeKm(stats: StatisticsData): number | null {
  return stats.range?.current?.rangeKm ?? null
}

/**
 * Reichweite nach einer Ladung bis {@code socTo} %: der Anteil des Fensters 100 → 10 % an der Reichweite,
 * auf 10 km gerundet. Keine eigene Verbrauchsformel, nur die Skalierung des Backend-Werts.
 */
export function rangeAtSoc(rangeKm: number | null, socTo: number | null): number | null {
  if (rangeKm == null || rangeKm <= 0 || socTo == null || socTo <= RANGE_SOC_MIN) return null
  return Math.round(rangeKm * (Math.min(socTo, RANGE_SOC_MAX) - RANGE_SOC_MIN) / (RANGE_SOC_MAX - RANGE_SOC_MIN) / 10) * 10
}

/** Herkunft einer Zeile in einem Wort für Kachel und Sheet, je Stufe und bei anderen Fahrern je Variante oder Modell. */
export function rangeOriginKey(row: Pick<RangeSeason, 'source' | 'scope'>): string {
  switch (row.source) {
    case 'OWN_DRIVE': return 'story.range_origin_drive'
    case 'PEER_DRIVE': return row.scope === 'VARIANT' ? 'story.range_origin_peer_variant' : 'story.range_origin_peer_model'
    case 'OWN_CHARGES': return 'story.range_origin_charges'
    case 'PEER_CHARGES': return 'story.range_origin_peer_charges'
    case 'WLTP_ESTIMATE': return 'story.range_origin_wltp'
  }
}

/** Unterzeile der Kachel ("Ø 4 Wochen, Fahrten ab 20 km") und erste Zeile der Verbrauchstabelle im Sheet. */
export function rangeBasisLabel(row: Pick<RangeSeason, 'source' | 'scope'> | undefined, model: string,
  t: (key: string, params: Record<string, string>) => string): string {
  if (!row) return ''
  const keys: Record<RangeBasisSource, string> = {
    OWN_DRIVE: 'story.range_basis_drive',
    PEER_DRIVE: row.scope === 'VARIANT' ? 'story.range_basis_peer_variant' : 'story.range_basis_peer_model',
    OWN_CHARGES: 'story.range_basis_charges',
    PEER_CHARGES: 'story.range_basis_peer_charges',
    WLTP_ESTIMATE: 'story.range_basis_wltp',
  }
  return t(keys[row.source], { model })
}

/**
 * Kachelwert der letzten 4 Wochen, Sommer und Winter als eigene Zeilen für das Sheet, alles fertig vom
 * Backend: welcher Verbrauch zählt (eigene Fahrten, andere Fahrer, Ladungen, Schätzung) und die km auf
 * der eigenen Kapazität. Null ohne Wert für die 4 Wochen.
 */
export function buildRange(stats: StatisticsData): RangeEstimate | null {
  const current = seasonRow(stats.range?.current)
  if (current == null) return null
  return { ...current, summer: seasonRow(stats.range?.summer), winter: seasonRow(stats.range?.winter) }
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

/** Strom, Fixkosten und Einnahmen eines Monats für die gestapelten Balken im Kosten-Sheet. */
export interface MonthCosts {
  energyEur: number
  fixedEur: number
  /** Positiv ausgewiesen, steht nicht im Balken. */
  incomeEur: number
}

/** Monatsstatistik auf ihre Kostenteile; ein fehlender Monat zählt als 0. */
export function monthCosts(stats: StatisticsData | null): MonthCosts {
  return { energyEur: stats?.energyCostEur ?? 0, fixedEur: stats?.fixedCostEur ?? 0, incomeEur: stats?.fixedIncomeEur ?? 0 }
}

/** Stapel je Monat: Strom unten, Fixkosten oben. Einnahmen bleiben draußen, sie stehen als Satz darunter. */
export function costStack(months: MonthCosts[]): { energy: number[]; fixed: number[] } {
  return { energy: months.map(m => m.energyEur), fixed: months.map(m => m.fixedEur) }
}
