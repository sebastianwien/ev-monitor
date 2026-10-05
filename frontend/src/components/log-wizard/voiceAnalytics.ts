import type { LogFormData } from '../log-form/logFormData'
import type { VoiceDraft, VoiceDraftFields } from './wizardLogic'
import { MAX_RECORDING_MS } from '../../composables/useVoiceRecorder'

/**
 * Messpunkte des Sprachlogs für Plausible. Nur Zählwerte und Feldnamen, nie Werte, Transkript
 * oder Orte. Die Kernfrage dahinter: Welche Felder muss der Nutzer nach der Sprache noch
 * korrigieren? Daran wird der Prompt nachgeschärft.
 */
export type VoiceEntry = 'create' | 'edit'

/** Aufnahmelänge grob; der automatische Stopp bei 60 s heißt "limit" (Satz evtl. abgeschnitten). */
export function durationBucket(ms: number): '<10s' | '10-30s' | '30-60s' | 'limit' {
  if (ms >= MAX_RECORDING_MS - 500) return 'limit'
  if (ms < 10_000) return '<10s'
  if (ms < 30_000) return '10-30s'
  return '30-60s'
}

/** Wartezeit vom Ende der Aufnahme bis zum Entwurf. */
export function latencyBucket(ms: number): '<3s' | '3-6s' | '6-10s' | '>10s' {
  if (ms < 3_000) return '<3s'
  if (ms < 6_000) return '3-6s'
  if (ms < 10_000) return '6-10s'
  return '>10s'
}

/** Gesprochene Felder, wie sie im Formular landen; Kosten und Preis je kWh enden beide in costEur. */
const FORM_KEY = {
  kwhCharged: 'kwhCharged', kwhAtVehicle: 'kwhAtVehicle', socBefore: 'socBeforeChargePercent', socAfter: 'socAfterChargePercent',
  odometerKm: 'odometerKm', costEur: 'costEur', loggedAt: 'loggedAt', chargeDurationMinutes: 'chargeDurationMinutes',
  maxChargingPowerKw: 'maxChargingPowerKw', chargingType: 'chargingType', routeType: 'routeType', tireType: 'tireType',
} as const satisfies Record<string, keyof LogFormData>
export type VoiceField = keyof typeof FORM_KEY

const VALUE_KEYS = ['kwhCharged', 'kwhAtVehicle', 'socBefore', 'socAfter', 'odometerKm', 'costEur', 'pricePerKwh', 'loggedAt',
  'chargeDurationMinutes', 'maxChargingPowerKw', 'chargingType', 'routeType', 'tireType'] as const

/** Wie viele Angaben gesprochen wurden, Ort zählt einmal - wie filledCount im Backend. */
export function filledCount(f: VoiceDraftFields): number {
  const raw = f as unknown as Record<string, unknown>
  const place = raw.placeIndex != null || raw.placeKind != null || f.spokenAddress != null
  return (place ? 1 : 0) + (raw.tariffIndex != null ? 1 : 0) + VALUE_KEYS.filter(k => f[k] != null).length
}

/** Wie der Ort zustande kam: Treffer aus der Liste (inkl. privat), Adresse, freier Betreiber oder gar nicht. */
export function placeOutcome(d: VoiceDraft): 'match' | 'address' | 'other' | 'none' {
  if (d.place && d.place.kind !== 'other') return 'match'
  if (d.fields.spokenAddress) return 'address'
  return d.place ? 'other' : 'none'
}

/** Was die Sprache ins Formular geschrieben hat - Stand direkt nach dem Übernehmen. */
export type SpokenSnapshot = Partial<Record<VoiceField, unknown>>

export function spokenSnapshot(form: LogFormData, f: VoiceDraftFields): SpokenSnapshot {
  const snap: SpokenSnapshot = {}
  for (const key of Object.keys(FORM_KEY) as VoiceField[]) {
    const spoken = key === 'costEur' ? f.costEur ?? f.pricePerKwh : f[key]
    if (spoken != null) snap[key] = form[FORM_KEY[key]]
  }
  return snap
}

const same = (a: unknown, b: unknown) =>
  typeof a === 'number' && typeof b === 'number' ? Math.abs(a - b) < 0.005 : a === b

/** Gesprochene Felder, die der Nutzer vor dem Speichern geändert hat. */
export function correctedFields(snap: SpokenSnapshot, form: LogFormData): VoiceField[] {
  return (Object.keys(snap) as VoiceField[]).filter(k => !same(snap[k], form[FORM_KEY[k]]))
}
