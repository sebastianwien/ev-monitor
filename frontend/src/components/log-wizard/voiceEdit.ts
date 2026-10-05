import type { LogFormData } from '../log-form/logFormData'

/** Felder, die eine Sprachaufnahme beim Bearbeiten ändern kann, in Anzeige-Reihenfolge. */
export const EDIT_DIFF_KEYS = ['kwhCharged', 'kwhAtVehicle', 'socBeforeChargePercent', 'socAfterChargePercent', 'odometerKm',
  'costEur', 'loggedAt', 'chargeDurationMinutes', 'maxChargingPowerKw', 'chargingType', 'routeType', 'tireType'] as const
export type EditDiffKey = typeof EDIT_DIFF_KEYS[number] | 'place'
export interface FieldChange { key: EditDiffKey; from: unknown; to: unknown }

const same = (a: unknown, b: unknown) =>
  typeof a === 'number' && typeof b === 'number' ? Math.abs(a - b) < 0.005 : (a ?? null) === (b ?? null)
const placeOf = (f: LogFormData) => ({ isPublic: !!f.isPublicCharging, name: f.chargingSite?.name ?? f.cpoName ?? null, lat: f.latitude, lon: f.longitude })

/**
 * Was eine Sprachaufnahme im Bearbeiten-Formular geändert hat - als Vorher/Nachher für die
 * Bestätigung. Ort zählt als ein Eintrag (privat/öffentlich, Betreiber oder neue Position).
 */
export function formChanges(before: LogFormData, after: LogFormData): FieldChange[] {
  const out: FieldChange[] = []
  const pb = placeOf(before), pa = placeOf(after)
  if (pb.isPublic !== pa.isPublic || pb.name !== pa.name || !same(pb.lat, pa.lat) || !same(pb.lon, pa.lon)) {
    out.push({ key: 'place', from: pb, to: pa })
  }
  for (const key of EDIT_DIFF_KEYS) if (!same(before[key], after[key])) out.push({ key, from: before[key], to: after[key] })
  return out
}
