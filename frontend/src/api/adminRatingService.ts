import api from './axios'
import type { RatingAnswers, ReviewStatus } from './ratingService'

export type TextType = 'STARS_ONLY' | 'SHORT' | 'LONG'
export type RejectionReason = 'PERSONAL_DATA' | 'INSULT' | 'ADVERTISING' | 'WRONG_MODEL' | 'NO_EXPERIENCE' | 'OTHER'

export interface AdminRatingItem {
    ratingId: string
    model: string
    rebuy: string
    overall: number
    kmDriven: number
    logCount: number
    updatedAt: string
    type: TextType
    reviewId: string | null
    reviewStatus: ReviewStatus | null
    preview: string | null
    reviewSubmittedAt: string | null
    userShort: string | null
}

export interface AdminRatingPage {
    items: AdminRatingItem[]
    page: number
    size: number
    total: number
    openTexts: number
}

export interface AdminReviewVersion {
    reviewId: string
    body: string
    pros: string[]
    cons: string[]
    status: ReviewStatus
    publishedBody: string | null
    rejectionReason: RejectionReason | null
    rejectionNote: string | null
    llmCheck: unknown
    deleteWithCar: boolean
    proofKmDriven: number | null
    proofLogCount: number | null
    proofConsumption: number | null
    reviewedAt: string | null
    createdAt: string
}

export interface AdminRatingDetail {
    ratingId: string
    model: string
    answers: RatingAnswers
    kmDriven: number
    logCount: number
    businessCar: boolean
    anonymized: boolean
    userShort: string | null
    createdAt: string
    updatedAt: string
    versions: AdminReviewVersion[]
}

export interface AdminRatingFilter {
    status?: ReviewStatus | ''
    type?: TextType | ''
    page?: number
}

/** Gleiche Regel wie der Server: nur nicht leere Stellen durch [entfernt] ersetzen. */
export const REDACTION = '[entfernt]'
export function isRedactionOf(body: string, published: string): boolean {
    const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const pattern = published.split(REDACTION).map(escape).join('(.+?)')
    return new RegExp(`^${pattern}$`, 's').test(body)
}

export const adminRatingService = {
    async list(filter: AdminRatingFilter): Promise<AdminRatingPage> {
        const params = Object.fromEntries(Object.entries(filter).filter(([, v]) => v !== '' && v !== undefined))
        return (await api.get<AdminRatingPage>('/admin/ratings', { params })).data
    },
    async openCount(): Promise<number> {
        return (await api.get<number>('/admin/ratings/open-count')).data
    },
    async detail(ratingId: string): Promise<AdminRatingDetail> {
        return (await api.get<AdminRatingDetail>(`/admin/ratings/${ratingId}`)).data
    },
    async approve(reviewId: string, publishedBody: string | null): Promise<AdminRatingDetail> {
        return (await api.post<AdminRatingDetail>(`/admin/ratings/reviews/${reviewId}/approve`, { publishedBody })).data
    },
    async reject(reviewId: string, reason: RejectionReason, note: string): Promise<AdminRatingDetail> {
        return (await api.post<AdminRatingDetail>(`/admin/ratings/reviews/${reviewId}/reject`, { reason, note: note || null })).data
    },
}
