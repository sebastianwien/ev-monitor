import api from './axios'

export type RebuyAnswer = 'YES' | 'BRAND_OTHER_MODEL' | 'NO'
export type RebuyReason = 'SIZE' | 'RANGE' | 'PRICE' | 'EQUIPMENT' | 'SOFTWARE'
export type TotalCostVerdict = 'CHEAPER' | 'EXPECTED' | 'PRICIER'
export type RatingChip =
    | 'EFFICIENCY' | 'CHARGING_SPEED' | 'SPACE' | 'DRIVING_FUN' | 'PRICE'
    | 'SOFTWARE' | 'BUILD_QUALITY' | 'DESIGN' | 'ASSISTANCE' | 'SERVICE'
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED'

export interface CarEligibility {
    carId: string
    brandName: string
    modelName: string
    eligible: boolean
    rated: boolean
    kmDriven: number
    logCount: number
    firstLogDate: string | null
}

export interface RatingEligibilityResponse {
    minLogs: number
    minKm: number
    cars: CarEligibility[]
}

/** Antworten wie sie das Backend erwartet. longDistance/service null = "selten" bzw. "noch nicht gebraucht". */
export interface RatingAnswers {
    rebuy: RebuyAnswer
    rebuyReasons: RebuyReason[]
    dailyFit: number
    longDistance: number | null
    software: number
    buildQuality: number
    service: number | null
    totalCost: TotalCostVerdict
    strengths: RatingChip[]
    weaknesses: RatingChip[]
}

export interface ReviewState {
    body: string
    pros: string[]
    cons: string[]
    status: ReviewStatus
    rejectionReason: string | null
    rejectionNote: string | null
    published: boolean
    deleteWithCar: boolean
    submittedAt: string
}

export interface MyRating {
    answers: RatingAnswers
    kmDriven: number
    logCount: number
    createdAt: string
    updatedAt: string
    review: ReviewState | null
}

export interface CategoryAverage {
    average: number | null
    count: number
}

export interface RatingResult {
    count: number
    rebuy: { yes: number; brandOtherModel: number; no: number }
    categories: {
        dailyFit: CategoryAverage
        longDistance: CategoryAverage
        software: CategoryAverage
        buildQuality: CategoryAverage
        service: CategoryAverage
    } | null
    mine: RatingAnswers
    standing: { moreCriticalThanPercent: number; moreSatisfiedThanPercent: number } | null
    coinsAwarded: number
}

export interface ReviewInput {
    body: string
    pros: string[]
    cons: string[]
    deleteWithCar: boolean | null
}

export const ratingService = {
    async getEligibility(): Promise<RatingEligibilityResponse> {
        return (await api.get<RatingEligibilityResponse>('/ratings/eligibility')).data
    },

    /** null, wenn das Auto noch nicht bewertet ist (204). */
    async getMyRating(carId: string): Promise<MyRating | null> {
        const res = await api.get<MyRating>(`/cars/${carId}/rating`)
        return res.status === 204 ? null : res.data
    },

    async submitRating(carId: string, answers: RatingAnswers): Promise<RatingResult> {
        return (await api.put<RatingResult>(`/cars/${carId}/rating`, answers)).data
    },

    async submitReview(carId: string, review: ReviewInput): Promise<ReviewState> {
        return (await api.put<ReviewState>(`/cars/${carId}/rating/review`, review)).data
    },

    async updateReviewSettings(carId: string, deleteWithCar: boolean): Promise<ReviewState> {
        return (await api.patch<ReviewState>(`/cars/${carId}/rating/review/settings`, { deleteWithCar })).data
    },
}
