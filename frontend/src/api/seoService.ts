import apiClient from './axios'

export type SeoPeriod = 7 | 28 | 90
export type SeoPageFilter = 'ALL' | 'UNDERRATED' | 'DEAD'
export type SeoBrandFilter = 'ALL' | 'BRAND' | 'NON_BRAND'
export type SeoQueryFilter = 'ALL' | 'STRIKING_DISTANCE' | 'CANNIBALIZED'

export interface SeoTotals {
    impressions: number
    clicks: number
    ctr: number
    avgPosition: number
}

export interface SeoDayPoint {
    day: string
    impressions: number
    clicks: number
}

export interface SeoOverview {
    periodDays: number
    from: string
    to: string
    current: SeoTotals
    previous: SeoTotals
    days: SeoDayPoint[]
}

export interface SeoPageRow {
    path: string
    impressions: number
    clicks: number
    ctr: number
    avgPosition: number
    finding: 'UNDERRATED' | 'DEAD' | null
}

export interface SeoQueryRow {
    query: string
    brand: boolean
    impressions: number
    clicks: number
    ctr: number
    avgPosition: number
    topPath: string | null
    pageCount: number
    finding: 'STRIKING_DISTANCE' | 'CANNIBALIZED' | null
}

export interface SeoSyncRun {
    id: string
    source: string
    startedAt: string
    finishedAt: string | null
    status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'
    rowCount: number
    fromDay: string
    toDay: string
    error: string | null
}

export interface SeoStatus {
    enabled: boolean
    dataUntil: string
    runs: SeoSyncRun[]
}

export async function getSeoStatus(): Promise<SeoStatus> {
    const res = await apiClient.get('/admin/seo/status')
    return res.data
}

export async function getSeoOverview(days: SeoPeriod): Promise<SeoOverview> {
    const res = await apiClient.get('/admin/seo/overview', { params: { days } })
    return res.data
}

export async function getSeoPages(days: SeoPeriod, filter: SeoPageFilter): Promise<SeoPageRow[]> {
    const res = await apiClient.get('/admin/seo/pages', { params: { days, filter } })
    return res.data
}

export async function getSeoQueries(days: SeoPeriod, brand: SeoBrandFilter, filter: SeoQueryFilter): Promise<SeoQueryRow[]> {
    const res = await apiClient.get('/admin/seo/queries', { params: { days, brand, filter } })
    return res.data
}

/** true, wenn ein Sync gestartet wurde; false bei fehlendem Zugang oder laufendem Sync (HTTP 409). */
export async function triggerSeoSync(days: number): Promise<boolean> {
    const res = await apiClient.post('/admin/seo/sync', null, {
        params: { days },
        validateStatus: s => s === 202 || s === 409,
    })
    return res.status === 202
}
