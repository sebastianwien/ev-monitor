import type { LogFormData } from '../log-form/logFormData'
import type { NearbyStation } from '../../composables/useNearbyStations'
import type { RecentSite } from '../../composables/useRecentSites'
import { datetimeLocalToUtcIso } from '../../utils/datetime'

export type WizardStep = 1 | 2 | 3 | 4 | 5
export const LAST_STEP: WizardStep = 5
/** Schritte mit Eingabe; Schritt 5 ist die Prüfseite und zählt in der Anzeige nicht mit. */
export const INPUT_STEPS = 4

export type PlaceKind = 'home' | 'station' | 'site' | 'other'

export type PlaceChoice =
  | { kind: 'home' }
  | { kind: 'station'; station: NearbyStation }
  | { kind: 'site'; site: RecentSite }
  | { kind: 'other'; cpoName: string | null }

export interface WizardState { place: PlaceKind | null }

export function emptyLogForm(): LogFormData {
  return {
    kwhCharged: null, costEur: null, costExchangeRate: null, costCurrency: null,
    odometerKm: null, socAfterChargePercent: null, socBeforeChargePercent: null,
    kwhAtVehicle: null, chargeDurationMinutes: null, maxChargingPowerKw: null,
    loggedAt: null, chargingType: 'AC', routeType: 'COMBINED', tireType: 'SUMMER',
    latitude: null, longitude: null, isPublicCharging: false, cpoName: null,
    chargingProviderId: null, chargingSite: null, applyTariffToLocation: false,
  }
}

const positive = (v: number | null | undefined) => v != null && v > 0

/** Pflicht je Schritt: Ort, Energie, Tacho + SoC danach, Kosten. Schritt 5 prüft nur. */
export function canProceed(step: WizardStep, f: LogFormData, state: WizardState): boolean {
  switch (step) {
    case 1: return state.place !== null
    case 2: return positive(f.kwhCharged) || positive(f.kwhAtVehicle)
    case 3: return positive(f.odometerKm) && f.socAfterChargePercent != null
    case 4: return f.costEur != null
    default: return true
  }
}

/** Die Ortswahl setzt öffentlich/privat, Anbieter und Ladeart in einem Schritt. */
export function applyPlace(f: LogFormData, choice: PlaceChoice): void {
  switch (choice.kind) {
    case 'home':
      f.isPublicCharging = false; f.chargingType = 'AC'; f.cpoName = null; f.chargingSite = null
      break
    case 'station':
      f.isPublicCharging = true
      f.chargingType = choice.station.fastCharging ? 'DC' : 'AC'
      f.cpoName = choice.station.name
      f.chargingSite = { name: choice.station.name, geohash: choice.station.geohash }
      break
    case 'site':
      f.isPublicCharging = true
      f.chargingType = choice.site.fastCharging ? 'DC' : 'AC'
      f.cpoName = choice.site.cpoName ?? choice.site.name
      f.chargingSite = { name: choice.site.name, geohash: choice.site.geohash }
      break
    case 'other':
      f.isPublicCharging = true; f.cpoName = choice.cpoName; f.chargingSite = null
      break
  }
}

const round2 = (n: number) => Math.round(n * 100) / 100

/** Der Request-Body für POST /logs - identisch für Wizard und klassisches Formular. */
export function buildLogPayload(f: LogFormData, carId: string, ocrUsed: boolean): Record<string, unknown> {
  const payload: Record<string, unknown> = {
    carId,
    costEur: round2(f.costEur ?? 0),
    odometerKm: f.odometerKm,
    socAfterChargePercent: f.socAfterChargePercent,
    chargingType: f.chargingType,
    routeType: f.routeType,
    tireType: f.tireType,
    isPublicCharging: f.isPublicCharging,
  }
  if (positive(f.kwhCharged)) payload.kwhCharged = round2(f.kwhCharged!)
  if (positive(f.kwhAtVehicle)) payload.kwhAtVehicle = round2(f.kwhAtVehicle!)
  if (f.socBeforeChargePercent != null) payload.socBeforeChargePercent = f.socBeforeChargePercent
  if (f.chargeDurationMinutes) payload.chargeDurationMinutes = f.chargeDurationMinutes
  if (f.latitude != null && f.longitude != null) { payload.latitude = f.latitude; payload.longitude = f.longitude }
  if (f.maxChargingPowerKw != null) payload.maxChargingPowerKw = round2(f.maxChargingPowerKw)
  if (f.loggedAt) payload.loggedAt = datetimeLocalToUtcIso(f.loggedAt)
  if (ocrUsed) payload.ocrUsed = true
  if (f.isPublicCharging && f.cpoName) payload.cpoName = f.cpoName
  if (f.chargingProviderId) payload.chargingProviderId = f.chargingProviderId
  if (f.isPublicCharging && f.chargingSite) payload.chargingSite = f.chargingSite
  if (f.costExchangeRate != null) payload.costExchangeRate = f.costExchangeRate
  if (f.costCurrency != null) payload.costCurrency = f.costCurrency
  return payload
}

/** Der Request-Body für PATCH /logs/{id}: wie die Anlage, nur ohne Auto und OCR-Marker. */
export function buildLogUpdatePayload(f: LogFormData): Record<string, unknown> {
  const { carId: _carId, ocrUsed: _ocr, ...rest } = buildLogPayload(f, '', false)
  return rest
}

export type RequiredField = 'energy' | 'odometer' | 'soc' | 'cost'

/** Welche Pflichtwerte einem (älteren) Log fehlen - für den Block "Noch offen" beim Bearbeiten. */
export function missingRequired(f: LogFormData): RequiredField[] {
  const missing: RequiredField[] = []
  if (!positive(f.kwhCharged) && !positive(f.kwhAtVehicle)) missing.push('energy')
  if (!positive(f.odometerKm)) missing.push('odometer')
  if (f.socAfterChargePercent == null) missing.push('soc')
  if (f.costEur == null) missing.push('cost')
  return missing
}

/** kWh im Akku aus SoC und SoH-bereinigter Kapazität (siehe CLAUDE.md, Batteriekapazität). */
export function socToKwh(socPercent: number | null, effectiveCapacityKwh: number | null | undefined): number | null {
  if (socPercent == null || !effectiveCapacityKwh) return null
  return (socPercent / 100) * effectiveCapacityKwh
}

/** Netto geladene Energie aus SoC vorher/danach; ein Rückgang ergibt 0, nicht einen negativen Wert. */
export function netEnergyKwh(socBefore: number | null, socAfter: number | null,
                             effectiveCapacityKwh: number | null | undefined): number | null {
  if (socBefore == null || socAfter == null || !effectiveCapacityKwh) return null
  return Math.max(0, ((socAfter - socBefore) / 100) * effectiveCapacityKwh)
}

export type OptionalFactKind = 'time' | 'socBefore' | 'route' | 'tires' | 'duration' | 'peak'
export interface OptionalFact { kind: OptionalFactKind; value: string | number | null }

/**
 * Die optionalen Angaben, die gerade einen Wert tragen, als flache Liste für die
 * Schnellkontrolle über dem zugeklappten "Mehr Details". Zeit gehört dazu, sobald sie
 * nicht als eigene Kachel steht (null = jetzt); Strecke und Reifen sind immer vorbelegt.
 */
export function optionalFacts(f: LogFormData, opts: { withTime?: boolean } = {}): OptionalFact[] {
  const facts: OptionalFact[] = []
  if (opts.withTime !== false) facts.push({ kind: 'time', value: f.loggedAt })
  if (f.socBeforeChargePercent != null) facts.push({ kind: 'socBefore', value: f.socBeforeChargePercent })
  facts.push({ kind: 'route', value: f.routeType }, { kind: 'tires', value: f.tireType })
  if (positive(f.chargeDurationMinutes)) facts.push({ kind: 'duration', value: f.chargeDurationMinutes })
  if (positive(f.maxChargingPowerKw)) facts.push({ kind: 'peak', value: f.maxChargingPowerKw })
  return facts
}
