import axios from 'axios'

export interface WltpVariant {
    batteryCapacityKwh: number
    wltpRangeKm: number
    wltpConsumptionKwhPer100km: number
}

export interface PublicModelStats {
    brand: string
    model: string
    modelDisplayName: string
    logCount: number
    uniqueContributors: number
    avgCostPerKwh: number | null
    avgKwhPerSession: number | null
    avgConsumptionKwhPer100km: number | null
    wltpVariants: WltpVariant[]
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

export async function getModelStats(brand: string, model: string): Promise<PublicModelStats | null> {
    try {
        const response = await axios.get<PublicModelStats>(
            `${API_BASE}/public/models/${brand}/${model}`
        )
        return response.data
    } catch (err: any) {
        if (err.response?.status === 404) return null
        throw err
    }
}
