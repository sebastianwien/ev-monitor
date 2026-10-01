/**
 * Richtwert für Verbrauch und Kosten je 100 km im Erfassungs-Wizard, bevor der Log gespeichert ist.
 *
 * ACHTUNG: Das ist bewusst die einzige Verbrauchsformel außerhalb von EvLogService. Sebastian hat
 * sie am 01.10.2026 ausdrücklich nur als unverbindliche Vorschau erlaubt. Die gespeicherte Zahl
 * kommt immer vom Backend (ConsumptionCalculationService.calculateConsumptionPerLogDetailed),
 * diese Datei spiegelt dessen Kernregel und muss bei Änderungen dort nachgezogen werden.
 *
 * ABWEICHUNG (Entscheidung Sebastian, 01.10.2026, "brutto"): Die Vorschau rechnet ohne Ladewirkungsgrad,
 * also mit den kWh ab Säule - der Richtwert soll den echten Energiebezug zeigen. Das Backend wendet
 * beim Speichern weiterhin AC 0,90 / DC 0,95 an, der gespeicherte Wert liegt deshalb rund 5 bis 10 %
 * unter der Vorschau, solange das Backend nicht nachgezogen ist (nur mit ausdrücklicher Freigabe).
 *
 *   energie  = (kwhAtVehicle, sonst kwhCharged) + (SoC nach Vorgänger - SoC nach dieser Ladung) / 100 × SoH-Kapazität
 *   distanz  = Tacho jetzt - Tacho des letzten Logs mit Tacho, mindestens plausibility.min-trip-distance-km
 *   kWh/100  = energie / distanz × 100, plausibel zwischen absolute-min und absolute-max
 *
 * Nicht gespiegelt (zu viel Kontext für eine Vorschau): fahrzeugspezifische Wirkungsgrade aus der
 * Spezifikation, Zwischen-Logs ohne Tacho, statistische und WLTP-Plausibilität.
 */
/** Wirkungsgrade wie im Backend - hier nur noch für den SoC-Startpunkt (StepVehicle), nicht für den Richtwert */
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
export interface ConsumptionPreview { kwhPer100km: number; eurPer100km: number | null; distanceKm: number; plausible: boolean }

export function consumptionPreview(i: PreviewInput): ConsumptionPreview | null {
  if (!i.previous || i.odometerKm == null) return null
  const distanceKm = i.odometerKm - i.previous.odometerKm
  if (distanceKm < MIN_TRIP_DISTANCE_KM) return null
  const net = i.kwhAtVehicle != null && i.kwhAtVehicle > 0 ? i.kwhAtVehicle
    : i.kwhCharged != null && i.kwhCharged > 0 ? i.kwhCharged : null
  if (net == null) return null
  const socCorrection = i.socAfter != null && i.previous.socAfter != null && i.capacityKwh
    ? (i.previous.socAfter - i.socAfter) / 100 * i.capacityKwh : 0
  const kwhPer100km = (net + socCorrection) / distanceKm * 100
  if (kwhPer100km <= 0) return null
  return {
    kwhPer100km, distanceKm,
    eurPer100km: i.costEur != null ? i.costEur / distanceKm * 100 : null,
    plausible: kwhPer100km >= ABSOLUTE_MIN_KWH_PER_100KM && kwhPer100km <= ABSOLUTE_MAX_KWH_PER_100KM,
  }
}
