/**
 * Richtwert für Verbrauch und Kosten je 100 km im Erfassungs-Wizard, bevor der Log gespeichert ist.
 *
 * ACHTUNG: Das ist bewusst die einzige Verbrauchsformel außerhalb von EvLogService. Sebastian hat
 * sie am 01.10.2026 ausdrücklich nur als unverbindliche Vorschau erlaubt. Die gespeicherte Zahl
 * kommt immer vom Backend (ConsumptionCalculationService.calculateConsumptionPerLogDetailed),
 * diese Datei spiegelt dessen Kernregel und muss bei Änderungen dort nachgezogen werden.
 *
 * ABWEICHUNG (Entscheidung Sebastian, 01. und 02.10.2026, "brutto"): Die Vorschau zeigt den Energiebezug
 * ab Säule. Brutto hat Vorrang und zählt ohne Wirkungsgrad. Nur wenn allein Netto (laut Auto) vorliegt,
 * wird auf brutto hochgerechnet (AC 0,90 / DC 0,95) und der Wert als Schätzung markiert ("~").
 * Das Backend bleibt unverändert: es rechnet netto (kwhAtVehicle, sonst kwhCharged × Wirkungsgrad), der
 * gespeicherte Wert liegt deshalb rund 5 bis 10 % unter der Vorschau.
 *
 *   energie  = (kwhCharged, sonst kwhAtVehicle / Wirkungsgrad) + (SoC nach Vorgänger - SoC nach dieser Ladung) / 100 × SoH-Kapazität
 *   distanz  = Tacho jetzt - Tacho des letzten Logs mit Tacho, mindestens plausibility.min-trip-distance-km
 *   kWh/100  = energie / distanz × 100, plausibel zwischen absolute-min und absolute-max
 *
 * Nicht gespiegelt (zu viel Kontext für eine Vorschau): fahrzeugspezifische Wirkungsgrade aus der
 * Spezifikation, Zwischen-Logs ohne Tacho, statistische und WLTP-Plausibilität.
 */
/** Wirkungsgrade wie im Backend - nur für die Hochrechnung Netto auf Brutto */
export const AC_CHARGING_EFFICIENCY = 0.90
export const DC_CHARGING_EFFICIENCY = 0.95
export const MIN_TRIP_DISTANCE_KM = 10
export const ABSOLUTE_MIN_KWH_PER_100KM = 10
export const ABSOLUTE_MAX_KWH_PER_100KM = 40

export interface PreviousLogRef { odometerKm: number; socAfter: number | null }
export interface PreviewInput {
  kwhCharged: number | null
  kwhAtVehicle: number | null
  chargingType: 'AC' | 'DC'
  odometerKm: number | null
  socAfter: number | null
  capacityKwh: number | null | undefined
  costEur: number | null
  previous: PreviousLogRef | null
}
/** estimated: kWh/100 km aus Netto hochgerechnet (Anzeige mit "~"); Euro je 100 km sind immer exakt */
export interface ConsumptionPreview { kwhPer100km: number; eurPer100km: number | null; distanceKm: number; plausible: boolean; estimated: boolean }

export function consumptionPreview(i: PreviewInput): ConsumptionPreview | null {
  if (!i.previous || i.odometerKm == null) return null
  const distanceKm = i.odometerKm - i.previous.odometerKm
  if (distanceKm < MIN_TRIP_DISTANCE_KM) return null
  const gross = i.kwhCharged != null && i.kwhCharged > 0 ? i.kwhCharged : null
  const net = i.kwhAtVehicle != null && i.kwhAtVehicle > 0 ? i.kwhAtVehicle : null
  const estimated = gross == null && net != null
  const energy = gross ?? (net != null ? net / (i.chargingType === 'DC' ? DC_CHARGING_EFFICIENCY : AC_CHARGING_EFFICIENCY) : null)
  if (energy == null) return null
  const socCorrection = i.socAfter != null && i.previous.socAfter != null && i.capacityKwh
    ? (i.previous.socAfter - i.socAfter) / 100 * i.capacityKwh : 0
  const kwhPer100km = (energy + socCorrection) / distanceKm * 100
  if (kwhPer100km <= 0) return null
  return {
    kwhPer100km, distanceKm, estimated,
    eurPer100km: i.costEur != null ? i.costEur / distanceKm * 100 : null,
    plausible: kwhPer100km >= ABSOLUTE_MIN_KWH_PER_100KM && kwhPer100km <= ABSOLUTE_MAX_KWH_PER_100KM,
  }
}
