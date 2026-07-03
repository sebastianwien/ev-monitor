import type { Ref } from 'vue'
import { useWallboxStore } from '../stores/wallbox'
import { isVwGroupBrand } from '../api/vwGroupService'
import type { SmartcarConnectionStatus } from '../api/smartcarService'
import type { VwGroupConnectionStatus } from '../api/vwGroupService'

/** Minimal-Shape, das die Charging-Predicates brauchen. */
export interface ChargingCar {
  id: string
  brand?: string | null
}

/**
 * Kapselt die Frage "laedt dieses Fahrzeug gerade?" fuer die verschiedenen
 * Provider (Smartcar, VW Group, Wallbox). Einzige Quelle fuer Dashboard-
 * und Log-Feed-Auto-Cards, damit die Glow-Logik nicht dupliziert wird.
 *
 * Wallbox kennt keine carId -> eine laufende Wallbox-Ladung ist nur bei
 * Single-Car-Accounts sicher einem Fahrzeug zuordenbar.
 */
export function useVehicleCharging(
  cars: Ref<ChargingCar[]>,
  smartcarStatus: Ref<SmartcarConnectionStatus | null>,
  vwGroupStatus: Ref<VwGroupConnectionStatus | null>,
) {
  const wallboxStore = useWallboxStore()

  const isSmartcarCharging = (car: ChargingCar) =>
    smartcarStatus.value?.connected === true &&
    smartcarStatus.value?.vehicleState === 'CHARGING' &&
    (smartcarStatus.value?.carId === car.id ||
      (smartcarStatus.value?.carId === null && cars.value.length === 1))

  const isWallboxCharging = () =>
    wallboxStore.isCharging && cars.value.length === 1

  const isVwGroupCharging = (car: ChargingCar) =>
    isVwGroupBrand(car.brand) &&
    vwGroupStatus.value?.connected === true &&
    vwGroupStatus.value?.vehicleState === 'charging'

  const isVehicleCharging = (car: ChargingCar) =>
    isSmartcarCharging(car) || isVwGroupCharging(car) || isWallboxCharging()

  return { isVehicleCharging, isSmartcarCharging, isVwGroupCharging, isWallboxCharging }
}
