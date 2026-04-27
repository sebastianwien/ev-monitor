import apiClient from './axios'

export interface WltpVariant {
    batteryCapacityKwh: number
    variantName: string | null
    displayLabel: string | null
    wltpRangeKm: number
    wltpRangeMinKm: number | null
    wltpConsumptionKwhPer100km: number
    wltpConsumptionMinKwhPer100km: number | null
    wltpConsumptionMaxKwhPer100km: number | null
    realConsumptionKwhPer100km: number | null
    realConsumptionMinKwhPer100km: number | null
    realConsumptionMaxKwhPer100km: number | null
    realConsumptionTripCount: number | null
    estimatedConsumptionCount: number | null
    realConsumptionRangeSource: 'PER_DRIVER' | 'PER_TRIP' | null
    seasonalDistribution: SeasonalDistribution | null
}

export interface EpaVariant {
    batteryCapacityKwh: number
    variantName: string | null
    displayLabel: string | null
    epaRangeKm: number
    epaConsumptionKwhPer100km: number
    epaConsumptionMinKwhPer100km: number | null
    epaConsumptionMaxKwhPer100km: number | null
    realConsumptionKwhPer100km: number | null
    realConsumptionMinKwhPer100km: number | null
    realConsumptionMaxKwhPer100km: number | null
    realConsumptionTripCount: number | null
    estimatedConsumptionCount: number | null
    realConsumptionRangeSource: 'PER_DRIVER' | 'PER_TRIP' | null
    seasonalDistribution: SeasonalDistribution | null
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

export interface YearEntry {
    year: number
    carCount: number
}

export interface RouteTypeEntry {
    routeType: string
    count: number
}

export interface PublicModelStats {
    brand: string
    model: string
    brandDisplayName: string
    modelDisplayName: string
    logCount: number
    uniqueContributors: number
    uniqueCars: number
    avgCostPerKwh: number | null
    acAvgCostPerKwh: number | null
    dcAvgCostPerKwh: number | null
    avgKwhPerSession: number | null
    avgConsumptionKwhPer100km: number | null
    estimatedConsumptionCount: number
    avgChargingPowerKw: number | null
    wltpVariants: WltpVariant[]
    epaVariants: EpaVariant[] | null
    seasonalDistribution: SeasonalDistribution | null
    yearDistribution: YearEntry[]
    routeTypeDistribution: RouteTypeEntry[]
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

/** Fetches community stats by Java enum name (e.g. "MODEL_3", "IONIQ_5").
 *  Used by the Dashboard empty state to avoid display-name conversion on the frontend. */
export async function getModelStatsByEnum(modelEnum: string): Promise<PublicModelStats | null> {
    try {
        const response = await apiClient.get<PublicModelStats>(
            `/public/models/by-enum/${modelEnum}`
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
    category: string
    categoryDisplayName: string
}

export interface VehicleCategoryItem {
    key: string
    displayName: string
}

export async function getCategories(): Promise<VehicleCategoryItem[]> {
    const response = await apiClient.get<VehicleCategoryItem[]>('/public/rankings/categories')
    return response.data
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
    batteryCapacityKwh: number           // representative net capacity for range calculation
    variantName: string | null
    displayLabel: string | null
    realConsumptionKwhPer100km: number | null
    realConsumptionMinKwhPer100km: number | null
    realConsumptionMaxKwhPer100km: number | null
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

export async function getMostEfficientModels(limit: number = 5): Promise<TopModelPreview[]> {
    const response = await apiClient.get<TopModelPreview[]>(
        `/public/rankings/efficiency?limit=${limit}`
    )
    return response.data
}

export async function getPlatformStats(): Promise<PlatformStats> {
    const response = await apiClient.get<PlatformStats>('/public/stats')
    return response.data
}
