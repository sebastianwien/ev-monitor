import type { LogFormData } from '../log-form/logFormData'
import type { StationMatch } from '../../composables/useNearbyStations'
import { datetimeLocalToUtcIso } from '../../utils/datetime'

/** Ort, Zahlen (Energie, Tacho, SoC, Kosten), Prüfen - drei Schritte, drei Taps, drei Zahlen. */
export type WizardStep = 1 | 2 | 3
export const LAST_STEP: WizardStep = 3
/** Schritte mit Eingabe; Schritt 3 ist die Prüfseite und zählt in der Anzeige nicht mit. */
export const INPUT_STEPS = 2

export type PlaceKind = 'home' | 'station' | 'site' | 'other'

/** Ein gespeicherter Ladestandort, an dem der Nutzer schon geladen hat (kommt aus der Sprachaufnahme). */
export interface RecentSite {
  id: string
  name: string
  cpoName: string | null
  geohash: string
  maxAcKw: number | null
  maxDcKw: number | null
  chargePoints: number
  fastCharging: boolean
  address: string | null
  plugTypes: string[]
  lastUsedAt: string
  usageCount: number
}

export type PlaceChoice =
  | { kind: 'home' }
  /** viaSearch: aus der Textsuche gewählt - der Wizard springt dann nicht automatisch weiter */
  | { kind: 'station'; station: StationMatch; viaSearch?: boolean }
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

/** Pflicht je Schritt: Ort; dann Energie, Tacho, SoC danach und Kosten zusammen. Schritt 3 prüft nur. */
export function canProceed(step: WizardStep, f: LogFormData, state: WizardState): boolean {
  switch (step) {
    case 1: return state.place !== null
    case 2: return missingRequired(f).length === 0
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

// ── Sprachlog ─────────────────────────────────────────────────────────────────

/** Felder aus POST /logs/voice-draft; null = nicht gesagt. Kosten in Euro. */
export interface VoiceDraftFields {
  kwhCharged: number | null
  kwhAtVehicle: number | null
  socBefore: number | null
  socAfter: number | null
  odometerKm: number | null
  costEur: number | null
  pricePerKwh: number | null
  loggedAt: string | null
  chargeDurationMinutes: number | null
  maxChargingPowerKw: number | null
  chargingType: 'AC' | 'DC' | null
  routeType: LogFormData['routeType'] | null
  tireType: LogFormData['tireType'] | null
  /** Feldnamen, bei denen das Modell unsicher war - werden markiert, nicht verworfen */
  uncertain: string[]
}

/** Der erkannte Ort in der Form der PlaceChoice: station wie die Umkreissuche, site wie die zuletzt genutzten Standorte. */
export interface VoicePlace {
  kind: PlaceKind
  station: StationMatch | null
  site: RecentSite | null
  cpoName: string | null
}

export interface VoiceUsage { limit: number | null; remaining: number | null; resetsOn: string }

export interface VoiceDraft {
  transcript: string
  fields: VoiceDraftFields
  place: VoicePlace | null
  chargingProviderId: string | null
  usage: VoiceUsage
}

const toPlaceChoice = (p: VoicePlace): PlaceChoice | null => {
  switch (p.kind) {
    case 'home': return { kind: 'home' }
    case 'station': return p.station ? { kind: 'station', station: p.station } : null
    case 'site': return p.site ? { kind: 'site', site: p.site } : null
    case 'other': return { kind: 'other', cpoName: p.cpoName }
  }
}

/**
 * Eine Sprachaufnahme ins Formular übernehmen: Ort (samt Ladekarte) und alle gesagten Werte.
 * Ungesagtes (null) überschreibt nichts. Kosten bleiben draußen, die laufen über die
 * Kosteneingabe des Wizards (siehe voiceCost), sonst setzt deren Abgleich sie zurück.
 */
export function applyVoiceDraft(f: LogFormData, draft: VoiceDraft): PlaceChoice | null {
  const choice = draft.place ? toPlaceChoice(draft.place) : null
  if (choice) applyPlace(f, choice)
  if (choice || draft.chargingProviderId) f.chargingProviderId = draft.chargingProviderId
  const v = draft.fields
  const set = <K extends keyof LogFormData>(key: K, value: LogFormData[K] | null) => { if (value != null) f[key] = value }
  set('kwhCharged', v.kwhCharged)
  set('kwhAtVehicle', v.kwhAtVehicle)
  set('socBeforeChargePercent', v.socBefore)
  set('socAfterChargePercent', v.socAfter)
  set('odometerKm', v.odometerKm)
  set('loggedAt', v.loggedAt)
  set('chargeDurationMinutes', v.chargeDurationMinutes)
  set('maxChargingPowerKw', v.maxChargingPowerKw)
  set('chargingType', v.chargingType)
  set('routeType', v.routeType)
  set('tireType', v.tireType)
  return choice
}

export type VoiceCost = { mode: 'total' | 'per_kwh'; eur: number }

/** Gesagte Kosten für die Kosteneingabe: Gesamtbetrag vor Preis je kWh. */
export function voiceCost(v: Pick<VoiceDraftFields, 'costEur' | 'pricePerKwh'>): VoiceCost | null {
  if (v.costEur != null) return { mode: 'total', eur: v.costEur }
  if (v.pricePerKwh != null) return { mode: 'per_kwh', eur: v.pricePerKwh }
  return null
}

/**
 * Nach der Sprachaufnahme direkt auf die Prüfseite - nur wenn nichts fehlt. Die Prüfseite selbst
 * blockiert nie (canProceed), der lineare Weg sichert die Pflichtwerte über Schritt 2 ab.
 */
export function canJumpToReview(f: LogFormData, state: WizardState): boolean {
  return state.place !== null && missingRequired(f).length === 0
}

export type VoiceFlag = 'place' | RequiredField | 'time' | 'details'
const VOICE_FLAG_OF: Record<string, VoiceFlag> = {
  placeIndex: 'place', placeKind: 'place', spokenOperator: 'place',
  kwhCharged: 'energy', kwhAtVehicle: 'energy', odometerKm: 'odometer', socAfter: 'soc',
  costEur: 'cost', pricePerKwh: 'cost', tariffIndex: 'cost', loggedAt: 'time',
}
const VOICE_FLAG_ORDER: VoiceFlag[] = ['place', 'energy', 'odometer', 'soc', 'cost', 'time', 'details']

/** Unsichere Felder als Kacheln der Prüfseite; was keine eigene Kachel hat, steht unter "details". */
export function voiceFlags(uncertain: string[]): VoiceFlag[] {
  const flags = new Set(uncertain.map(u => VOICE_FLAG_OF[u] ?? 'details'))
  return VOICE_FLAG_ORDER.filter(f => flags.has(f))
}
