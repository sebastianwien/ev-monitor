import apiClient from './axios'

export type SeoPeriod = 7 | 28 | 90
export type SeoPageFilter = 'ALL' | 'UNDERRATED' | 'DEAD' | 'LEAK' | 'MONEY'
export type SeoBrandFilter = 'ALL' | 'BRAND' | 'NON_BRAND'
export type SeoQueryFilter = 'ALL' | 'STRIKING_DISTANCE' | 'CANNIBALIZED' | 'ADWORDS'
export type SeoModelLevel = 'MODEL' | 'BRAND'
export type SeoModelFilter = 'ALL' | 'CONTENT_GAP' | 'INVISIBLE'

export interface SeoTotals {
    impressions: number
    clicks: number
    ctr: number
    avgPosition: number
    visitors: number
    googleEntries: number
    registrations: number
    organicRegistrations: number
}

export interface SeoDayPoint {
    day: string
    impressions: number
    clicks: number
    visitors: number
    googleEntries: number
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
    entries: number
    bounceRate: number
    ctaRate: number
    registrations: number
    finding: 'UNDERRATED' | 'DEAD' | 'LEAK' | 'MONEY' | null
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
    adwordsCandidate: boolean
    finding: 'STRIKING_DISTANCE' | 'CANNIBALIZED' | null
}

/** Quelle x Medium x Kampagne aus Plausible; registrations/trials nur bei attributed (UTM oder Google organisch). */
export interface SeoCampaignRow {
    source: string
    utmMedium: string
    utmCampaign: string
    visitors: number
    visits: number
    ctaVisitors: number
    ctaRate: number
    registrations: number
    trials: number
    attributed: boolean
}

/** Modellseite je Modell (level MODEL) oder Marke samt Markenseite (level BRAND, model null); cars/logs aus der DB, nicht zeitraumbezogen. */
export interface SeoModelRow {
    brand: string
    model: string | null
    path: string
    impressions: number
    clicks: number
    ctr: number
    avgPosition: number
    registrations: number
    cars: number
    logs: number
    finding: 'CONTENT_GAP' | 'INVISIBLE' | null
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
    plausibleEnabled: boolean
    plausibleDataUntil: string | null
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

export async function getSeoCampaigns(days: SeoPeriod): Promise<SeoCampaignRow[]> {
    const { data } = await apiClient.get<SeoCampaignRow[]>('/admin/seo/campaigns', { params: { days } })
    return data
}

export async function getSeoModels(days: SeoPeriod, level: SeoModelLevel, filter: SeoModelFilter): Promise<SeoModelRow[]> {
    const { data } = await apiClient.get<SeoModelRow[]>('/admin/seo/models', { params: { days, level, filter } })
    return data
}

/** true, wenn ein Sync gestartet wurde; false bei fehlendem Zugang oder laufendem Sync (HTTP 409). */
export async function triggerSeoSync(days: number): Promise<boolean> {
    const res = await apiClient.post('/admin/seo/sync', null, {
        params: { days },
        validateStatus: s => s === 202 || s === 409,
    })
    return res.status === 202
}

export type SeoAuditCheck = 'STATUS' | 'NOINDEX' | 'CANONICAL' | 'TITLE' | 'DESCRIPTION' | 'H1' | 'JSONLD' | 'PRERENDER'
    | 'BROKEN_LINK' | 'ORPHAN' | 'CWV'
export type SeoAuditSeverity = 'ERROR' | 'WARNING'

/** message enthält Seiteninhalte (Title, Linkziel): nur als Text rendern. */
export interface SeoAuditFinding {
    check: SeoAuditCheck
    severity: SeoAuditSeverity
    message: string
}

/** source FIELD = echte Chrome-Nutzer, LAB = Lighthouse-Messung ohne INP. */
export interface SeoVitals {
    lcpMs: number | null
    cls: number | null
    inpMs: number | null
    source: 'FIELD' | 'LAB'
}

export interface SeoAuditRow {
    path: string
    httpStatus: number
    score: number
    errors: number
    warnings: number
    findings: SeoAuditFinding[]
    vitals: SeoVitals | null
}

export interface SeoAuditView {
    enabled: boolean
    day: string | null
    lastRunAt: string | null
    vitalsWeek: string | null
    summary: { checked: number; withErrors: number; prerenderFailed: number; brokenLinkPages: number; orphans: number }
    rows: SeoAuditRow[]
}

export async function getSeoAudit(): Promise<SeoAuditView> {
    const { data } = await apiClient.get<SeoAuditView>('/admin/seo/audit')
    return data
}

/** true, wenn ein Crawl gestartet wurde; false ohne Basis-URL oder bei laufendem Crawl (HTTP 409). */
export async function triggerSeoAudit(): Promise<boolean> {
    const res = await apiClient.post('/admin/seo/audit/run', null, { validateStatus: s => s === 202 || s === 409 })
    return res.status === 202
}
