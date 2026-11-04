import type { SeoCampaignRow, SeoPageType } from '../../../api/seoService'

/** Auswertungen, die mehrere Zeilen zusammenfassen: Keyword-Themen, Kanäle, Gewinner/Verlierer, URL-Varianten. */

/** Reihenfolge der Seitentyp-Filter; Typen ohne Zeile erscheinen nicht. */
export const PAGE_TYPE_LABELS: Record<SeoPageType, string> = {
    MODEL: 'Modellseiten',
    BRAND: 'Markenseiten',
    MODEL_LIST: 'Modellübersicht',
    CONTENT: 'Content',
    HOME: 'Startseite',
    CONVERSION: 'Conversion-Seiten',
    LEGAL: 'Rechtliches',
    OTHER: 'Sonstige',
}

export type KeywordTopic = 'BRAND' | 'COST' | 'CHARGING' | 'CONSUMPTION' | 'RANGE' | 'BATTERY' | 'REVIEW' | 'OTHER'

export const TOPIC_LABELS: Record<KeywordTopic, string> = {
    BRAND: 'Brand',
    COST: 'Kosten',
    CHARGING: 'Laden',
    CONSUMPTION: 'Verbrauch',
    RANGE: 'Reichweite',
    BATTERY: 'Akku',
    REVIEW: 'Test & Erfahrung',
    OTHER: 'Modell / Sonstige',
}

/** Erste passende Regel gewinnt; Verbrauch vor Reichweite, weil "verbrauch 100km" sonst bei Reichweite landet. */
const TOPIC_RULES: [Exclude<KeywordTopic, 'BRAND' | 'OTHER'>, RegExp][] = [
    ['COST', /kosten|kostet|preis|euro|€|günstig|tarif|cost|price/],
    ['CHARGING', /lade|laden|wallbox|säule|charging|charger|\bkw\b/],
    ['CONSUMPTION', /verbrauch|kwh|consumption|effizien|wltp|förbrukning|forbruk/],
    ['RANGE', /reichweite|range|räckvidd|rekkevidde/],
    ['BATTERY', /akku|batterie|battery|\bsoh\b|degradation|kapazität/],
    ['REVIEW', /test|erfahrung|bericht|review|probleme|vergleich/],
]

export function keywordTopic(query: string, brand: boolean): KeywordTopic {
    if (brand) return 'BRAND'
    const q = query.toLowerCase()
    return TOPIC_RULES.find(([, re]) => re.test(q))?.[0] ?? 'OTHER'
}

export interface TopicSummary { topic: KeywordTopic; label: string; impressions: number; clicks: number; count: number }

export function summarizeTopics(rows: { query: string; brand: boolean; impressions: number; clicks: number }[]): TopicSummary[] {
    const acc = new Map<KeywordTopic, TopicSummary>()
    for (const r of rows) {
        const topic = keywordTopic(r.query, r.brand)
        const s = acc.get(topic) ?? { topic, label: TOPIC_LABELS[topic], impressions: 0, clicks: 0, count: 0 }
        s.impressions += r.impressions
        s.clicks += r.clicks
        s.count++
        acc.set(topic, s)
    }
    return [...acc.values()].sort((a, b) => b.impressions - a.impressions)
}

export type Channel = 'ORGANIC_SEARCH' | 'PAID_SEARCH' | 'DIRECT' | 'EMAIL' | 'FORUM' | 'SOCIAL' | 'AI' | 'APP' | 'REFERRAL'

/** Angelehnt an die Default Channel Group von Google Analytics; Foren als eigener Referral-Kanal, weil sie die Kampagnen sind. */
export const CHANNEL_LABELS: Record<Channel, string> = {
    ORGANIC_SEARCH: 'Organic Search',
    PAID_SEARCH: 'Paid Search',
    DIRECT: 'Direct',
    EMAIL: 'E-Mail',
    FORUM: 'Referral: Foren',
    SOCIAL: 'Organic Social',
    AI: 'KI-Assistenten',
    APP: 'App (PWA)',
    REFERRAL: 'Referral: sonstige',
}

const SEARCH = new Set(['google', 'bing', 'duckduckgo', 'ecosia', 'yahoo!', 'yahoo', 'qwant', 'startpage', 'brave', 'yandex', 'baidu', 'ecosia.org'])
const SOCIAL = new Set(['facebook', 'instagram', 'linkedin', 'reddit', 'x', 'twitter', 'youtube', 'tiktok', 'mastodon', 'threads', 'pinterest'])
const AI = new Set(['chatgpt', 'perplexity', 'gemini', 'claude', 'copilot'])

export function channelOf(source: string, medium: string): Channel {
    const s = source.trim().toLowerCase()
    const m = medium.trim().toLowerCase()
    if (['cpc', 'ppc', 'paid', 'paid-search'].includes(m)) return 'PAID_SEARCH'
    if (s === 'direct / none') return 'DIRECT'
    if (m === 'email' || s === 'email' || s === 'newsletter') return 'EMAIL'
    if (m === 'forum' || s.includes('forum')) return 'FORUM'
    if (SEARCH.has(s) && (m === '' || m === 'organic')) return 'ORGANIC_SEARCH'
    if (AI.has(s)) return 'AI'
    if (SOCIAL.has(s) || m === 'social') return 'SOCIAL'
    if (s === 'pwa') return 'APP'
    return 'REFERRAL'
}

/** perThousand: Sign-ups je 1 000 Besucher, nur über Zeilen mit zuordenbaren Sign-ups (UTM oder Google); sonst null. */
export interface ChannelSummary { channel: Channel; label: string; visitors: number; registrations: number; perThousand: number | null }

export function summarizeChannels(rows: SeoCampaignRow[]): ChannelSummary[] {
    const acc = new Map<Channel, { visitors: number; registrations: number; attributedVisitors: number }>()
    for (const r of rows) {
        const ch = channelOf(r.source, r.utmMedium)
        const a = acc.get(ch) ?? { visitors: 0, registrations: 0, attributedVisitors: 0 }
        a.visitors += r.visitors
        if (r.attributed) {
            a.registrations += r.registrations
            a.attributedVisitors += r.visitors
        }
        acc.set(ch, a)
    }
    return [...acc.entries()]
        .map(([channel, a]) => ({
            channel, label: CHANNEL_LABELS[channel], visitors: a.visitors, registrations: a.registrations,
            perThousand: a.attributedVisitors ? (a.registrations / a.attributedVisitors) * 1000 : null,
        }))
        .sort((a, b) => b.visitors - a.visitors)
}

export interface Mover { key: string; clicks: number; clicksPrevious: number; delta: number }

/** Gewinner und Verlierer nach Klick-Veränderung zum Vorzeitraum; Zeilen ohne Vorzeitraum-Wert fehlen. */
export function movers(rows: { key: string; clicks: number; clicksPrevious?: number | null }[], n: number): { winners: Mover[]; losers: Mover[] } {
    const all = rows
        .filter(r => r.clicksPrevious != null)
        .map(r => ({ key: r.key, clicks: r.clicks, clicksPrevious: r.clicksPrevious as number, delta: r.clicks - (r.clicksPrevious as number) }))
    return {
        winners: all.filter(m => m.delta > 0).sort((a, b) => b.delta - a.delta).slice(0, n),
        losers: all.filter(m => m.delta < 0).sort((a, b) => a.delta - b.delta).slice(0, n),
    }
}

export interface VariantGroup { paths: { path: string; impressions: number }[]; impressions: number }

/** Pfade, die Google getrennt führt, obwohl sie sich nur in Groß-/Kleinschreibung unterscheiden (Duplicate URLs). */
export function caseVariants(rows: { path: string; impressions: number }[]): VariantGroup[] {
    const groups = new Map<string, { path: string; impressions: number }[]>()
    for (const r of rows) {
        if (!r.impressions) continue
        const key = r.path.toLowerCase()
        groups.set(key, [...(groups.get(key) ?? []), { path: r.path, impressions: r.impressions }])
    }
    return [...groups.values()]
        .filter(paths => paths.length > 1)
        .map(paths => ({
            paths: [...paths].sort((a, b) => b.impressions - a.impressions),
            impressions: paths.reduce((sum, p) => sum + p.impressions, 0),
        }))
        .sort((a, b) => b.impressions - a.impressions)
}
