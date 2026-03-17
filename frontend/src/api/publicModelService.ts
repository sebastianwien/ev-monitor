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
    brandDisplayName: string
    modelDisplayName: string
    logCount: number
    uniqueContributors: number
    avgCostPerKwh: number | null
    avgKwhPerSession: number | null
    avgConsumptionKwhPer100km: number | null
    estimatedConsumptionCount: number
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

export interface TopModelPreview {
    brand: string
    model: string
    brandDisplayName: string
    modelDisplayName: string
    modelUrlSlug: string
    logCount: number
    avgConsumptionKwhPer100km: number | null
    minRealConsumptionKwhPer100km: number | null
    maxRealConsumptionKwhPer100km: number | null
    minWltpConsumptionKwhPer100km: number | null
    maxWltpConsumptionKwhPer100km: number | null
    avgCostPerKwh: number | null
}

export async function getTopModels(limit: number = 12): Promise<TopModelPreview[]> {
    const response = await apiClient.get<TopModelPreview[]>(`/public/models/top?limit=${limit}`)
    return response.data
}

export interface PlatformStats {
    modelCount: number
    userCount: number
    validTripCount: number
}

export interface BrandWltpVariant {
    batteryCapacityKwh: number
    wltpRangeKm: number | null
    wltpConsumptionKwhPer100km: number | null
    realConsumptionKwhPer100km: number | null
}

export interface BrandModelSummary {
    modelEnum: string
    modelDisplayName: string
    modelUrlSlug: string
    logCount: number
    avgConsumptionKwhPer100km: number | null
    wltpVariants: BrandWltpVariant[]
}

export interface PublicBrandResponse {
    brandEnum: string
    brandDisplayName: string
    models: BrandModelSummary[]
}

export async function getBrandModels(brand: string): Promise<PublicBrandResponse | null> {
    try {
        const response = await apiClient.get<PublicBrandResponse>(`/public/brands/${brand}`)
        return response.data
    } catch (err: any) {
        if (err.response?.status === 404) return null
        throw err
    }
}

export async function getPlatformStats(): Promise<PlatformStats> {
    const response = await apiClient.get<PlatformStats>('/public/stats')
    return response.data
}
