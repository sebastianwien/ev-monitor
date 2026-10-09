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
    llmCheck: LlmCheck | null
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

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** Ergebnis der Mistral-Prüfung, wie in car_review.llm_check gespeichert. */
export interface LlmCheck {
    recommendation: 'APPROVE' | 'APPROVE_WITH_REDACTION' | 'REJECT' | 'NEEDS_HUMAN'
    confidence: 'LOW' | 'MEDIUM' | 'HIGH'
    summary: string
    findings: { category: string; excerpt: string; explanation: string; severity: 'INFO' | 'WARN' | 'BLOCK' }[]
    redacted_text: string | null
    topics: string[]
    meta?: { model: string; checkedAt: string; costUsd: number }
}

/** Text in Abschnitte zerlegen, damit Fundstellen ohne v-html markiert werden können. */
export function highlightSegments(text: string, excerpts: string[]): { text: string; hit: boolean }[] {
    const marks = excerpts.filter(e => e && text.includes(e))
    if (marks.length === 0) return [{ text, hit: false }]
    const parts = text.split(new RegExp(`(${marks.map(escapeRegExp).join('|')})`, 'g'))
    return parts.filter(p => p !== '').map(p => ({ text: p, hit: marks.includes(p) }))
}

export interface AdminRatingFilter {
    status?: ReviewStatus | ''
    type?: TextType | ''
    page?: number
}

/** Gleiche Regel wie der Server: nur nicht leere Stellen durch [entfernt] ersetzen. */
export const REDACTION = '[entfernt]'
export function isRedactionOf(body: string, published: string): boolean {
    const pattern = published.split(REDACTION).map(escapeRegExp).join('(.+?)')
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
    async llmCheck(reviewId: string): Promise<AdminRatingDetail> {
        return (await api.post<AdminRatingDetail>(`/admin/ratings/reviews/${reviewId}/llm-check`)).data
    },
    async reject(reviewId: string, reason: RejectionReason, note: string): Promise<AdminRatingDetail> {
        return (await api.post<AdminRatingDetail>(`/admin/ratings/reviews/${reviewId}/reject`, { reason, note: note || null })).data
    },
}
