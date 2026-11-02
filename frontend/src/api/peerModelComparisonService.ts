import api from './axios'

export interface PeerModelComparisonItem {
  vehicleSpecificationId: string
  displayName: string
  isUserVehicleSpec: boolean
  netBatteryCapacityKwh: number | null
  userMetrics: UserMetrics | null
  peerMetrics: PeerMetrics
  /** Reichweite 100 → 10 % aus dem Backend (RangeMath), null ohne Daten */
  userRangeKm?: number | null
  peerRangeKm?: number | null
}

export interface UserMetrics {
  consumptionKwhPer100km: number | null
  costPerKwh: number | null
}

export interface PeerMetrics {
  avgConsumptionKwhPer100km: number | null
  avgCostPerKwh: number | null
  uniqueCars: number
}

export interface PeerModelComparisonResponse {
  modelComparisons: PeerModelComparisonItem[]
  /** Alle Autos aller Varianten des Modells, ohne die eigenen; null ohne Spec. */
  allVariants: PeerMetrics | null
  /** Winterverbrauch derselben Autos, November bis Februar; null ohne genug Winterdaten anderer Fahrer. */
  allVariantsWinterConsumptionKwhPer100km: number | null
  /** Fahrverbrauch im Winter derselben Fahrer aus ihren Fahrten; null unter 5 Nutzern. Fehlt bei älterem Backend. */
  allVariantsDriveWinterConsumptionKwhPer100km?: number | null
}

export const peerModelComparisonService = {
  async getPeerModelComparison(carId: string): Promise<PeerModelComparisonResponse> {
    const response = await api.get<PeerModelComparisonResponse>(
      `/logs/${carId}/peer-model-comparison`
    )
    return response.data
  }
}
