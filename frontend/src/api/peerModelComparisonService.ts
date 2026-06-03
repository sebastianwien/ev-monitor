import api from './axios'

export interface PeerModelComparisonItem {
  vehicleSpecificationId: string
  displayName: string
  isUserVehicleSpec: boolean
  netBatteryCapacityKwh: number | null
  userMetrics: UserMetrics | null
  peerMetrics: PeerMetrics
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
}

export const peerModelComparisonService = {
  async getPeerModelComparison(carId: string): Promise<PeerModelComparisonResponse> {
    const response = await api.get<PeerModelComparisonResponse>(
      `/logs/${carId}/peer-model-comparison`
    )
    return response.data
  }
}
