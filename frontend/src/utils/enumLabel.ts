import type { Car } from '../api/carService'

/**
 * Fahrzeugname fuer jede Anzeige. Kommt fertig aus dem Backend (`CarModel.getFullDisplayName()`,
 * z. B. "VW ID.5", "Polestar 2"). Nie aus brand/model-Enums formatieren: daraus wurde "Vw Id 5".
 */
export function carDisplayName(car: Pick<Car, 'displayName'> | null | undefined): string {
  return car?.displayName ?? ''
}
