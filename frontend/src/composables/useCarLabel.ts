import type { Car } from '../api/carService'
import { carDisplayName } from '../utils/enumLabel'

/** Fahrzeugname plus Ausstattung und Kennzeichen, z. B. "VW ID.5 GTX · B-EV 1". */
export function carLabel(car: Pick<Car, 'displayName' | 'trim' | 'licensePlate'>): string {
  const base = carDisplayName(car)
  const name = car.trim ? `${base} ${car.trim}` : base
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}
