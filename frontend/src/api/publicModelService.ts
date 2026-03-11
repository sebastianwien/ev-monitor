import apiClient from './axios'

export interface WltpVariant {
    batteryCapacityKwh: number
    wltpRangeKm: number
    wltpConsumptionKwhPer100km: number
    realConsumptionKwhPer100km: number | null
    realConsumptionTripCount: number | null
}

export interface SeasonalDistribution {
    summerPercentage: number
    winterPercentage: number
    summerConsumptionKwhPer100km: number | null
    winterConsumptionKwhPer100km: number | null
    totalConsumptionKwhPer100km: number | null
    summerLogCount: number
    winterLogCount: number
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
    seasonalDistribution: SeasonalDistribution | null
}

export async function getModelStats(brand: string, model: string): Promise<PublicModelStats | null> {
    try {
        const response = await apiClient.get<PublicModelStats>(
            `/public/models/${brand}/${model}`
        )
        return response.data
    } catch (err: any) {
        if (err.response?.status === 404) return null
        throw err
    }
}

export async function getAllModelsWithWltpData(): Promise<string[]> {
    const response = await apiClient.get<string[]>('/public/models')
    return response.data
}

export interface PlatformStats {
    modelCount: number
    userCount: number
    validTripCount: number
}

export async function getPlatformStats(): Promise<PlatformStats> {
    const response = await apiClient.get<PlatformStats>('/public/stats')
    return response.data
}
