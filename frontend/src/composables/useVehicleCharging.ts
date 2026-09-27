import type { Ref } from 'vue'
import { useWallboxStore } from '../stores/wallbox'
import { connectionsOf, type SmartcarConnectionStatus } from '../api/smartcarService'

/** Minimal-Shape, das die Charging-Predicates brauchen. */
export interface ChargingCar {
  id: string
  brand?: string | null
}

/**
 * Kapselt die Frage "laedt dieses Fahrzeug gerade?" fuer die verschiedenen
 * Provider (Smartcar, Wallbox). Einzige Quelle fuer Dashboard-
 * und Log-Feed-Auto-Cards, damit die Glow-Logik nicht dupliziert wird.
 *
 * Wallbox kennt keine carId -> eine laufende Wallbox-Ladung ist nur bei
 * Single-Car-Accounts sicher einem Fahrzeug zuordenbar.
 */
export function useVehicleCharging(
  cars: Ref<ChargingCar[]>,
  smartcarStatus: Ref<SmartcarConnectionStatus | null>,
) {
  const wallboxStore = useWallboxStore()

  // Mehrere Verbindungen je Nutzer (AutoSync-Slots): die Verbindung dieses Autos zählt.
  const isSmartcarCharging = (car: ChargingCar) =>
    connectionsOf(smartcarStatus.value).some(c =>
      c.vehicleState === 'CHARGING' &&
      (c.carId === car.id || (c.carId === null && cars.value.length === 1)))

  const isWallboxCharging = () =>
    wallboxStore.isCharging && cars.value.length === 1

  const isVehicleCharging = (car: ChargingCar) =>
    isSmartcarCharging(car) || isWallboxCharging()

  return { isVehicleCharging, isSmartcarCharging, isWallboxCharging }
}
